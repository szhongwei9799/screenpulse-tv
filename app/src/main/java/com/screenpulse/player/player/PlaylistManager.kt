package com.screenpulse.player.player

import android.util.Log
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.PlaybackMode
import kotlin.random.Random

/**
 * Manages playlist iteration logic.
 *
 * Tracks the current playback position within the playlist and determines
 * which item plays next based on the configured [PlaybackMode].
 *
 * Also handles interstitial scheduling — when the current hour falls within
 * the interstitial time window, a special interstitial playlist can override
 * the normal playback.
 */
class PlaylistManager(initialItems: List<MediaItem>) {

    companion object {
        private const val TAG = "PlaylistManager"
        private const val INTERSTITIAL_PREFIX = "[INTERSTITIAL] "
    }

    // ── Playlist state ──────────────────────────────────────────────────
    private var items: MutableList<MediaItem> = initialItems.toMutableList()
    private var currentIndex: Int = 0
    private var playbackMode: PlaybackMode = PlaybackMode.LOOP

    // ── Interstitial state ───────────────────────────────────────────────
    private var interstitialEnabled: Boolean = false
    private var interstitialStartHour: Int = 12
    private var interstitialEndHour: Int = 13
    private var isPlayingInterstitial: Boolean = false

    // ── Shuffle state ────────────────────────────────────────────────────
    private var shuffledIndices: MutableList<Int> = mutableListOf()

    /**
     * Returns the current media item, or null if the playlist is empty.
     */
    fun currentItem(): MediaItem? {
        if (items.isEmpty()) return null

        // Check if we should be playing an interstitial
        if (interstitialEnabled && shouldPlayInterstitial()) {
            isPlayingInterstitial = true
            // Return the first item as interstitial placeholder
            // In a full implementation, this would swap in the interstitial playlist
            return items.firstOrNull()?.let {
                it.copy(title = INTERSTITIAL_PREFIX + it.title)
            }
        }

        isPlayingInterstitial = false
        return if (currentIndex in items.indices) {
            items[currentIndex]
        } else {
            null
        }
    }

    /**
     * Advances to the next item based on the current [playbackMode].
     * Returns true if there is a next item to play, false if the playlist
     * has been exhausted (only happens in SEQUENTIAL mode).
     */
    fun advanceToNext(): Boolean {
        if (items.isEmpty()) return false

        when (playbackMode) {
            PlaybackMode.LOOP -> {
                currentIndex = (currentIndex + 1) % items.size
                Log.d(TAG, "LOOP: advancing to index $currentIndex")
                return true
            }
            PlaybackMode.SEQUENTIAL -> {
                currentIndex++
                if (currentIndex >= items.size) {
                    Log.d(TAG, "SEQUENTIAL: playlist exhausted at end")
                    currentIndex = 0  // Reset for next play
                    return false
                }
                Log.d(TAG, "SEQUENTIAL: advancing to index $currentIndex")
                return true
            }
            PlaybackMode.RANDOM -> {
                if (shuffledIndices.isEmpty() || shuffledIndices.all { it == currentIndex }) {
                    rebuildShuffleIndices()
                }
                // Pick a random index different from current
                val nextIndex = if (items.size > 1) {
                    var candidate: Int
                    do {
                        candidate = Random.nextInt(items.size)
                    } while (candidate == currentIndex)
                    candidate
                } else {
                    0
                }
                currentIndex = nextIndex
                Log.d(TAG, "RANDOM: advancing to index $currentIndex")
                return true
            }
        }
    }

    /**
     * Moves to the previous item in the playlist.
     */
    fun previous(): Boolean {
        if (items.isEmpty()) return false
        currentIndex = if (currentIndex > 0) currentIndex - 1 else items.size - 1
        Log.d(TAG, "Previous: going to index $currentIndex")
        return true
    }

    /**
     * Jumps to a specific item by index.
     */
    fun jumpTo(index: Int): Boolean {
        if (index in items.indices) {
            currentIndex = index
            Log.d(TAG, "Jump to index $currentIndex")
            return true
        }
        return false
    }

    /**
     * Updates the playlist with new items. Returns true if the items changed
     * significantly enough to warrant restarting playback.
     */
    fun updateItems(newItems: List<MediaItem>): Boolean {
        if (newItems.isEmpty()) {
            items.clear()
            currentIndex = 0
            return true
        }

        val changed = items.size != newItems.size ||
                items.map { it.id to it.url } != newItems.map { it.id to it.url }

        items = newItems.toMutableList()

        // Clamp current index
        if (currentIndex >= items.size) {
            currentIndex = 0
        }

        if (playbackMode == PlaybackMode.RANDOM) {
            rebuildShuffleIndices()
        }

        Log.d(TAG, "Playlist updated: ${items.size} items, changed=$changed")
        return changed
    }

    /**
     * Sets the playback mode.
     */
    fun setPlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        if (mode == PlaybackMode.RANDOM) {
            rebuildShuffleIndices()
        }
        Log.d(TAG, "Playback mode set to: $mode")
    }

    /**
     * Configures interstitial scheduling.
     */
    fun setInterstitialConfig(
        enabled: Boolean,
        startHour: Int,
        endHour: Int
    ) {
        interstitialEnabled = enabled
        interstitialStartHour = startHour.coerceIn(0, 23)
        interstitialEndHour = endHour.coerceIn(0, 23)
        Log.d(TAG, "Interstitial config: enabled=$enabled, $interstitialStartHour:00-$interstitialEndHour:00")
    }

    /**
     * Checks if we are currently in the interstitial time window.
     */
    fun shouldPlayInterstitial(): Boolean {
        if (!interstitialEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        return if (interstitialStartHour <= interstitialEndHour) {
            currentHour in interstitialStartHour until interstitialEndHour
        } else {
            // Wraps around midnight (e.g., 22:00 to 06:00)
            currentHour >= interstitialStartHour || currentHour < interstitialEndHour
        }
    }

    /**
     * Returns whether the playlist is currently in interstitial mode.
     */
    fun isInInterstitial(): Boolean = isPlayingInterstitial

    /**
     * Returns true if the playlist has at least one item.
     */
    fun hasItems(): Boolean = items.isNotEmpty()

    /**
     * Returns the total number of items in the playlist.
     */
    fun itemCount(): Int = items.size

    /**
     * Returns the current index in the playlist.
     */
    fun getCurrentIndex(): Int = currentIndex

    /**
     * Stops playback tracking (resets to beginning).
     */
    fun stop() {
        currentIndex = 0
        isPlayingInterstitial = false
    }

    // =====================================================================
    //  Shuffle helpers
    // =====================================================================

    private fun rebuildShuffleIndices() {
        shuffledIndices = items.indices.shuffled().toMutableList()
        Log.d(TAG, "Shuffle indices rebuilt: $shuffledIndices")
    }
}
