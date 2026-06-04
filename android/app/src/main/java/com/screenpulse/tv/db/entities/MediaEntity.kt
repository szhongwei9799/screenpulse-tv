package com.screenpulse.tv.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 媒体库实体
 *
 * 对应数据库中的 media 表，存储所有可用的媒体资源
 * 包括上传的本地文件和添加的网络 URL
 */
@Entity(tableName = "media")
data class MediaEntity(
    /** 主键 ID，自增 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 媒体标题 */
    @ColumnInfo(name = "title")
    val title: String,

    /** 媒体类型：video, image, iptv, stream, webpage */
    @ColumnInfo(name = "type")
    val type: String,

    /** 媒体 URL 或相对路径 */
    @ColumnInfo(name = "url")
    val url: String,

    /** 本地文件绝对路径（仅本地文件有值） */
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,

    /** 文件大小（字节） */
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,

    /** MIME 类型 */
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    /** 缩略图 URL */
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    /** 缩略图本地路径 */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    /** 宽度（像素） */
    @ColumnInfo(name = "width")
    val width: Int? = null,

    /** 高度（像素） */
    @ColumnInfo(name = "height")
    val height: Int? = null,

    /** 时长（秒，仅视频有效） */
    @ColumnInfo(name = "duration")
    val duration: Long? = null,

    /** 创建时间戳（毫秒） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 表名常量 */
        const val TABLE_NAME = "media"
    }
}
