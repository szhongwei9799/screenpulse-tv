package com.screenpulse.player.data.dao

import androidx.room.*
import com.screenpulse.player.data.entity.BackgroundMusic

@Dao
interface BackgroundMusicDao {
    @Query("SELECT * FROM background_music ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllMusic(): List<BackgroundMusic>

    @Query("SELECT * FROM background_music WHERE id = :id")
    suspend fun getMusicById(id: Long): BackgroundMusic?

    @Query("SELECT * FROM background_music ORDER BY sortOrder ASC, id ASC LIMIT 1")
    suspend fun getFirstMusic(): BackgroundMusic?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(music: BackgroundMusic): Long

    @Update
    suspend fun update(music: BackgroundMusic)

    @Delete
    suspend fun delete(music: BackgroundMusic)

    @Query("DELETE FROM background_music WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM background_music")
    suspend fun deleteAll()
}
