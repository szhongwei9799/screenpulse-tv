package com.screenpulse.player.data.entity

import androidx.room.Entity

@Entity(tableName = "media_group_items", primaryKeys = ["groupId", "mediaItemId"])
data class MediaGroupItem(
    val groupId: Long,
    val mediaItemId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
