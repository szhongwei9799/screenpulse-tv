package com.screenpulse.player.player

import android.util.Log
import com.screenpulse.player.data.entity.MediaItem
import kotlin.random.Random

/**
 * Manages playlist iteration logic.
 *
 * Two independent dimensions control playback:
 * - [orderMode]: SEQUENTIAL or RANDOM (order of items)
 * - [repeatMode]: ONCE, LOOP, or N_TIMES (repeat behavior, with [repeatCount])
 *
 * Also handles interstitial scheduling and playback statistics.
 */
class PlaylistManager(initialItems: List<MediaItem>) {

    companion object {
        private const val TAG = "PlaylistManager"
        private const val INTERSTITIAL_PREFIX = "[INTERSTITIAL] "
    }

    // ── Playlist state ──────────────────────────────────────────────────
    private var items: MutableList<MediaItem> = initialItems.toMutableList()
    private var currentIndex: Int = 0

    // ── Playback mode (two independent dimensions) ──────────────────────
    private var orderMode: String = "SEQUENTIAL"  // SEQUENTIAL or RANDOM
    private var repeatMode: String = "LOOP"        // ONCE, LOOP, N_TIMES
    private var repeatCount: Int = 0               // Only used when repeatMode == N_TIMES
    private var completedCycles: Int = 0           // For N_TIMES tracking

    // ── Interstitial state ───────────────────────────────────────────────
    private var interstitialEnabled: Boolean = false
    private var interstitialStartHour: Int = 12
    private var interstitialEndHour: Int = 13
    private var isPlayingInterstitial: Boolean = false

    // ── Shuffle state ────────────────────────────────────────────────────
    private var shuffledIndices: MutableList<Int> = mutableListOf()

    // ── Playback stats ──────────────────────────────────────────────────
    private val playCounts: MutableMap<Long, Int> = mutableMapOf()
    private var loopCount: Int = 0
    private var totalPlayCount: Int = 0
    private var statsDay: Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    private var currentPlayingTitle: String = ""
    private var currentPlayingType: String = ""

    /**
     * Returns the current media item, or null if the playlist is empty.
     */
    fun currentItem(): MediaItem? {
        if (items.isEmpty()) return null

        if (interstitialEnabled && shouldPlayInterstitial()) {
            isPlayingInterstitial = true
            return items.firstOrNull()?.let {
                it.copy(title = INTERSTITIAL_PREFIX + it.title)
            }
        }

        isPlayingInterstitial = false
        return if (currentIndex in items.indices) items[currentIndex] else null
    }

    /**
     * Advances to the next item based on [orderMode] and [repeatMode].
     * Returns true if there is a next item, false if playback should stop.
     */
    fun advanceToNext(): Boolean {
        if (items.isEmpty()) return false

        checkDayReset()

        // Record play count
        if (currentIndex in items.indices) {
            val itemId = items[currentIndex].id
            playCounts[itemId] = (playCounts[itemId] ?: 0) + 1
            totalPlayCount++
        }

        // Determine next index based on orderMode
        val nextIndex = when (orderMode) {
            "RANDOM" -> {
                var candidate: Int
                do {
                    candidate = Random.nextInt(items.size)
                } while (items.size > 1 && candidate == currentIndex)
                candidate
            }
            else -> {
                // SEQUENTIAL
                if (currentIndex >= items.size - 1) {
                    // Reached end of playlist
                    when (repeatMode) {
                        "ONCE" -> {
                            Log.d(TAG, "SEQUENTIAL+ONCE: playlist exhausted")
                            loopCount++
                            currentIndex = 0
                            return false
                        }
                        "N_TIMES" -> {
                            completedCycles++
                            if (completedCycles >= repeatCount && repeatCount > 0) {
                                Log.d(TAG, "SEQUENTIAL+N_TIMES: completed $completedCycles/$repeatCount cycles, stopping")
                                loopCount++
                                currentIndex = 0
                                return false
                            }
                            Log.d(TAG, "SEQUENTIAL+N_TIMES: cycle $completedCycles/$repeatCount")
                        }
                        else -> {
                            // LOOP
                            loopCount++
                            Log.d(TAG, "SEQUENTIAL+LOOP: completed cycle #$loopCount")
                        }
                    }
                    0 // Wrap to beginning
                } else {
                    currentIndex + 1
                }
            }
        }

        currentIndex = nextIndex
        Log.d(TAG, "advanceToNext: order=$orderMode repeat=$repeatMode index=$currentIndex")
        return true
    }

    fun previous(): Boolean {
        if (items.isEmpty()) return false
        currentIndex = if (currentIndex > 0) currentIndex - 1 else items.size - 1
        Log.d(TAG, "Previous: index $currentIndex")
        return true
    }

