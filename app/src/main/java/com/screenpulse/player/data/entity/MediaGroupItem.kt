package com.screenpulse.player.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 媒体分组关联实体
 *
 * 多对多关系：一个媒体可以属于多个分组，一个分组包含多个媒体。
 */
@Entity(
    tableName = "media_group_items",
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["media_id"]),
        Index(value = ["group_id", "media_id"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = MediaGroup::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaItem::class, parentColumns = ["id"], childColumns = ["media_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class MediaGroupItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "media_id")
    val mediaId: Long,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
