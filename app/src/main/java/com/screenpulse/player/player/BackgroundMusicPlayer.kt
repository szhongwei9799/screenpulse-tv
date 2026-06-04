package com.screenpulse.player.player

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Plays background music using Android's MediaPlayer.
 * Designed to work alongside ExoPlayer for video playback.
 * When enabled, video audio is muted and this player provides background music.
 */
class BackgroundMusicPlayer(private val context: Context) {

    companion object {
        private const val TAG = "BgMusicPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var enabled: Boolean = false
    private var volume: Int = 50
    private var looping: Boolean = true
    private var shuffle: Boolean = false
    private var musicFiles: List<String> = emptyList()
    private var currentIndex: Int = 0

    /**
     * Update the list of available music files.
     */
    fun setMusicFiles(files: List<String>) {
        musicFiles = files
        if (files.isEmpty()) {
            stop()
        }
    }

    /**
     * Set volume (0-100).
     */
    fun setVolume(vol: Int) {
        volume = vol.coerceIn(0, 100)
        mediaPlayer?.setVolume(volume / 100f, volume / 100f)
    }

    /**
     * Set loop mode.
     */
    fun setLooping(loop: Boolean) {
        looping = loop
        mediaPlayer?.isLooping = loop
    }

    fun enable() {
        if (musicFiles.isEmpty()) {
            Log.w(TAG, "No music files to play")
            return
        }
        enabled = true
        playCurrent()
    }

    fun disable() {
        enabled = false
        stop()
    }

    fun isEnabled(): Boolean = enabled

    private fun playCurrent() {
        if (!enabled || musicFiles.isEmpty()) return
        try {
            mediaPlayer?.release()
            val mp = MediaPlayer()
            mp.setDataSource(musicFiles[currentIndex])
            mp.prepareAsync()
            mp.setOnPreparedListener { player ->
                player.setVolume(volume / 100f, volume / 100f)
                player.isLooping = looping
                player.start()
                Log.d(TAG, "Playing bg music: ${musicFiles[currentIndex]}")
            }
            mp.setOnCompletionListener {
                if (enabled && !looping) {
                    playNext()
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                Log.e(TAG, "Error playing music, trying next")
                playNext()
                true
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play music: ${e.message}")
            playNext()
        }
    }

    private fun playNext() {
        if (musicFiles.isEmpty()) return
        if (shuffle) {
            currentIndex = (0 until musicFiles.size).random()
        } else {
            currentIndex = (currentIndex + 1) % musicFiles.size
        }
        playCurrent()
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        stop()
        musicFiles = emptyList()
    }

    fun getCurrentTitle(): String {
        return if (musicFiles.isNotEmpty() && currentIndex in musicFiles.indices) {
            File(musicFiles[currentIndex]).nameWithoutExtension
        } else ""
    }
}
