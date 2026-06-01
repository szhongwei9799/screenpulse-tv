package com.screenpulse.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.screenpulse.player.data.dao.MediaItemDao
import com.screenpulse.player.data.dao.PlaylistConfigDao
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.PlaylistConfig

/**
 * Room database for the ScreenPulse digital signage player.
 * Holds the media playlist and global playback configuration.
 */
@Database(
    entities = [
        MediaItem::class,
        PlaylistConfig::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaItemDao(): MediaItemDao
    abstract fun playlistConfigDao(): PlaylistConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "screenpulse.db"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
