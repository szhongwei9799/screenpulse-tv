package com.screenpulse.player.schedule

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.screenpulse.player.ScreenPulseApp

/**
 * Periodic WorkManager worker that checks interstitial schedule conditions.
 *
 * This worker is scheduled by [ScreenPulseApp] to run every 15 minutes.
 * It reads the [PlaylistConfig] from the Room database and checks if the
 * current time falls within the configured interstitial window.
 *
 * When the interstitial window starts or ends, it broadcasts an intent
 * that the [MainActivity] can react to by switching playlists.
 */
class ScheduleCheckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ScheduleCheckWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Running periodic schedule check")

        return try {
            val scheduleManager = ScheduleManager(applicationContext)
            scheduleManager.checkInterstitialSchedule()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Schedule check failed", e)
            Result.retry()
        }
    }
}
