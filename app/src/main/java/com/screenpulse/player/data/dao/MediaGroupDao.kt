package com.screenpulse.player.data.dao

import androidx.room.*
import com.screenpulse.player.data.entity.MediaGroup
import com.screenpulse.player.data.entity.MediaGroupItem
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [MediaGroup] and [MediaGroupItem] operations.
 */
@Dao
interface MediaGroupDao {

    // ===== Group CRUD =====

    @Query("SELECT * FROM media_groups ORDER BY sort_order ASC, id ASC")
    fun getAllGroups(): Flow<List<MediaGroup>>

    @Query("SELECT * FROM media_groups ORDER BY sort_order ASC, id ASC")
    suspend fun getAllGroupsOnce(): List<MediaGroup>

    @Query("SELECT * FROM media_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): MediaGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: MediaGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<MediaGroup>): List<Long>

    @Update
    suspend fun update(group: MediaGroup)

    @Query("DELETE FROM media_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("DELETE FROM media_groups")
    suspend fun deleteAllGroups()

    @Query("SELECT COUNT(*) FROM media_groups")
    suspend fun getGroupCount(): Int

    // ===== Group-Item (many-to-many) =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToGroup(item: MediaGroupItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToGroupAll(items: List<MediaGroupItem>)

    @Query("DELETE FROM media_group_items WHERE group_id = :groupId AND media_id = :mediaId")
    suspend fun removeMediaFromGroup(groupId: Long, mediaId: Long)

    @Query("DELETE FROM media_group_items WHERE group_id = :groupId")
    suspend fun removeAllItemsFromGroup(groupId: Long)

    @Query("DELETE FROM media_group_items WHERE media_id = :mediaId")
    suspend fun removeMediaFromAllGroups(mediaId: Long)

    @Query("SELECT * FROM media_group_items WHERE group_id = :groupId ORDER BY sort_order ASC, id ASC")
    suspend fun getItemsInGroup(groupId: Long): List<MediaGroupItem>

    @Query("SELECT * FROM media_group_items WHERE media_id = :mediaId")
    suspend fun getGroupsForMedia(mediaId: Long): List<MediaGroupItem>

    @Query("SELECT g.* FROM media_groups g INNER JOIN media_group_items mgi ON g.id = mgi.group_id WHERE mgi.media_id = :mediaId ORDER BY g.sort_order ASC")
    suspend fun getGroupsContainingMedia(mediaId: Long): List<MediaGroup>

    @Query("SELECT COUNT(*) FROM media_group_items WHERE group_id = :groupId")
    suspend fun getItemCountInGroup(groupId: Long): Int

    @Query("SELECT media_id FROM media_group_items WHERE group_id = :groupId ORDER BY sort_order ASC")
    suspend fun getMediaIdsInGroup(groupId: Long): List<Long>
}
