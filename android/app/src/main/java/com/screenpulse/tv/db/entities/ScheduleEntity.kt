package com.screenpulse.tv.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时任务实体
 *
 * 对应数据库中的 schedule 表，存储定时播放任务的配置
 * 支持简单的 cron-like 表达式和重复调度
 */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    /** 主键 ID，自增 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 任务名称 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 定时表达式（支持 HH:mm, cron-like） */
    @ColumnInfo(name = "cron")
    val cron: String,

    /** 定时内容的 JSON 序列化（List<PlaylistEntity>） */
    @ColumnInfo(name = "content_json")
    val contentJson: String? = null,

    /** 是否重复执行 */
    @ColumnInfo(name = "repeat")
    val repeat: Boolean = true,

    /** 是否已完成（一次性任务） */
    @ColumnInfo(name = "completed")
    val completed: Boolean = false,

    /** 是否启用 */
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    /** 优先级（数字越大优先级越高） */
    @ColumnInfo(name = "priority")
    val priority: Int = 0,

    /** 最后执行时间（毫秒时间戳） */
    @ColumnInfo(name = "last_executed_at")
    val lastExecutedAt: Long? = null,

    /** 下次执行时间（毫秒时间戳） */
    @ColumnInfo(name = "next_trigger_at")
    val nextTriggerAt: Long? = null,

    /** 创建时间戳（毫秒） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 更新时间戳（毫秒） */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 表名常量 */
        const val TABLE_NAME = "schedule"
    }
}
