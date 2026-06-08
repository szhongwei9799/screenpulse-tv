package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    val title: String,
    val url: String,
    val type: MediaType = MediaType.VIDEO,
    val source: MediaSource = MediaSource.LOCAL,
    /** Duration in seconds for images. 0 = auto-detect for video, >0 = custom hold time. */
    val durationSeconds: Int = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** "media" = media library file, "group" = playlist group reference */
    val sourceType: String = "media",
    /** When sourceType is "group", this holds the referenced group ID */
    val groupId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class MediaType {
    VIDEO,
    IMAGE,
    PPT,
    IPTV,
    STREAM
}

enum class MediaSource {
    LOCAL,
    ONLINE
}
