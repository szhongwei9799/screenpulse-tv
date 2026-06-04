package com.screenpulse.player.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenpulse.player.data.entity.BackgroundMusic
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundMusicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(music: BackgroundMusic): Long

    @Update
    suspend fun update(music: BackgroundMusic)

    @Query("DELETE FROM background_music WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM background_music")
    suspend fun deleteAll()

    @Query("SELECT * FROM background_music ORDER BY sort_order ASC, created_at DESC")
    suspend fun getAll(): List<BackgroundMusic>

    @Query("SELECT * FROM background_music ORDER BY sort_order ASC, created_at DESC")
    fun getAllFlow(): Flow<List<BackgroundMusic>>

    @Query("SELECT * FROM background_music WHERE id = :id")
    suspend fun getById(id: Long): BackgroundMusic?

    @Query("SELECT COUNT(*) FROM background_music")
    suspend fun getCount(): Int

    @Query("UPDATE background_music SET sort_order = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
}
