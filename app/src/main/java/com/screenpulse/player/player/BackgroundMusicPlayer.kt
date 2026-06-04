package com.screenpulse.player.player

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.util.Collections
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

/**
 * Plays background music (uploaded audio files + TTS-generated audio) using Android MediaPlayer.
 * Supports sequential and shuffle playback modes.
 */
class BackgroundMusicPlayer(private val context: Context) {

    companion object {
        private const val TAG = "BgMusicPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private val playlist = LinkedList<String>() // file paths
    private var currentIndex = 0
    private var isLooping = false
    private var isShuffle = false
    private var volumePercent = 50

    @Volatile
    var isPlaying = false
        private set

    fun setPlaylist(files: List<String>, shuffle: Boolean = false, loop: Boolean = true) {
        stop()
        playlist.clear()
        playlist.addAll(files)
        isShuffle = shuffle
        isLooping = loop
        if (isShuffle) Collections.shuffle(playlist)
        currentIndex = 0
    }

    fun setVolume(percent: Int) {
        volumePercent = max(0, min(100, percent))
        mediaPlayer?.setVolume(volumePercent / 100f, volumePercent / 100f)
    }

    fun play() {
        if (playlist.isEmpty()) {
            Log.w(TAG, "Playlist is empty, cannot play")
            return
        }
        playFile(playlist[currentIndex])
    }

    private fun playFile(path: String) {
        try {
            mediaPlayer?.release()
            val file = File(path)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $path")
                playNext()
                return
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    mp.setVolume(volumePercent / 100f, volumePercent / 100f)
                    mp.start()
                    isPlaying = true
                    Log.d(TAG, "Playing: $path")
                }
                setOnCompletionListener {
                    Log.d(TAG, "Completed: $path")
                    playNext()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    playNext()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: $path", e)
            playNext()
        }
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        if (isLooping) {
            currentIndex = (currentIndex + 1) % playlist.size
        } else if (currentIndex < playlist.size - 1) {
            currentIndex++
        } else {
            stop()
            return
        }
        playFile(playlist[currentIndex])
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Pause failed", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                isPlaying = true
            } else if (!isPlaying && playlist.isNotEmpty()) {
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resume failed", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Release failed", e)
        }
        mediaPlayer = null
        isPlaying = false
    }

    fun release() {
        stop()
        playlist.clear()
    }
}
