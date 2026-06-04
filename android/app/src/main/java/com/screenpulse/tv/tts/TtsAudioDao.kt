package com.screenpulse.tv.tts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for TTS audio entities.
 * Provides CRUD operations for TTS-generated audio files stored in the database.
 */
@Dao
interface TtsAudioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tts: TtsAudioEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TtsAudioEntity>): List<Long>

    @Update
    suspend fun update(tts: TtsAudioEntity)

    @Query("UPDATE tts_audio SET volume = :volume WHERE id = :id")
    suspend fun updateVolume(id: Long, volume: Int)

    @Query("UPDATE tts_audio SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM tts_audio WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tts_audio")
    suspend fun deleteAll()

    @Query("SELECT * FROM tts_audio ORDER BY created_at DESC")
    suspend fun getAll(): List<TtsAudioEntity>

    @Query("SELECT * FROM tts_audio WHERE enabled = 1 ORDER BY created_at DESC")
    suspend fun getEnabled(): List<TtsAudioEntity>

    @Query("SELECT * FROM tts_audio WHERE id = :id")
    suspend fun getById(id: Long): TtsAudioEntity?

    @Query("SELECT COUNT(*) FROM tts_audio")
    suspend fun getCount(): Int
}
