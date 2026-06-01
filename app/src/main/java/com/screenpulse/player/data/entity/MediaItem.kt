package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single media item in the playlist.
 * Supports video, image, PPT, IPTV (m3u8), and generic stream URLs.
 */
@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val type: MediaType = MediaType.VIDEO,
    /** Duration in seconds for images. 0 = auto-detect for video, >0 = custom hold time. */
    val durationSeconds: Int = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Supported media types for the digital signage player.
 */
enum class MediaType {
    VIDEO,
    IMAGE,
    PPT,
    IPTV,
    STREAM
}
