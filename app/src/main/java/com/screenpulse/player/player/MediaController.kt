package com.screenpulse.player.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controls the ExoPlayer instance for media playback.
 * Handles video, image, IPTV (m3u8), and stream URL playback.
 *
 * This class is stateful and wraps a single [ExoPlayer] instance.
 * It provides methods for preparing media, controlling playback,
 * and managing image display durations.
 */
class MediaController(private val context: Context) {

    companion object {
        private const val TAG = "MediaController"
        private const val DEFAULT_IMAGE_DURATION_MS = 10_000L
        private const val BUFFER_SIZE = 64 * 1024  // 64 KB
        private const val CONNECT_TIMEOUT = 30_000  // 30 seconds
        private const val READ_TIMEOUT = 30_000    // 30 seconds
    }

    private val player: ExoPlayer
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var imageDisplayJob: Job? = null

    // Callbacks
    var onPlaybackEnded: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null
    var onImageDisplayComplete: (() -> Unit)? = null

    init {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT)
            .setReadTimeoutMs(READ_TIMEOUT)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("ScreenPulsePlayer/1.0")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> Log.d(TAG, "Buffering...")
                    Player.STATE_READY -> Log.d(TAG, "Ready to play")
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Playback ended naturally")
                        onPlaybackEnded?.invoke()
                    }
                    Player.STATE_IDLE -> Log.d(TAG, "Player idle")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.errorCodeName} - ${error.message}")
                onPlaybackError?.invoke(error.message ?: "Unknown playback error")
            }
        })
    }

    // =====================================================================
    //  Public playback controls
    // =====================================================================

    /**
     * Prepares and plays a media item.
     */
    fun prepareMedia(item: com.screenpulse.player.data.entity.MediaItem) {
        Log.d(TAG, "prepareMedia: ${item.title} (${item.type})")

        imageDisplayJob?.cancel()
        imageDisplayJob = null

        when (item.type) {
            com.screenpulse.player.data.entity.MediaType.VIDEO -> handleVideo(item)
            com.screenpulse.player.data.entity.MediaType.IMAGE -> handleImage(item)
            com.screenpulse.player.data.entity.MediaType.PPT -> handlePresentation(item)
            com.screenpulse.player.data.entity.MediaType.IPTV -> handleIPTV(item)
            com.screenpulse.player.data.entity.MediaType.STREAM -> handleStream(item)
        }
    }

    /**
     * Resumes playback.
     */
    fun play() {
        player.playWhenReady = true
        player.play()
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        player.playWhenReady = false
    }

    /**
     * Stops playback and resets the player.
     */
    fun stop() {
        imageDisplayJob?.cancel()
        imageDisplayJob = null
        player.stop()
    }

    /**
     * Jumps to the next media item. Notifies via [onPlaybackEnded].
     */
    fun next() {
        stop()
        onPlaybackEnded?.invoke()
    }

    /**
     * Jumps to the previous media item. Notifies via [onPlaybackEnded].
     */
    fun previous() {
        stop()
        onPlaybackEnded?.invoke()
    }

    // =====================================================================
    //  Media type handlers
    // =====================================================================

    /**
     * Handles standard video file playback.
     */
    private fun handleVideo(item: com.screenpulse.player.data.entity.MediaItem) {
        val uri = resolveUri(item.url)
        val mediaItem = MediaItem.fromUri(uri)

        player.apply {
            stop()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            play()
        }
    }

    /**
     * Handles image display with configurable duration.
     * The caller (e.g., [MainActivity]) is responsible for loading the image
     * into an ImageView. This controller only handles the timing.
     */
    private fun handleImage(item: com.screenpulse.player.data.entity.MediaItem) {
        val durationMs = if (item.durationSeconds > 0) {
            item.durationSeconds * 1000L
        } else {
            DEFAULT_IMAGE_DURATION_MS
        }

        imageDisplayJob = scope.launch {
            delay(durationMs)
            onImageDisplayComplete?.invoke()
        }
    }

    /**
     * Handles presentation (PPT/PDF) display.
     * Duration defaults to 60 seconds if not specified.
     */
    private fun handlePresentation(item: com.screenpulse.player.data.entity.MediaItem) {
        val durationMs = if (item.durationSeconds > 0) {
            item.durationSeconds * 1000L
        } else {
            60_000L
        }

        imageDisplayJob = scope.launch {
            delay(durationMs)
            onImageDisplayComplete?.invoke()
        }
    }

    /**
     * Handles IPTV / HLS (m3u8) stream playback.
     * Uses HlsMediaSource for proper HLS support.
     */
    private fun handleIPTV(item: com.screenpulse.player.data.entity.MediaItem) {
        val uri = resolveUri(item.url)
        val hlsMediaSource = HlsMediaSource.Factory(
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(CONNECT_TIMEOUT)
                .setReadTimeoutMs(READ_TIMEOUT)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("ScreenPulsePlayer/1.0")
        ).createMediaSource(MediaItem.fromUri(uri))

        player.apply {
            stop()
            setMediaSource(hlsMediaSource)
            prepare()
            playWhenReady = true
            play()
        }
    }

    /**
     * Handles generic stream URL playback (DASH, smooth streaming, etc.).
     */
    private fun handleStream(item: com.screenpulse.player.data.entity.MediaItem) {
        val uri = resolveUri(item.url)
        val mediaItem = MediaItem.fromUri(uri)

        player.apply {
            stop()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            play()
        }
    }

    // =====================================================================
    //  Volume and playback speed
    // =====================================================================

    /**
     * Sets the volume level (0-100).
     */
    fun setVolume(level: Int) {
        val volume = level.coerceIn(0, 100) / 100f
        player.volume = volume
    }

    /**
     * Sets playback speed (0.5x to 3.0x).
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        player.playbackParameters = PlaybackParameters(clampedSpeed)
    }

    // =====================================================================
    //  State queries
    // =====================================================================

    val isPlaying: Boolean
        get() = player.isPlaying

    val currentPosition: Long
        get() = player.currentPosition

    val duration: Long
        get() = player.duration

    val exoPlayer: ExoPlayer
        get() = player

    // =====================================================================
    //  Cleanup
    // =====================================================================

    fun release() {
        imageDisplayJob?.cancel()
        player.release()
    }

    // =====================================================================
    //  Utilities
    // =====================================================================

    private fun resolveUri(url: String): Uri {
        return when {
            url.startsWith("http://") || url.startsWith("https://") ||
            url.startsWith("content://") || url.startsWith("file://") -> Uri.parse(url)
            url.startsWith("/") -> Uri.parse("file://$url")
            else -> Uri.parse("file://${android.os.Environment.getExternalStorageDirectory()}/$url")
        }
    }
}
