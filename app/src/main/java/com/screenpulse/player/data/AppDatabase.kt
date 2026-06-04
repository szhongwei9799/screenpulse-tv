package com.screenpulse.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.screenpulse.player.data.dao.MediaGroupDao
import com.screenpulse.player.data.dao.MediaItemDao
import com.screenpulse.player.data.dao.PlaylistConfigDao
import com.screenpulse.player.data.dao.BackgroundMusicDao
import com.screenpulse.player.data.dao.ScheduledTaskDao
import com.screenpulse.player.data.entity.MediaGroup
import com.screenpulse.player.data.entity.MediaGroupItem
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.PlaylistConfig
import com.screenpulse.player.data.entity.BackgroundMusic
import com.screenpulse.player.data.entity.ScheduledTask

/**
 * Room database for the ScreenPulse digital signage player.
 * Holds the media playlist, playback configuration, media groups, and scheduled tasks.
 */
@Database(
    entities = [
        MediaItem::class,
        PlaylistConfig::class,
        MediaGroup::class,
        MediaGroupItem::class,
        ScheduledTask::class,
        BackgroundMusic::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun playlistConfigDao(): PlaylistConfigDao
    abstract fun mediaGroupDao(): MediaGroupDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun backgroundMusicDao(): BackgroundMusicDao

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
