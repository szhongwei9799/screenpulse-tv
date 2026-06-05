package com.screenpulse.player.data.dao

import androidx.room.*
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [MediaItem] operations.
 */
@Dao
interface MediaItemDao {

    @Query("SELECT * FROM media_items ORDER BY sortOrder ASC, id ASC")
    fun getAllItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllItemsOnce(): List<MediaItem>

    /** Only media library items (not playlist group references) */
    @Query("SELECT * FROM media_items WHERE sourceType = 'media' ORDER BY sortOrder ASC, id ASC")
    suspend fun getMediaLibraryItemsOnce(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getEnabledItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnabledItemsOnce(): List<MediaItem>

    /** Playlist entries: enabled group-type items */
    @Query("SELECT * FROM media_items WHERE sourceType = 'group' AND enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getPlaylistGroupItems(): Flow<List<MediaItem>>

    /** Playlist entries: all group-type items (for admin list) */
    @Query("SELECT * FROM media_items WHERE sourceType = 'group' ORDER BY sortOrder ASC, id ASC")
    suspend fun getPlaylistGroupItemsOnce(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE id IN (:ids) ORDER BY sortOrder ASC, id ASC")
    suspend fun getItemsByIds(ids: List<Long>): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getItemById(id: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE type = :type ORDER BY sortOrder ASC, id ASC")
    suspend fun getItemsByType(type: MediaType): List<MediaItem>

    @Query("SELECT COUNT(*) FROM media_items WHERE enabled = 1")
    suspend fun getEnabledCount(): Int

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getTotalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItem>): List<Long>

    @Update
    suspend fun update(item: MediaItem)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Query("UPDATE media_items SET enabled = :enabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET sortOrder = :sortOrder, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Batch-update sort orders for reordering the playlist.
     * [updates] is a map of item ID to new sortOrder value.
     */
    @Transaction
    suspend fun reorderItems(updates: Map<Long, Int>) {
        val timestamp = System.currentTimeMillis()
        for ((id, sortOrder) in updates) {
            updateSortOrder(id, sortOrder, timestamp)
        }
    }

    @Query("UPDATE media_items SET title = :title, url = :url, type = :type, durationSeconds = :durationSeconds, enabled = :enabled, sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFull(
        id: Long,
        title: String,
        url: String,
        type: MediaType,
        durationSeconds: Int,
        enabled: Boolean,
        sortOrder: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
}
