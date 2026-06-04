package com.screenpulse.player.data.dao

import androidx.room.*
import com.screenpulse.player.data.entity.MediaGroup
import com.screenpulse.player.data.entity.MediaGroupItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaGroupDao {
    @Query("SELECT * FROM media_groups ORDER BY name ASC")
    suspend fun getAllGroups(): List<MediaGroup>

    @Query("SELECT * FROM media_groups ORDER BY name ASC")
    fun getAllGroupsFlow(): Flow<List<MediaGroup>>

    @Query("SELECT * FROM media_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): MediaGroup?

    @Query("SELECT * FROM media_groups WHERE name = :name LIMIT 1")
    suspend fun getGroupByName(name: String): MediaGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: MediaGroup): Long

    @Update
    suspend fun update(group: MediaGroup)

    @Delete
    suspend fun delete(group: MediaGroup)

    @Query("DELETE FROM media_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Junction table operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGroupItem(item: MediaGroupItem)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGroupItems(items: List<MediaGroupItem>)

    @Query("DELETE FROM media_group_items WHERE groupId = :groupId AND mediaItemId = :mediaItemId")
    suspend fun removeGroupItem(groupId: Long, mediaItemId: Long)

    @Query("DELETE FROM media_group_items WHERE groupId = :groupId")
    suspend fun clearGroupItems(groupId: Long)

    @Query("SELECT mediaItemId FROM media_group_items WHERE groupId = :groupId")
    suspend fun getGroupMediaIds(groupId: Long): List<Long>

    @Query("SELECT groupId FROM media_group_items WHERE mediaItemId = :mediaItemId")
    suspend fun getMediaGroupIds(mediaItemId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM media_group_items WHERE groupId = :groupId")
    suspend fun getGroupItemCount(groupId: Long): Int
}
