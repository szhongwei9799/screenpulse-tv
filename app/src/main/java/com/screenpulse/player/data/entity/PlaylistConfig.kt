package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Global playback configuration stored as a single-row table.
 * Holds settings that affect how the playlist is played back.
 */
@Entity(tableName = "playlist_config")
data class PlaylistConfig(
    @PrimaryKey
    val id: Int = 1,
    val playbackMode: PlaybackMode = PlaybackMode.LOOP,
    val interstitialEnabled: Boolean = false,
    val interstitialStartHour: Int = 12,
    val interstitialEndHour: Int = 13,
    val interstitialPlaylistName: String = "interstitial",
    val volumeLevel: Int = 80,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Determines how media items are sequenced during playback.
 */
enum class PlaybackMode {
    /** Repeat the playlist from the beginning after the last item. */
    LOOP,
    /** Play through once and stop. */
    SEQUENTIAL,
    /** Shuffle items each cycle. */
    RANDOM
}
