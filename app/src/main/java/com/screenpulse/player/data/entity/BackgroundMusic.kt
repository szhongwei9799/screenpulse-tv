package com.screenpulse.player.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing background music files uploaded by the user.
 */
@Entity(tableName = "background_music")
data class BackgroundMusic(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Long = 0,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
