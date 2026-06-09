package com.screenpulse.tv.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.screenpulse.tv.ScreenPulseApp
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.player.PlaybackEngine
import com.screenpulse.tv.player.PlaylistManager
import com.screenpulse.tv.server.ApiHandler
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放视图模型
 *
 * 管理 PlaybackFragment 和 PlaybackEngine 之间的交互
 * 处理播放列表加载、播放控制、状态管理等逻辑
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
    }

    private val app = application as ScreenPulseApp
    private val database = AppDatabase.getInstance(application)
    private val playlistManager = PlaylistManager(application)

    /** 播放引擎（延迟初始化） */
    private var playbackEngine: PlaybackEngine? = null

    /** 播放状态 LiveData */
    private val _playbackState = MutableLiveData<PlaybackEngine.PlaybackState>()
    val playbackState: LiveData<PlaybackEngine.PlaybackState> = _playbackState

    /** 当前播放项标题 */
    private val _currentTitle = MutableLiveData<String>()
    val currentTitle: LiveData<String> = _currentTitle

    /** 当前播放索引 */
    private val _currentIndex = MutableLiveData<Int>()
    val currentIndex: LiveData<Int> = _currentIndex

    /** 播放列表总数 */
    private val _totalItems = MutableLiveData<Int>()
    val totalItems: LiveData<Int> = _totalItems

    /** 是否正在播放 */
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    init {
        // 初始化播放引擎
        playbackEngine = PlaybackEngine(application).apply {
            // 设置状态变化监听
            onStateChanged = { state, item ->
                _playbackState.postValue(state)
                _currentTitle.postValue(item?.title ?: "")
                _isPlaying.postValue(state == PlaybackEngine.PlaybackState.PLAYING)
            }
        }

        // 初始化 API 控制回调
        setupApiCallbacks()

        // 加载播放列表
        loadPlaylist()
    }

    /**
     * 设置 API 控制回调
     * 将 Web API 的控制请求桥接到 ViewModel
     */
    private fun setupApiCallbacks() {
        app.webServerManager.let { server ->
            // 获取 ApiHandler 并设置回调
            // 注意：需要通过反射或暴露方法来设置
            // 这里简化处理
        }
    }

    /**
     * 绑定播放引擎到视图容器
     */
    fun attachToContainer(container: ViewGroup) {
        playbackEngine?.attachViews(container)
        playbackEngine?.start()
    }

    /**
     * 解绑视图容器
     */
    fun detachFromContainer() {
        // 停止但不要释放引擎，保持播放状态
    }

    /**
     * 加载播放列表
     */
    private fun loadPlaylist() {
        viewModelScope.launch {
            playlistManager.getActivePlaylist().let { items ->
                _totalItems.postValue(items.size)
                Log.d(TAG, "播放列表已加载: ${items.size} 项")
            }
        }
    }

    /**
     * 播放
     */
    fun play() {
        playbackEngine?.let {
            if (it.getCurrentItem() == null) {
                it.start()
            } else {
                it.resume()
            }
        }
    }

    /**
     * 暂停
     */
    fun pause() {
        playbackEngine?.pause()
    }

    /**
     * 播放/暂停切换
     */
    fun togglePlayPause() {
        if (_isPlaying.value == true) {
            pause()
        } else {
            play()
        }
    }

    /**
     * 跳到下一项
     */
    fun skipToNext() {
        playbackEngine?.playNext()
    }

    /**
     * 跳到上一项
     */
    fun skipToPrevious() {
        playbackEngine?.playPrevious()
    }

    /**
     * 跳到指定索引
     */
    fun jumpTo(index: Int) {
        playbackEngine?.jumpTo(index)
    }

    /**
     * 恢复播放
     */
    fun resumePlayback() {
        playbackEngine?.let {
            if (it.getCurrentItem() != null && !it.isPlaying) {
                it.resume()
            }
        }
    }

    /**
     * 刷新播放列表
     */
    fun refreshPlaylist() {
        viewModelScope.launch {
            playlistManager.refreshPlaylist()
            loadPlaylist()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackEngine?.release()
        playbackEngine = null
    }
}