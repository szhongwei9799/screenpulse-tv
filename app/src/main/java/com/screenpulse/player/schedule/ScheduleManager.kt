package com.screenpulse.player.schedule

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.screenpulse.player.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages scheduled tasks related to interstitial playlists and periodic
 * content synchronization.
 *
 * Uses WorkManager to ensure reliable execution even when the device
 * is in Doze mode or the app is not in the foreground.
 */
class ScheduleManager(private val context: Context) {

    companion object {
        private const val TAG = "ScheduleManager"
        private const val WORK_TAG_SCHEDULE = "interstitial_schedule_check"
        private const val WORK_TAG_SYNC = "content_sync"
    }

    private val database = AppDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callback when interstitial state changes
    var onInterstitialStateChanged: ((shouldPlayInterstitial: Boolean) -> Unit)? = null

    // Track current interstitial state to detect changes
    private var isCurrentlyInterstitial = false

    /**
     * Checks if the current time falls within the interstitial schedule window.
     */
    suspend fun checkInterstitialSchedule() {
        try {
            val config = database.playlistConfigDao().getConfigOnce() ?: return
            if (!config.interstitialEnabled) {
                notifyInterstitialChange(false)
                return
            }

            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val inWindow = if (config.interstitialStartHour <= config.interstitialEndHour) {
                currentHour in config.interstitialStartHour until config.interstitialEndHour
            } else {
                // Wraps around midnight
                currentHour >= config.interstitialStartHour || currentHour < config.interstitialEndHour
            }

            notifyInterstitialChange(inWindow)
            Log.d(TAG, "Interstitial check: hour=$currentHour, window=${config.interstitialStartHour}-${config.interstitialEndHour}, inWindow=$inWindow")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking interstitial schedule", e)
        }
    }

    /**
     * Notifies listeners if the interstitial state has changed.
     */
    private fun notifyInterstitialChange(shouldPlayInterstitial: Boolean) {
        if (shouldPlayInterstitial != isCurrentlyInterstitial) {
            isCurrentlyInterstitial = shouldPlayInterstitial
            Log.d(TAG, "Interstitial state changed: $shouldPlayInterstitial")
            scope.launch {
                onInterstitialStateChanged?.invoke(shouldPlayInterstitial)
            }
        }
    }

    /**
     * Schedules a one-time interstitial check using WorkManager.
     * Useful for checking at the exact minute a schedule starts/ends.
     */
    fun scheduleOneTimeCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ScheduleCheckWorker>()
            .setConstraints(constraints)
            .addTag(WORK_TAG_SCHEDULE)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_TAG_SCHEDULE,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Returns whether we are currently in the interstitial window.
     */
    fun isCurrentlyInInterstitial(): Boolean = isCurrentlyInterstitial

    /**
     * Cancels all scheduled work.
     */
    fun cancelAllSchedules() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG_SCHEDULE)
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG_SYNC)
    }
}
