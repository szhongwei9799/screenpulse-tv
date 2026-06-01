package com.screenpulse.tv.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenpulse.tv.db.entities.ScheduleEntity

/**
 * 定时任务数据访问对象 (DAO)
 *
 * 提供定时任务的数据库操作接口
 */
@Dao
interface ScheduleDao {

    /**
     * 插入定时任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    /**
     * 批量插入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>): List<Long>

    /**
     * 更新定时任务
     */
    @Update
    suspend fun update(schedule: ScheduleEntity)

    /**
     * 标记为已完成
     */
    @Query("UPDATE schedule SET completed = :completed WHERE id = :id")
    suspend fun updateCompleted(id: Long, completed: Boolean)

    /**
     * 更新最后执行时间
     */
    @Query("UPDATE schedule SET last_executed_at = :timestamp, next_trigger_at = :nextTrigger WHERE id = :id")
    suspend fun updateLastExecuted(id: Long, timestamp: Long, nextTrigger: Long?)

    /**
     * 根据 ID 删除
     */
    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有定时任务
     */
    @Query("DELETE FROM schedule")
    suspend fun deleteAll()

    /**
     * 获取所有定时任务
     */
    @Query("SELECT * FROM schedule ORDER BY priority DESC, created_at ASC")
    suspend fun getAll(): List<ScheduleEntity>

    /**
     * 获取已启用的定时任务
     */
    @Query("SELECT * FROM schedule WHERE enabled = 1 AND completed = 0 ORDER BY priority DESC, created_at ASC")
    suspend fun getActive(): List<ScheduleEntity>

    /**
     * 根据 ID 获取定时任务
     */
    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEntity?

    /**
     * 获取定时任务总数
     */
    @Query("SELECT COUNT(*) FROM schedule")
    suspend fun getCount(): Int
}
