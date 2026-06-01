package com.screenpulse.tv.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenpulse.tv.db.entities.PlaylistEntity

/**
 * 播放列表数据访问对象 (DAO)
 *
 * 提供播放列表的数据库操作接口：
 * - CRUD 操作
 * - 查询活跃项（启用状态）
 * - 批量操作
 * - 排序和状态更新
 */
@Dao
interface PlaylistDao {

    // ==================== 插入操作 ====================

    /**
     * 插入单个播放项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistEntity): Long

    /**
     * 批量插入播放项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistEntity>): List<Long>

    // ==================== 更新操作 ====================

    /**
     * 更新播放项
     */
    @Update
    suspend fun update(item: PlaylistEntity)

    /**
     * 更新播放顺序
     */
    @Query("UPDATE playlist SET play_order = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    /**
     * 更新启用状态
     */
    @Query("UPDATE playlist SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    /**
     * 更新音量
     */
    @Query("UPDATE playlist SET volume = :volume WHERE id = :id")
    suspend fun updateVolume(id: Long, volume: Int)

    // ==================== 删除操作 ====================

    /**
     * 删除播放项
     */
    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有播放项
     */
    @Query("DELETE FROM playlist")
    suspend fun deleteAll()

    // ==================== 查询操作 ====================

    /**
     * 获取所有播放项（按顺序排列）
     */
    @Query("SELECT * FROM playlist ORDER BY play_order ASC")
    suspend fun getAll(): List<PlaylistEntity>

    /**
     * 获取所有播放项（LiveData，实时更新）
     */
    @Query("SELECT * FROM playlist ORDER BY play_order ASC")
    fun getAllFlow(): LiveData<List<PlaylistEntity>>

    /**
     * 获取活跃的播放项（启用状态，按顺序排列）
     */
    @Query("SELECT * FROM playlist WHERE enabled = 1 ORDER BY play_order ASC")
    suspend fun getActivePlaylistItems(): List<PlaylistEntity>

    /**
     * 获取活跃播放项的 Flow（启用状态，按顺序排列）
     */
    @Query("SELECT * FROM playlist WHERE enabled = 1 ORDER BY play_order ASC")
    fun getActivePlaylistFlow(): LiveData<List<PlaylistEntity>>

    /**
     * 获取活跃播放项数量
     */
    @Query("SELECT COUNT(*) FROM playlist WHERE enabled = 1")
    suspend fun getActivePlaylistCount(): Int

    /**
     * 获取总播放项数量
     */
    @Query("SELECT COUNT(*) FROM playlist")
    suspend fun getTotalCount(): Int

    /**
     * 根据 ID 获取播放项
     */
    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    /**
     * 根据类型获取播放项
     */
    @Query("SELECT * FROM playlist WHERE type = :type ORDER BY play_order ASC")
    suspend fun getByType(type: String): List<PlaylistEntity>
}
