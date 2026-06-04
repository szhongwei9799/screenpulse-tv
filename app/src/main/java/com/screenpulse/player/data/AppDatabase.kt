package com.screenpulse.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.screenpulse.player.data.dao.BackgroundMusicDao
import com.screenpulse.player.data.dao.MediaItemDao
import com.screenpulse.player.data.dao.PlaylistConfigDao
import com.screenpulse.player.data.dao.TtsAudioDao
import com.screenpulse.player.data.entity.BackgroundMusic
import com.screenpulse.player.data.entity.MediaItem
import com.screenpulse.player.data.entity.PlaylistConfig
import com.screenpulse.player.data.entity.TtsAudioEntity

@Database(
    entities = [
        MediaItem::class,
        PlaylistConfig::class,
        TtsAudioEntity::class,
        BackgroundMusic::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaItemDao(): MediaItemDao
    abstract fun playlistConfigDao(): PlaylistConfigDao
    abstract fun ttsAudioDao(): TtsAudioDao
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