    fun jumpTo(index: Int): Boolean {
        if (index in items.indices) {
            currentIndex = index
            Log.d(TAG, "Jump to index $currentIndex")
            return true
        }
        return false
    }

    fun updateItems(newItems: List<MediaItem>): Boolean {
        if (newItems.isEmpty()) {
            items.clear()
            currentIndex = 0
            return true
        }

        val changed = items.size != newItems.size ||
                items.map { it.id to it.url } != newItems.map { it.id to it.url }

        items = newItems.toMutableList()

        if (currentIndex >= items.size) currentIndex = 0
        if (orderMode == "RANDOM") rebuildShuffleIndices()

        Log.d(TAG, "Playlist updated: ${items.size} items, changed=$changed")
        return changed
    }

    /**
     * Sets the order mode (SEQUENTIAL or RANDOM).
     */
    fun setOrderMode(mode: String) {
        orderMode = mode
        if (mode == "RANDOM") rebuildShuffleIndices()
        Log.d(TAG, "Order mode: $mode")
    }

    /**
     * Sets the repeat mode (ONCE, LOOP, N_TIMES).
     */
    fun setRepeatMode(mode: String, count: Int = 0) {
        repeatMode = mode
        repeatCount = count
        completedCycles = 0
        Log.d(TAG, "Repeat mode: $mode count=$count")
    }

    /**
     * Combined setter for backward compatibility.
     */
    fun setPlaybackMode(mode: String) {
        // Map old playbackMode to new dimensions
        when (mode) {
            "RANDOM" -> { setOrderMode("RANDOM"); setRepeatMode("LOOP") }
            "SEQUENTIAL" -> { setOrderMode("SEQUENTIAL"); setRepeatMode("ONCE") }
            else -> { setOrderMode("SEQUENTIAL"); setRepeatMode("LOOP") }
        }
    }

    fun setInterstitialConfig(enabled: Boolean, startHour: Int, endHour: Int) {
        interstitialEnabled = enabled
        interstitialStartHour = startHour.coerceIn(0, 23)
        interstitialEndHour = endHour.coerceIn(0, 23)
        Log.d(TAG, "Interstitial: enabled=$enabled, $interstitialStartHour:00-$interstitialEndHour:00")
    }

    fun shouldPlayInterstitial(): Boolean {
        if (!interstitialEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (interstitialStartHour <= interstitialEndHour) {
            currentHour in interstitialStartHour until interstitialEndHour
        } else {
            currentHour >= interstitialStartHour || currentHour < interstitialEndHour
        }
    }

    fun isInInterstitial(): Boolean = isPlayingInterstitial
    fun hasItems(): Boolean = items.isNotEmpty()
    fun itemCount(): Int = items.size
    fun getCurrentIndex(): Int = currentIndex

    fun getOrderMode(): String = orderMode
    fun getRepeatMode(): String = repeatMode

    fun stop() {
        currentIndex = 0
        completedCycles = 0
        isPlayingInterstitial = false
        currentPlayingTitle = ""
        currentPlayingType = ""
    }

    // =====================================================================
    //  Playback stats
    // =====================================================================

    fun getPlaybackStats(): PlaybackStats {
        checkDayReset()
        val current = currentItem()
        if (current != null) {
            currentPlayingTitle = current.title
            currentPlayingType = current.type.name
        }

        val itemStats = items.map { item ->
            mapOf(
                "id" to item.id,
                "title" to item.title,
                "type" to item.type.name,
                "playCount" to (playCounts[item.id] ?: 0)
            )
        }

        return PlaybackStats(
            currentPlayingTitle = currentPlayingTitle,
            currentPlayingType = currentPlayingType,
            currentIndex = currentIndex,
            totalPlayCount = totalPlayCount,
            loopCount = loopCount,
            itemStats = itemStats,
            orderMode = orderMode,
            repeatMode = repeatMode,
            totalItems = items.size
        )
    }

    private fun checkDayReset() {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (today != statsDay) {
            Log.d(TAG, "Day changed ($statsDay -> $today), resetting stats")
            playCounts.clear()
            loopCount = 0
            totalPlayCount = 0
            completedCycles = 0
            statsDay = today
        }
    }

    private fun rebuildShuffleIndices() {
        shuffledIndices = items.indices.shuffled().toMutableList()
        Log.d(TAG, "Shuffle indices rebuilt: $shuffledIndices")
    }
}

data class PlaybackStats(
    val currentPlayingTitle: String = "",
    val currentPlayingType: String = "",
    val currentIndex: Int = 0,
    val totalPlayCount: Int = 0,
    val loopCount: Int = 0,
    val itemStats: List<Map<String, Any>> = emptyList(),
    val orderMode: String = "SEQUENTIAL",
    val repeatMode: String = "LOOP",
    val totalItems: Int = 0
)
