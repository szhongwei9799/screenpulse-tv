package com.screenpulse.tv.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenpulse.tv.db.entities.MediaEntity

/**
 * 媒体库数据访问对象 (DAO)
 *
 * 提供媒体资源的数据库操作接口
 */
@Dao
interface MediaDao {

    /**
     * 插入媒体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    /**
     * 批量插入媒体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaList: List<MediaEntity>): List<Long>

    /**
     * 更新媒体
     */
    @Query("UPDATE media SET title = :title, type = :type, url = :url WHERE id = :id")
    suspend fun update(id: Long, title: String, type: String, url: String)

    /**
     * 根据 ID 删除媒体
     */
    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有媒体
     */
    @Query("DELETE FROM media")
    suspend fun deleteAll()

    /**
     * 获取所有媒体
     */
    @Query("SELECT * FROM media ORDER BY created_at DESC")
    suspend fun getAll(): List<MediaEntity>

    /**
     * 根据 ID 获取媒体
     */
    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    /**
     * 根据类型获取媒体
     */
    @Query("SELECT * FROM media WHERE type = :type ORDER BY created_at DESC")
    suspend fun getByType(type: String): List<MediaEntity>

    /**
     * 获取本地文件媒体
     */
    @Query("SELECT * FROM media WHERE file_path IS NOT NULL ORDER BY created_at DESC")
    suspend fun getLocalMedia(): List<MediaEntity>

    /**
     * 获取媒体总数
     */
    @Query("SELECT COUNT(*) FROM media")
    suspend fun getCount(): Int
}
