package com.screenpulse.player.data.dao

import androidx.room.*
import com.screenpulse.player.data.entity.PlaylistConfig
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [PlaylistConfig] operations.
 * The table holds a single row (id=1) that stores global playback settings.
 */
@Dao
interface PlaylistConfigDao {

    @Query("SELECT * FROM playlist_config WHERE id = 1")
    fun getConfig(): Flow<PlaylistConfig?>

    @Query("SELECT * FROM playlist_config WHERE id = 1")
    suspend fun getConfigOnce(): PlaylistConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: PlaylistConfig)

    @Update
    suspend fun update(config: PlaylistConfig)

    @Query("UPDATE playlist_config SET playbackMode = :mode, lastUpdated = :timestamp WHERE id = 1")
    suspend fun setPlaybackMode(mode: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_config SET volumeLevel = :volume, lastUpdated = :timestamp WHERE id = 1")
    suspend fun setVolumeLevel(volume: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_config SET interstitialEnabled = :enabled, lastUpdated = :timestamp WHERE id = 1")
    suspend fun setInterstitialEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())
}
