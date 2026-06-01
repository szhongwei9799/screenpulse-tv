package com.screenpulse.player.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.screenpulse.player.MainActivity
import com.screenpulse.player.R
import com.screenpulse.player.ScreenPulseApp

/**
 * Foreground service that keeps playback running even when the activity
 * is not in the foreground. Ensures the digital signage continues to display
 * content on Android TV devices that may kill background processes.
 *
 * This service holds the [MediaController] instance and provides it to
 * bound activities.
 */
class PlaybackService : Service() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.screenpulse.player.action.PLAY"
        const val ACTION_PAUSE = "com.screenpulse.player.action.PAUSE"
        const val ACTION_STOP = "com.screenpulse.player.action.STOP"
        const val ACTION_SET_VOLUME = "com.screenpulse.player.action.SET_VOLUME"
        private const val EXTRA_VOLUME = "extra_volume"

        /**
         * Starts the playback service as a foreground service.
         */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the playback service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    private val binder = LocalBinder()
    private var mediaController: MediaController? = null
    private var playlistManager: PlaylistManager? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
        fun getMediaController(): MediaController? = mediaController
        fun getPlaylistManager(): PlaylistManager? = playlistManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService created")
        mediaController = MediaController(this)
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PlaybackService started")
        startForegroundNotification()

        when (intent?.action) {
            ACTION_PLAY -> {
                mediaController?.play()
            }
            ACTION_PAUSE -> {
                mediaController?.pause()
            }
            ACTION_STOP -> {
                mediaController?.stop()
                stopSelf()
            }
            ACTION_SET_VOLUME -> {
                val volume = intent.getIntExtra(EXTRA_VOLUME, 80)
                mediaController?.setVolume(volume)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return true // Allow rebind
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PlaybackService destroyed")
        mediaController?.release()
        mediaController = null
        playlistManager = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Sets the playlist manager for this service.
     */
    fun setPlaylistManager(manager: PlaylistManager) {
        this.playlistManager = manager
    }

    // =====================================================================
    //  Notification
    // =====================================================================

    private fun startForegroundNotification() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Pause action
        val pauseIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ScreenPulseApp.CHANNEL_PLAYBACK)
            .setContentTitle(getString(R.string.playback_notification_title))
            .setContentText(getString(R.string.playback_notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

}
