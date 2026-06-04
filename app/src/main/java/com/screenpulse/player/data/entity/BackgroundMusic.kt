package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "background_music")
data class BackgroundMusic(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val title: String,
    val filePath: String,
    val fileSize: Long = 0,
    val durationSec: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
