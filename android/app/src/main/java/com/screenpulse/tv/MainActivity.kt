package com.screenpulse.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.player.PlaybackEngine
import com.screenpulse.tv.ui.LandingFragment
import com.screenpulse.tv.ui.PlaybackFragment
import com.screenpulse.tv.ui.SettingsFragment
import com.screenpulse.tv.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ScreenPulse TV Player 主 Activity
 * Leanback 启动器入口，负责全屏沉浸式显示和导航
 */
class MainActivity : FragmentActivity() {

    private lateinit var playbackEngine: PlaybackEngine

    // 当前显示的片段类型
    private enum class Screen {
        LANDING,      // 首次运行 - 显示 QR 码和管理地址
        PLAYBACK,     // 播放中 - 全屏播放
        SETTINGS      // 设置
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 启用全屏沉浸式模式
        enableImmersiveMode()

        // 初始化播放引擎
        playbackEngine = PlaybackEngine(this)

        // 处理返回键 - 在 TV 上使用遥控器返回
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lifecycleScope.launch {
                    when (getCurrentScreen()) {
                        Screen.SETTINGS -> {
                            navigateTo(if (hasActivePlaylist()) Screen.PLAYBACK else Screen.LANDING)
                        }
                        Screen.PLAYBACK -> {
                            navigateTo(Screen.LANDING)
                        }
                        Screen.LANDING -> {
                            // 在首屏按返回退出应用
                            finish()
                        }
                    }
                }
            }
        })

        // 检查播放列表并决定显示哪个页面
        checkPlaylistAndNavigate()

        // 处理深度链接
        handleDeepLink(intent)

        // 监听播放列表变化，自动切换页面
        observePlaylistChanges()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleDeepLink(it) }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackEngine.release()
    }

    /**
     * 启用全屏沉浸式模式
     * 隐藏状态栏、导航栏，适用于 Android TV
     */
    @SuppressLint("ObsoleteSdkInt")
    private fun enableImmersiveMode() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // 隐藏系统 UI
        val decorView = window.decorView
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * 检查播放列表是否存在，决定导航目标
     */
    private fun checkPlaylistAndNavigate() {
        lifecycleScope.launch {
            val database = AppDatabase.getInstance(this@MainActivity)
            val count = withContext(Dispatchers.IO) {
                database.playlistDao().getActivePlaylistCount()
            }

            navigateTo(if (count > 0) Screen.PLAYBACK else Screen.LANDING)
        }
    }

    /**
     * 处理深度链接
     * 支持从浏览器或其他应用跳转到播放
     */
    private fun handleDeepLink(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                val mediaType = when (intent.type) {
                    "video/*" -> com.screenpulse.tv.player.MediaType.VIDEO
                    "image/*" -> com.screenpulse.tv.player.MediaType.IMAGE
                    else -> return
                }

                // 通过深度链接添加到播放列表并播放
                lifecycleScope.launch {
                    // TODO: 添加媒体到播放列表
                }
            }
        }
    }

    /**
     * 监听播放列表变化
     * 当播放列表从空变为有内容时，自动切换到播放页面
     */
    private fun observePlaylistChanges() {
        val database = AppDatabase.getInstance(this)
        database.playlistDao().getActivePlaylistFlow().observe(this) { items ->
            if (items.isEmpty() && getCurrentScreen() == Screen.PLAYBACK) {
                // 播放列表被清空，切换到首屏
                playbackEngine.stop()
                navigateTo(Screen.LANDING)
            } else if (items.isNotEmpty() && getCurrentScreen() == Screen.LANDING) {
                // 播放列表有了内容，开始播放
                navigateTo(Screen.PLAYBACK)
            }
        }
    }

    /**
     * 检查是否有活跃的播放列表
     */
    private suspend fun hasActivePlaylist(): Boolean {
        val database = AppDatabase.getInstance(this)
        return withContext(Dispatchers.IO) {
            database.playlistDao().getActivePlaylistCount() > 0
        }
    }

    /**
     * 获取当前显示的页面类型
     */
    private fun getCurrentScreen(): Screen {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.rootContainer)
        return when (currentFragment) {
            is LandingFragment -> Screen.LANDING
            is PlaybackFragment -> Screen.PLAYBACK
            is SettingsFragment -> Screen.SETTINGS
            else -> Screen.LANDING
        }
    }

    /**
     * 页面导航
     */
    private fun navigateTo(screen: Screen) {
        if (getCurrentScreen() == screen) return

        val fragment: Fragment = when (screen) {
            Screen.LANDING -> LandingFragment.newInstance()
            Screen.PLAYBACK -> PlaybackFragment.newInstance()
            Screen.SETTINGS -> SettingsFragment.newInstance()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.animator.fade_in,
                android.R.animator.fade_out,
                android.R.animator.fade_in,
                android.R.animator.fade_out
            )
            .replace(R.id.rootContainer, fragment)
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * 获取设备管理 URL
         */
        fun getManagementUrl(): String {
            val ip = NetworkUtils.getDeviceIpAddress()
            return "http://$ip:8080"
        }
    }
}
