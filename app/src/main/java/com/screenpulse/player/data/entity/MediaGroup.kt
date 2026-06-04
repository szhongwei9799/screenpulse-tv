package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_groups")
data class MediaGroup(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val name: String,
    val description: String = "",
    val color: String = "#409EFF",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
