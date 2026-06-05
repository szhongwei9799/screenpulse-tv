package com.screenpulse.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.screenpulse.player.data.AppDatabase
import com.screenpulse.player.data.entity.MediaGroup
import com.screenpulse.player.data.entity.MediaGroupItem
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.schedule.ScheduleCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        const val DEFAULT_GROUP_NAME = "未分类"
        const val DEFAULT_GROUP_COLOR = "#909399"

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
        initDefaultGroupAndPlaylist()
    }

    /**
     * Creates the default "未分类" group if it doesn't exist.
     * Playlist management is left to the user via the admin panel.
     */
    private fun initDefaultGroupAndPlaylist() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groupDao = database.mediaGroupDao()
                val mediaItemDao = database.mediaItemDao()

                // Create default "未分类" group if it doesn't exist
                val existingGroups = groupDao.getAllGroupsOnce()
                var defaultGroup = existingGroups.find { it.name == DEFAULT_GROUP_NAME }
                if (defaultGroup == null) {
                    val groupId = groupDao.insert(
                        MediaGroup(name = DEFAULT_GROUP_NAME, color = DEFAULT_GROUP_COLOR, sortOrder = 0)
                    )
                    defaultGroup = MediaGroup(id = groupId, name = DEFAULT_GROUP_NAME, color = DEFAULT_GROUP_COLOR, sortOrder = 0)
                    Log.i("ScreenPulseApp", "Created default group: $DEFAULT_GROUP_NAME (id=$groupId)")
                }

                // Note: Do NOT auto-add default group to playlist.
                // User will manage playlist items manually via the admin panel.
            } catch (e: Exception) {
                Log.e("ScreenPulseApp", "Failed to init default group/playlist", e)
            }
        }
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
