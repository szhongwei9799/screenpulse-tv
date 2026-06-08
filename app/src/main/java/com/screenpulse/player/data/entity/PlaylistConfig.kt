package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Global playback configuration stored as a single-row table.
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
    val lastUpdated: Long = System.currentTimeMillis(),
    // Repeat mode settings
    val repeatMode: String = "LOOP",
    val repeatCount: Int = 1,
    // Image display duration in seconds
    val imageDuration: Int = 10,
    // Device settings
    val deviceName: String = "",
    val orientation: String = "landscape",
    val idleTimeout: Int = 0,
    // Background music settings
    val bgMusicEnabled: Boolean = false,
    val bgMusicVolume: Int = 50,
    val bgMusicLoop: Boolean = true,
    val bgMusicShuffle: Boolean = false,
    // Transition animation settings
    val transitionEnabled: Boolean = true,
    val transitionType: String = "fade",
    val transitionDuration: Int = 500
)

enum class PlaybackMode {
    LOOP,
    SEQUENTIAL,
    RANDOM
}
