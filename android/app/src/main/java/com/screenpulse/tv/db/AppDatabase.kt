package com.screenpulse.tv.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.screenpulse.tv.db.entities.MediaEntity
import com.screenpulse.tv.db.entities.PlaylistEntity
import com.screenpulse.tv.db.entities.ScheduleEntity
import com.screenpulse.tv.tts.TtsAudioDao
import com.screenpulse.tv.tts.TtsAudioEntity

/**
 * ScreenPulse TV Room 数据库
 *
 * 版本 2，包含以下表：
 * - playlist: 播放列表
 * - media: 媒体库
 * - schedule: 定时任务
 * - tts_audio: TTS生成的音频文件
 */
@Database(
    entities = [
        PlaylistEntity::class,
        MediaEntity::class,
        ScheduleEntity::class,
        TtsAudioEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** 播放列表 DAO */
    abstract fun playlistDao(): PlaylistDao

    /** 媒体库 DAO */
    abstract fun mediaDao(): MediaDao

    /** 定时任务 DAO */
    abstract fun scheduleDao(): ScheduleDao

    /** TTS音频 DAO */
    abstract fun ttsAudioDao(): TtsAudioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "screenpulse_tv.db"

        /**
         * 获取数据库单例
         * 使用双重检查锁定保证线程安全
         */
        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
