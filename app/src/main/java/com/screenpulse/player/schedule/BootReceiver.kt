package com.screenpulse.player.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.screenpulse.player.MainActivity
import com.screenpulse.player.player.PlaybackService

/**
 * Boot receiver that auto-starts the ScreenPulse player when the device boots.
 *
 * This ensures the digital signage display starts automatically after a reboot
 * without requiring manual user interaction — essential for unattended TV deployments.
 *
 * Registered to receive:
 * - BOOT_COMPLETED
 * - QUICKBOOT_POWERON (some manufacturer-specific boot events)
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htg.intent.action.BOOT_COMPLETED"
        ) {
            Log.d(TAG, "Device booted, starting ScreenPulse player")

            // Start the playback service as foreground
            PlaybackService.start(context)

            // Launch the main activity
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            // Small delay to let system services initialize
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ requires explicit pending intent flags
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                pendingIntent.send()
            } else {
                @Suppress("DEPRECATION")
                context.startActivity(launchIntent)
            }
        }
    }
}
