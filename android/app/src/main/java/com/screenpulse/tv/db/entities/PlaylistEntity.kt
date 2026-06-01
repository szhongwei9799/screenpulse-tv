package com.screenpulse.tv.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 播放列表实体
 *
 * 对应数据库中的 playlist 表，存储播放列表中的每一项
 * 包含媒体元信息和播放参数
 */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    /** 主键 ID，自增 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 媒体标题 */
    @ColumnInfo(name = "title")
    val title: String,

    /** 媒体类型：video, image, iptv, stream, webpage */
    @ColumnInfo(name = "type")
    val type: String,

    /** 媒体 URL 或本地文件路径 */
    @ColumnInfo(name = "url")
    val url: String,

    /** 显示时长（秒），null 表示视频自动时长 */
    @ColumnInfo(name = "duration")
    val duration: Long? = null,

    /** 是否启用 */
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    /** 音量 0-100 */
    @ColumnInfo(name = "volume")
    val volume: Int = 100,

    /** 播放顺序 */
    @ColumnInfo(name = "play_order")
    val order: Int = 0,

    /** 创建时间戳（毫秒） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 表名常量 */
        const val TABLE_NAME = "playlist"
    }
}
