package com.screenpulse.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.screenpulse.tv.db.AppDatabase
import com.screenpulse.tv.server.WebServerManager
import com.screenpulse.tv.schedule.ScheduleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ScreenPulse TV Player 应用入口
 * 负责全局初始化：数据库、Web服务器、定时调度器
 */
class ScreenPulseApp : Application() {

    // 全局协程作用域，使用 SupervisorJob 避免子协程异常导致全部取消
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 数据库实例（懒加载）
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    // Web 服务器管理器
    lateinit var webServerManager: WebServerManager
        private set

    // 定时调度管理器
    lateinit var scheduleManager: ScheduleManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 创建通知渠道（Android 8.0+）
        createNotificationChannels()

        // 初始化 Web 服务器
        webServerManager = WebServerManager(this).apply {
            start()
        }

        // 初始化定时调度器
        scheduleManager = ScheduleManager(this)

        // 异步预热数据库
        applicationScope.launch {
            database.playlistDao().getActivePlaylistCount()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        webServerManager.stop()
        applicationScope.cancel()
    }

    /**
     * 创建通知渠道
     * 用于前台服务和播放控制通知
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    "播放服务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "后台播放服务通知"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_SCHEDULE,
                    "定时任务",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "定时播放任务通知"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_SERVER,
                    "Web服务器",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "内嵌Web服务器状态通知"
                    setShowBadge(false)
                }
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(channels)
        }
    }

    companion object {
        private const val CHANNEL_PLAYBACK = "screenpulse_playback"
        private const val CHANNEL_SCHEDULE = "screenpulse_schedule"
        private const val CHANNEL_SERVER = "screenpulse_server"

        @Volatile
        private lateinit var instance: ScreenPulseApp

        /**
         * 获取全局 Application 实例
         */
        fun getInstance(): ScreenPulseApp = instance
    }
}
