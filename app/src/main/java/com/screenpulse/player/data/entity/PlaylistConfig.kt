package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Global playback configuration stored as a single-row table.
 * Holds settings that affect how the playlist is played back.
 */
@Entity(tableName = "playlist_config")
data class PlaylistConfig(
    @PrimaryKey val id: Int = 1,
    // Playback mode - TWO independent dimensions
    val orderMode: String = "SEQUENTIAL",     // SEQUENTIAL or RANDOM
    val repeatMode: String = "LOOP",          // ONCE, LOOP, N_TIMES
    val repeatCount: Int = 0,                 // only used when repeatMode=N_TIMES
    // Volume
    val volumeLevel: Int = 80,
    // Image display duration
    val imageDuration: Int = 10,
    // Device settings
    val deviceName: String = "ScreenPulse Player",
    val orientation: String = "landscape",
    val idleTimeout: Int = 0,
    // Interstitial (legacy, kept for backward compat)
    val interstitialEnabled: Boolean = false,
    val interstitialStartHour: Int = 12,
    val interstitialEndHour: Int = 13,
    val interstitialPlaylistName: String = "interstitial",
    val offScreenMessage: String = "",
    // Background music
    val bgMusicEnabled: Boolean = false,
    val bgMusicVolume: Int = 50,
    val bgMusicLoop: Boolean = true,
    val bgMusicShuffle: Boolean = false,
    // Metadata
    val lastUpdated: Long = System.currentTimeMillis()
)
