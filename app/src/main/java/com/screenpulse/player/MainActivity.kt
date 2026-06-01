package com.screenpulse.player

import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.screenpulse.player.data.entity.MediaType
import com.screenpulse.player.data.entity.PlaylistConfig
import com.screenpulse.player.player.PlaylistManager
import com.screenpulse.player.qrcode.QRCodeGenerator
import com.screenpulse.player.server.WebServer
import com.screenpulse.player.util.NetworkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity for the ScreenPulse digital signage player.
 *
 * Responsibilities:
 * - Hides system bars for immersive fullscreen experience
 * - Shows QR code splash when no playlist is configured
 * - Manages ExoPlayer lifecycle for video/IPTV/stream playback
 * - Displays images with configurable duration
 * - Renders PPT/PDF content via WebView
 * - Listens for playlist changes and reacts accordingly
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_IMAGE_DURATION_MS = 10_000L
        private const val WEB_SERVER_PORT = 8080
    }

    // ── Views ────────────────────────────────────────────────────────────
    private lateinit var playerView: PlayerView
    private lateinit var imageView: ImageView
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var qrSplash: LinearLayout
    private lateinit var qrImageView: ImageView
    private lateinit var qrUrlText: TextView
    private lateinit var qrInstructionsText: TextView

    // ── Player ───────────────────────────────────────────────────────────
    private var exoPlayer: ExoPlayer? = null
    private var playlistManager: PlaylistManager? = null

    // ── Server ───────────────────────────────────────────────────────────
    private var webServer: WebServer? = null

    // ── Image display timer ──────────────────────────────────────────────
    private var imageDisplayJob: Job? = null

    // ── Network receiver ─────────────────────────────────────────────────
    private var networkReceiver: android.content.BroadcastReceiver? = null

    // ── State ───────────────────────────────────────────────────────────
    private var isShowingQrSplash = false
    private var currentVolume = 80

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()
        bindViews()
        initWebView()
        initExoPlayer()
        startWebServer()
        observePlaylist()
        registerNetworkReceiver()
    }

    // =====================================================================
    //  System bar immersion
    // =====================================================================

    private fun hideSystemBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    // =====================================================================
    //  View binding
    // =====================================================================

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        imageView = findViewById(R.id.imageView)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        qrSplash = findViewById(R.id.qrSplash)
        qrImageView = findViewById(R.id.qrImageView)
        qrUrlText = findViewById(R.id.qrUrlText)
        qrInstructionsText = findViewById(R.id.qrInstructionsText)
    }

    // =====================================================================
    //  WebView initialization
    // =====================================================================

    private fun initWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    // =====================================================================
    //  ExoPlayer initialization
    // =====================================================================

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            volume = currentVolume / 100f
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            progressBar.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "Playback ended for current item, advancing")
                            onCurrentItemFinished()
                        }
                        Player.STATE_IDLE -> {
                            progressBar.visibility = View.GONE
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    progressBar.visibility = View.GONE
                    // Skip to next item on error
                    onCurrentItemFinished()
                }
            })
        }
        playerView.player = exoPlayer
    }

    // =====================================================================
    //  Web server
    // =====================================================================

    private fun startWebServer() {
        try {
            webServer = WebServer(WEB_SERVER_PORT, this).apply {
                start()
                Log.d(TAG, "Web server started on port $WEB_SERVER_PORT")
            }
            // Generate and display QR code
            displayQrSplash()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server", e)
        }
    }

    // =====================================================================
    //  QR code splash screen
    // =====================================================================

    private fun displayQrSplash() {
        val ipAddress = NetworkUtil.getDeviceIpAddress(this) ?: "unknown"
        val port = WEB_SERVER_PORT
        val managementUrl = "http://$ipAddress:$port"

        val qrBitmap = QRCodeGenerator.generate(managementUrl, 512)
        qrImageView.setImageBitmap(qrBitmap)
        qrUrlText.text = managementUrl
        qrInstructionsText.text = getString(R.string.qr_instructions)
    }

    private fun showQrSplash() {
        if (!isShowingQrSplash) {
            isShowingQrSplash = true
            stopAllPlayback()
            qrSplash.visibility = View.VISIBLE
            displayQrSplash()
        }
    }

    private fun hideQrSplash() {
        if (isShowingQrSplash) {
            isShowingQrSplash = false
            qrSplash.visibility = View.GONE
        }
    }

    // =====================================================================
    //  Playlist observation
    // =====================================================================

    private fun observePlaylist() {
        val dao = (application as ScreenPulseApp).database.mediaItemDao()
        val configDao = (application as ScreenPulseApp).database.playlistConfigDao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dao.getEnabledItems().collect { items ->
                        if (items.isEmpty()) {
                            showQrSplash()
                            playlistManager?.stop()
                            playlistManager = null
                        } else {
                            hideQrSplash()
                            updatePlaylistManager(items)
                        }
                    }
                }

                launch {
                    configDao.getConfig().collect { config ->
                        config?.let { applyConfig(it) }
                    }
                }
            }
        }
    }

    private fun updatePlaylistManager(items: List<com.screenpulse.player.data.entity.MediaItem>) {
        if (playlistManager == null) {
            playlistManager = PlaylistManager(items.toMutableList())
            playCurrentItem()
        } else {
            val changed = playlistManager!!.updateItems(items)
            if (changed && playlistManager!!.hasItems()) {
                // Items changed significantly, restart playback with new item
                playCurrentItem()
            }
        }
    }

    private fun applyConfig(config: PlaylistConfig) {
        currentVolume = config.volumeLevel
        exoPlayer?.volume = currentVolume / 100f
        playlistManager?.setPlaybackMode(config.playbackMode)
        playlistManager?.setInterstitialConfig(
            enabled = config.interstitialEnabled,
            startHour = config.interstitialStartHour,
            endHour = config.interstitialEndHour
        )
    }

    // =====================================================================
    //  Playback control
    // =====================================================================

    private fun playCurrentItem() {
        val manager = playlistManager ?: return
        val item = manager.currentItem() ?: return
        stopAllPlayback()

        Log.d(TAG, "Playing item: ${item.title} (${item.type}) URL: ${item.url}")

        when (item.type) {
            MediaType.VIDEO, MediaType.IPTV, MediaType.STREAM -> {
                playVideoItem(item)
            }
            MediaType.IMAGE -> {
                displayImage(item)
            }
            MediaType.PPT -> {
                displayPresentation(item)
            }
        }
    }

    private fun playVideoItem(item: com.screenpulse.player.data.entity.MediaItem) {
        showPlayerView()

        val mediaItem = MediaItem.fromUri(Uri.parse(item.url))
        exoPlayer?.apply {
            stop()
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    private fun displayImage(item: com.screenpulse.player.data.entity.MediaItem) {
        showImageView()

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val url = if (item.url.startsWith("http", ignoreCase = true) || item.url.startsWith("content://")) {
                        item.url
                    } else if (item.url.startsWith("/")) {
                        "file://$item.url"
                    } else {
                        "file://${android.os.Environment.getExternalStorageDirectory()}/${item.url}"
                    }
                    BitmapFactory.decodeStream(
                        android.net.Uri.parse(url).let { uri ->
                            contentResolver.openInputStream(uri)
                        }
                    )
                }
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    Log.e(TAG, "Failed to decode image: ${item.url}")
                    onCurrentItemFinished()
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image: ${item.url}", e)
                onCurrentItemFinished()
                return@launch
            }

            // Show image for the configured duration
            val duration = if (item.durationSeconds > 0) {
                item.durationSeconds * 1000L
            } else {
                DEFAULT_IMAGE_DURATION_MS
            }

            imageDisplayJob?.cancel()
            imageDisplayJob = lifecycleScope.launch {
                delay(duration)
                onCurrentItemFinished()
            }
        }
    }

    private fun displayPresentation(item: com.screenpulse.player.data.entity.MediaItem) {
        showWebView()

        val url = if (item.url.startsWith("http", ignoreCase = true)) {
            item.url
        } else {
            "file://${android.os.Environment.getExternalStorageDirectory()}/${item.url}"
        }

        webView.loadUrl(url)

        // If PPT has a custom duration, auto-advance after that time
        if (item.durationSeconds > 0) {
            imageDisplayJob?.cancel()
            imageDisplayJob = lifecycleScope.launch {
                delay(item.durationSeconds * 1000L)
                onCurrentItemFinished()
            }
        } else {
            // Default PPT duration: 60 seconds
            imageDisplayJob?.cancel()
            imageDisplayJob = lifecycleScope.launch {
                delay(60_000L)
                onCurrentItemFinished()
            }
        }
    }

    private fun onCurrentItemFinished() {
        val manager = playlistManager ?: return
        val hasNext = manager.advanceToNext()
        if (hasNext) {
            playCurrentItem()
        } else {
            Log.d(TAG, "Playlist finished (sequential mode)")
            showQrSplash()
        }
    }

    private fun stopAllPlayback() {
        imageDisplayJob?.cancel()
        imageDisplayJob = null
        exoPlayer?.stop()
        imageView.setImageBitmap(null)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        progressBar.visibility = View.GONE
    }

    // =====================================================================
    //  View switching
    // =====================================================================

    private fun showPlayerView() {
        playerView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        webView.visibility = View.GONE
    }

    private fun showImageView() {
        playerView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun showWebView() {
        playerView.visibility = View.GONE
        imageView.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    // =====================================================================
    //  Network connectivity receiver
    // =====================================================================

    private fun registerNetworkReceiver() {
        networkReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                // Re-display QR code with potentially updated IP
                if (isShowingQrSplash) {
                    displayQrSplash()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction("android.net.wifi.STATE_CHANGE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(networkReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(networkReceiver, filter)
        }
    }

    // =====================================================================
    //  Lifecycle
    // =====================================================================

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        exoPlayer?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        imageDisplayJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        webServer?.stop()
        webServer = null
        playlistManager = null
        try {
            networkReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
    }
}
