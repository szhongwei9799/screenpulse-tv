package com.screenpulse.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.screenpulse.player.data.AppDatabase
import com.screenpulse.player.schedule.ScheduleCheckWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for ScreenPulse digital signage player.
 * Initializes the Room database, WorkManager, and notification channels.
 */
class ScreenPulseApp : Application(), Configuration.Provider {

    companion object {
        const val CHANNEL_PLAYBACK = "screenpulse_playback"
        const val CHANNEL_SCHEDULE = "screenpulse_schedule"
        const val TAG_SCHEDULE_WORK = "schedule_check"

        @Volatile
        lateinit var instance: ScreenPulseApp
            private set
    }

    // Lazy-initialized database instance
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    // SharedPreferences for app settings
    val appPreferences by lazy {
        getSharedPreferences("screenpulse_prefs", MODE_PRIVATE)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        schedulePeriodicWork()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Playback Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the playback service is running"
                setShowBadge(false)
            }

            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE,
                "Schedule Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for schedule changes"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(playbackChannel)
            notificationManager.createNotificationChannel(scheduleChannel)
        }
    }

    /**
     * Sets up periodic WorkManager job to check interstitial schedules.
     * Runs every 15 minutes when the device has network connectivity.
     */
    private fun schedulePeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val scheduleRequest = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TAG_SCHEDULE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            scheduleRequest
        )
    }
}
