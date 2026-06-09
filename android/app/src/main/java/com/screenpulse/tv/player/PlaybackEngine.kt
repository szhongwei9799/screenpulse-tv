package com.screenpulse.tv.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.screenpulse.tv.ScreenPulseApp
import com.screenpulse.tv.R
import com.screenpulse.tv.db.entities.PlaylistEntity
import com.screenpulse.tv.player.PlaylistManager.PlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放引擎 - 核心播放控制器
 *
 * 负责管理所有类型媒体的播放：
 * - 视频/IPTV/流媒体：使用 ExoPlayer
 * - 图片：使用 Glide 加载到 ImageView
 * - 网页：使用 WebView
 *
 * 支持循环、顺序、随机播放模式，定时切换，转场动画
 */
class PlaybackEngine(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackEngine"

        /** 默认图片显示时长（秒） */
        private const val DEFAULT_IMAGE_DURATION = 10L

        /** 默认网页显示时长（秒） */
        private const val DEFAULT_WEBPAGE_DURATION = 30L

        /** 最小音量 */
        private const val MIN_VOLUME = 0

        /** 最大音量 */
        private const val MAX_VOLUME = 100
    }

    /** ExoPlayer 实例 - 用于视频/IPTV/流媒体播放 */
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            // 设置播放器监听
            addListener(playerListener)
            // 视频播放完毕时自动停止（由引擎控制下一步）
            repeatMode = Player.REPEAT_MODE_OFF
            // 硬件加速
            setVideoScalingMode(C.SCALING_MODE_AUTOMATIC)
        }
    }

    /** 主线程 Handler - 用于定时任务 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 协程作用域 - 用于异步操作 */
    private val engineScope = CoroutineScope(Dispatchers.Main + Job())

    /** 当前播放状态 */
    @Volatile
    private var currentState: PlaybackState = PlaybackState.IDLE

    /** 当前播放的媒体项 */
    @Volatile
    private var currentMediaItem: PlaylistEntity? = null

    /** 播放列表管理器 */
    private val playlistManager: PlaylistManager by lazy {
        PlaylistManager(context)
    }

    /** 是否正在播放 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 当前播放索引 */
    @Volatile
    var currentIndex: Int = 0
        private set

    /** 获取当前播放项（供 ViewModel 访问） */
    fun getCurrentItem(): PlaylistEntity? = currentMediaItem

    /** 是否被定时任务中断 */
    @Volatile
    private var isInterruptedBySchedule: Boolean = false

    /** 定时任务中断前的播放列表 */
    private var preInterruptPlaylist: List<PlaylistEntity>? = null
    private var preInterruptIndex: Int = 0

    /** 定时切换 Runnable */
    private var durationRunnable: Runnable? = null

    /** 视频视图引用 */
    private var playerView: PlayerView? = null

    /** 图片视图引用 */
    private var imageView: ImageView? = null

    /** WebView 引用 */
    private var webView: WebView? = null

    /** 容器视图引用 */
    private var containerView: ViewGroup? = null

    // ======================== Transition Settings ========================

    /** Current transition effect */
    var transitionEffect: TransitionEffect = TransitionEffect.RANDOM
        private set

    /** Transition duration in milliseconds */
    var transitionDuration: Long = 600L

    /** Set transition effect */
    fun setTransitionEffect(effect: TransitionEffect) {
        transitionEffect = effect
    }

    // ======================== Background Music / TTS ========================

    /** Background music MediaPlayer */
    private var bgMusicPlayer: android.media.MediaPlayer? = null

    /** Current background music path */
    private var currentBgMusicPath: String? = null

    /** Background music volume (0-1) */
    var bgMusicVolume: Float = 0.8f

    /** Whether video audio is muted (replaced by background music) */
    var isVideoMuted: Boolean = false
        private set

    /** Current TTS audio queue */
    private var ttsAudioQueue: List<String> = emptyList()
    private var ttsAudioIndex: Int = 0

    /** 状态变化监听器 */
    var onStateChanged: ((PlaybackState, PlaylistEntity?) -> Unit)? = null

    /** 播放错误监听器 */
    var onError: ((Throwable) -> Unit)? = null

    /**
     * 播放状态枚举
     */
    enum class PlaybackState {
        IDLE,       // 空闲
        PREPARING,  // 准备中
        PLAYING,    // 播放中
        PAUSED,     // 暂停
        TRANSITION, // 切换转场中
        ERROR       // 错误
    }

    /**
     * 转场动画效果枚举
     */
    enum class TransitionEffect {
        NONE,       // No animation
        FADE,       // Fade in/out
        ZOOM,       // Zoom in/out
        SLIDE_LEFT, // Slide from right
        SLIDE_RIGHT,// Slide from left
        DISSOLVE,   // Dissolve effect
        RANDOM      // Random selection
    }

    // ExoPlayer 播放状态监听
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> {
                    updateState(PlaybackState.IDLE)
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "缓冲中: ${currentMediaItem?.title}")
                    updateState(PlaybackState.PREPARING)
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "准备就绪，开始播放: ${currentMediaItem?.title}")
                    exoPlayer.playWhenReady = true
                    updateState(PlaybackState.PLAYING)
                    isPlaying = true
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "播放结束: ${currentMediaItem?.title}")
                    // 视频自动播放完毕，播放下一项
                    onItemComplete()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "播放错误: ${error.message}", error)
            onError?.invoke(error)
            updateState(PlaybackState.ERROR)
            // 出错后跳到下一项
            engineScope.launch {
                delay(2000) // 等待 2 秒后跳到下一项
                playNext()
            }
        }
    }

    /**
     * 绑定视图容器
     * 将 PlayerView、ImageView、WebView 添加到容器中
     */
    fun attachViews(container: ViewGroup) {
        containerView = container

        // 创建并添加 PlayerView
        playerView = PlayerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false // TV 模式不需要播放控制器
            player = exoPlayer
            visibility = View.GONE
        }
        container.addView(playerView)

        // 创建并添加 ImageView（用于图片显示）
        imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        container.addView(imageView)

        // 创建并添加 WebView（用于网页显示）
        webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                // 允许混合内容
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            visibility = View.GONE
            // 网页加载完成回调
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (currentMediaItem?.type == MediaType.WEBPAGE.value) {
                        updateState(PlaybackState.PLAYING)
                    }
                }
            }
        }
        container.addView(webView)
    }

    /**
     * 开始播放
     * 加载播放列表并开始播放第一项
     */
    fun start() {
        engineScope.launch {
            val playlist = playlistManager.getActivePlaylist()
            if (playlist.isEmpty()) {
                Log.w(TAG, "播放列表为空，无法开始播放")
                return@launch
            }
            currentIndex = 0
            playItem(playlist[currentIndex])
        }
    }

    /**
     * 播放指定项
     */
    private fun playItem(item: PlaylistEntity) {
        if (!item.enabled) {
            Log.d(TAG, "媒体项已禁用，跳过: ${item.title}")
            playNext()
            return
        }

        currentMediaItem = item
        isInterruptedBySchedule = false
        updateState(PlaybackState.PREPARING)
        cancelDurationTimer()

        Log.d(TAG, "开始播放 [${item.type}]: ${item.title}")

        // 设置音量（考虑静音状态）
        val volume = (item.volume.toFloat() / MAX_VOLUME).coerceIn(0f, 1f)
        exoPlayer.volume = if (isVideoMuted) 0f else volume

        // 隐藏所有视图
        hideAllViews()

        when (MediaType.fromValue(item.type)) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                playVideo(item)
            }
            MediaType.IMAGE -> {
                playImage(item)
            }
            MediaType.WEBPAGE -> {
                playWebpage(item)
            }
        }

        onStateChanged?.invoke(PlaybackState.PREPARING, item)
    }

    /**
     * 播放视频/IPTV/流媒体
     */
    private fun playVideo(item: PlaylistEntity) {
        playerView?.visibility = View.VISIBLE

        val mediaItem = MediaItem.fromUri(item.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // 如果设置了时长限制，启动定时器
        val durationMs = item.duration?.times(1000)
        if (durationMs != null && durationMs > 0) {
            startDurationTimer(durationMs)
        }
        // 否则等待视频自动播放完毕
    }

    /**
     * 播放图片
     * 使用 Glide 加载图片，设置定时器自动切换
     */
    private fun playImage(item: PlaylistEntity) {
        imageView?.visibility = View.VISIBLE

        Glide.with(context)
            .asBitmap()
            .load(item.url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_gradient)
            .error(R.drawable.bg_gradient)
            .into(object : BitmapImageViewTarget(imageView!!) {
                override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {
                    super.onResourceReady(resource, transition)
                    updateState(PlaybackState.PLAYING)
                    isPlaying = true
                }
            })

        // 图片有固定显示时长
        val durationMs = (item.duration ?: DEFAULT_IMAGE_DURATION) * 1000
        startDurationTimer(durationMs)
    }

    /**
     * 播放网页
     * 使用 WebView 加载网页内容
     */
    private fun playWebpage(item: PlaylistEntity) {
        webView?.visibility = View.VISIBLE
        webView?.loadUrl(item.url)
        isPlaying = true

        // 网页有固定显示时长
        val durationMs = (item.duration ?: DEFAULT_WEBPAGE_DURATION) * 1000
        startDurationTimer(durationMs)
    }

    /**
     * 播放下一项
     */
    fun playNext() {
        engineScope.launch {
            val playlist = playlistManager.getActivePlaylist()
            if (playlist.isEmpty()) {
                stop()
                return@launch
            }

            val playMode = playlistManager.playMode
            when (playMode) {
                PlayMode.LOOP -> {
                    // 循环模式：播完后从头开始
                    currentIndex = (currentIndex + 1) % playlist.size
                }
                PlayMode.SEQUENTIAL -> {
                    // 顺序模式：播完后停止
                    currentIndex++
                    if (currentIndex >= playlist.size) {
                        currentIndex = 0 // 重新开始
                    }
                }
                PlayMode.RANDOM -> {
                    // 随机模式：随机选择下一项
                    currentIndex = (0 until playlist.size).random()
                }
            }

            if (currentIndex < playlist.size) {
                playItemWithTransition(playlist[currentIndex])
            }
        }
    }

    /**
     * 播放上一项
     */
    fun playPrevious() {
        engineScope.launch {
            val playlist = playlistManager.getActivePlaylist()
            if (playlist.isEmpty()) return@launch

            currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
            playItem(playlist[currentIndex])
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        when (MediaType.fromValue(currentMediaItem?.type ?: "video")) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                exoPlayer.playWhenReady = false
            }
            else -> {
                // 图片和网页暂停 = 停止定时器
                cancelDurationTimer()
            }
        }
        isPlaying = false
        updateState(PlaybackState.PAUSED)
    }

    /**
     * 恢复播放
     */
    fun resume() {
        when (MediaType.fromValue(currentMediaItem?.type ?: "video")) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                exoPlayer.playWhenReady = true
            }
            else -> {
                // 恢复定时器
                val remaining = currentMediaItem?.duration?.times(1000)
                    ?: DEFAULT_IMAGE_DURATION * 1000
                startDurationTimer(remaining)
            }
        }
        isPlaying = true
        updateState(PlaybackState.PLAYING)
    }

    /**
     * 停止播放
     */
    fun stop() {
        exoPlayer.stop()
        cancelDurationTimer()
        hideAllViews()
        isPlaying = false
        currentMediaItem = null
        updateState(PlaybackState.IDLE)
    }

    /**
     * 释放所有资源
     */
    fun release() {
        stop()
        stopBackgroundMusic()
        exoPlayer.release()
        engineScope.cancel()
        playerView = null
        imageView = null
        webView = null
        containerView = null
    }

    /**
     * 跳转到指定索引
     */
    fun jumpTo(index: Int) {
        engineScope.launch {
            val playlist = playlistManager.getActivePlaylist()
            if (index in playlist.indices) {
                currentIndex = index
                playItem(playlist[index])
            }
        }
    }

    /**
     * 获取播放进度信息
     */
    fun getProgress(): PlaybackProgress {
        val mediaItem = currentMediaItem ?: return PlaybackProgress(0, 0, 0)
        val position = when (MediaType.fromValue(mediaItem.type)) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                exoPlayer.currentPosition.coerceAtLeast(0)
            }
            else -> {
                // 非视频类型无法精确追踪进度
                0
            }
        }
        val duration = when (MediaType.fromValue(mediaItem.type)) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                exoPlayer.duration.coerceAtLeast(0)
            }
            else -> {
                (mediaItem.duration ?: DEFAULT_IMAGE_DURATION) * 1000
            }
        }

        return PlaybackProgress(
            currentIndex = currentIndex,
            position = position,
            duration = duration
        )
    }

    /**
     * 插入定时内容
     * 暂停当前播放，插入定时内容，完成后恢复
     */
    fun insertScheduledContent(items: List<PlaylistEntity>, onComplete: () -> Unit) {
        if (items.isEmpty()) {
            onComplete()
            return
        }

        engineScope.launch {
            // 保存当前播放状态
            preInterruptPlaylist = playlistManager.getActivePlaylist()
            preInterruptIndex = currentIndex
            isInterruptedBySchedule = true

            // 播放定时内容
            val savedOnItemComplete = ::onItemComplete
            playScheduledItems(items, 0, onComplete)
        }
    }

    /**
     * 递归播放定时内容列表
     */
    private fun playScheduledItems(
        items: List<PlaylistEntity>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (index >= items.size) {
            // 定时内容播放完毕，恢复原播放列表
            isInterruptedBySchedule = false
            onComplete()
            // 恢复之前的播放状态
            if (preInterruptPlaylist != null) {
                playlistManager.setActivePlaylist(preInterruptPlaylist!!)
                currentIndex = preInterruptIndex
                playNext()
            }
            return
        }

        currentMediaItem = items[index]
        hideAllViews()

        // 直接播放（不考虑 enabled 状态）
        when (MediaType.fromValue(items[index].type)) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                playVideo(items[index])
            }
            MediaType.IMAGE -> {
                playImage(items[index])
            }
            MediaType.WEBPAGE -> {
                playWebpage(items[index])
            }
        }

        // 定时内容播放完毕后播放下一项
        mainHandler.postDelayed({
            playScheduledItems(items, index + 1, onComplete)
        }, (items[index].duration ?: DEFAULT_IMAGE_DURATION) * 1000)
    }

    /**
     * 当前项播放完毕回调
     */
    private fun onItemComplete() {
        Log.d(TAG, "播放项完成: ${currentMediaItem?.title}")
        isPlaying = false
        playNext()
    }

    /**
     * 启动定时器
     */
    private fun startDurationTimer(durationMs: Long) {
        cancelDurationTimer()
        durationRunnable = Runnable {
            Log.d(TAG, "定时器到期，切换下一项")
            onItemComplete()
        }.also {
            mainHandler.postDelayed(it, durationMs)
        }
    }

    /**
     * 取消定时器
     */
    private fun cancelDurationTimer() {
        durationRunnable?.let {
            mainHandler.removeCallbacks(it)
            durationRunnable = null
        }
    }

    /**
     * Play next item with transition animation
     */
    private fun playItemWithTransition(item: PlaylistEntity) {
        val effect = if (transitionEffect == TransitionEffect.RANDOM) {
            TransitionEffect.values().filter { it != TransitionEffect.RANDOM && it != TransitionEffect.NONE }.random()
        } else {
            transitionEffect
        }

        if (effect == TransitionEffect.NONE) {
            playItem(item)
            return
        }

        updateState(PlaybackState.TRANSITION)

        when (effect) {
            TransitionEffect.FADE -> applyFadeTransition(item)
            TransitionEffect.ZOOM -> applyZoomTransition(item)
            TransitionEffect.SLIDE_LEFT -> applySlideTransition(item, fromLeft = true)
            TransitionEffect.SLIDE_RIGHT -> applySlideTransition(item, fromLeft = false)
            TransitionEffect.DISSOLVE -> applyDissolveTransition(item)
            else -> playItem(item)
        }
    }

    private fun applyFadeTransition(item: PlaylistEntity) {
        val currentView = getCurrentVisibleView() ?: run { playItem(item); return }
        currentView.animate()
            .alpha(0f)
            .setDuration(transitionDuration / 2)
            .withEndAction {
                hideAllViews()
                playItem(item)
                val newView = getCurrentVisibleView()
                newView?.alpha = 0f
                newView?.animate()?.alpha(1f)?.setDuration(transitionDuration / 2)?.start()
            }
            .start()
    }

    private fun applyZoomTransition(item: PlaylistEntity) {
        val currentView = getCurrentVisibleView() ?: run { playItem(item); return }
        currentView.animate()
            .alpha(0f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(transitionDuration / 2)
            .withEndAction {
                hideAllViews()
                playItem(item)
                val newView = getCurrentVisibleView()
                newView?.alpha = 0f
                newView?.scaleX = 0.8f
                newView?.scaleY = 0.8f
                newView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(transitionDuration / 2)?.start()
            }
            .start()
    }

    private fun applySlideTransition(item: PlaylistEntity, fromLeft: Boolean) {
        val currentView = getCurrentVisibleView() ?: run { playItem(item); return }
        val slideX = if (fromLeft) currentView.width.toFloat() else -currentView.width.toFloat()
        currentView.animate()
            .translationX(slideX)
            .alpha(0f)
            .setDuration(transitionDuration / 2)
            .withEndAction {
                hideAllViews()
                playItem(item)
                val newView = getCurrentVisibleView()
                val entryX = if (fromLeft) -slideX else slideX
                newView?.translationX = entryX
                newView?.alpha = 0f
                newView?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(transitionDuration / 2)?.start()
            }
            .start()
    }

    private fun applyDissolveTransition(item: PlaylistEntity) {
        // Dissolve: cross-fade with slight scale change
        val currentView = getCurrentVisibleView() ?: run { playItem(item); return }
        currentView.animate()
            .alpha(0.3f)
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(transitionDuration / 2)
            .withEndAction {
                hideAllViews()
                playItem(item)
                val newView = getCurrentVisibleView()
                newView?.alpha = 0.3f
                newView?.scaleX = 1.05f
                newView?.scaleY = 1.05f
                newView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(transitionDuration / 2)?.start()
            }
            .start()
    }

    /**
     * Get the currently visible view (playerView, imageView, or webView)
     */
    private fun getCurrentVisibleView(): View? {
        return when {
            playerView?.visibility == View.VISIBLE -> playerView
            imageView?.visibility == View.VISIBLE -> imageView
            webView?.visibility == View.VISIBLE -> webView
            else -> null
        }
    }

    // ======================== Background Music / TTS Methods ========================

    /**
     * Start playing background music
     */
    fun playBackgroundMusic(filePath: String) {
        stopBackgroundMusic()
        try {
            bgMusicPlayer = android.media.MediaPlayer().apply {
                setDataSource(filePath)
                setOnPreparedListener { mp ->
                    mp.setVolume(bgMusicVolume, bgMusicVolume)
                    mp.start()
                }
                setOnCompletionListener {
                    // Play next in queue if available
                    playNextBgMusic()
                }
                setOnErrorListener { _, _, _ ->
                    playNextBgMusic()
                    true
                }
                prepareAsync()
            }
            currentBgMusicPath = filePath
            Log.d(TAG, "Background music started: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play background music", e)
        }
    }

    /**
     * Play TTS audio list as background music
     */
    fun playTtsAudioList(audioPaths: List<String>, startIndex: Int = 0) {
        ttsAudioQueue = audioPaths
        ttsAudioIndex = startIndex
        if (audioPaths.isNotEmpty() && startIndex < audioPaths.size) {
            playBackgroundMusic(audioPaths[startIndex])
        }
    }

    private fun playNextBgMusic() {
        if (ttsAudioQueue.isNotEmpty()) {
            ttsAudioIndex = (ttsAudioIndex + 1) % ttsAudioQueue.size
            playBackgroundMusic(ttsAudioQueue[ttsAudioIndex])
        } else {
            stopBackgroundMusic()
        }
    }

    /**
     * Stop background music and release resources
     */
    fun stopBackgroundMusic() {
        try {
            bgMusicPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing bg music player", e)
        }
        bgMusicPlayer = null
        currentBgMusicPath = null
    }

    /**
     * Set background music volume (0-1)
     */
    fun setBgMusicVolume(volume: Float) {
        bgMusicVolume = volume.coerceIn(0f, 1f)
        bgMusicPlayer?.setVolume(bgMusicVolume, bgMusicVolume)
    }

    /**
     * Toggle video mute (when background music is playing)
     */
    fun setVideoMuted(muted: Boolean) {
        isVideoMuted = muted
        if (muted) {
            exoPlayer.volume = 0f
        } else {
            exoPlayer.volume = (currentMediaItem?.volume?.toFloat()?.div(100f)) ?: 1f
        }
    }

    /**
     * 隐藏所有播放视图
     */
    private fun hideAllViews() {
        playerView?.visibility = View.GONE
        imageView?.visibility = View.GONE
        webView?.visibility = View.GONE
    }

    /**
     * 更新播放状态
     */
    private fun updateState(state: PlaybackState) {
        currentState = state
        onStateChanged?.invoke(state, currentMediaItem)
    }

    /**
     * 播放进度数据类
     */
    data class PlaybackProgress(
        val currentIndex: Int,
        val position: Long,   // 当前播放位置（毫秒）
        val duration: Long     // 总时长（毫秒）
    )
}
