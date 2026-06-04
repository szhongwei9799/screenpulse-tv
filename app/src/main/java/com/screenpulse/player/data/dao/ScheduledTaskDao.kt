package com.screenpulse.player.data.dao

import androidx.room.*
import com.screenpulse.player.data.entity.ScheduledTask
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY priority DESC, id ASC")
    suspend fun getAllTasks(): List<ScheduledTask>

    @Query("SELECT * FROM scheduled_tasks ORDER BY priority DESC, id ASC")
    fun getAllTasksFlow(): Flow<List<ScheduledTask>>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getEnabledTasks(): List<ScheduledTask>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): ScheduledTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTask): Long

    @Update
    suspend fun update(task: ScheduledTask)

    @Delete
    suspend fun delete(task: ScheduledTask)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, timestamp: Long = System.currentTimeMillis())
}
