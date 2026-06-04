package com.screenpulse.player.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 媒体分组实体
 *
 * 一个媒体分组包含多个媒体项，播放列表通过对分组进行播放。
 * 播放列表可以包含多个分组，切换分组时有转场效果。
 */
@Entity(tableName = "media_groups")
data class MediaGroup(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: String = "#409EFF",

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
