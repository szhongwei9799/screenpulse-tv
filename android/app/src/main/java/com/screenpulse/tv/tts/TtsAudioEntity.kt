package com.screenpulse.tv.tts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing TTS-generated audio files.
 * These can be used as background music or voiceover tracks in the digital signage playlist.
 */
@Entity(tableName = "tts_audio")
data class TtsAudioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "text")
    val text: String,           // Original text used for TTS

    @ColumnInfo(name = "voice")
    val voice: String,           // Edge TTS voice name e.g. "zh-CN-XiaoxiaoNeural"

    @ColumnInfo(name = "file_path")
    val filePath: String,       // Local path to the generated MP3 file

    @ColumnInfo(name = "file_url")
    val fileUrl: String,        // URL path for serving: "/tts/{filename}"

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,

    @ColumnInfo(name = "duration")
    val duration: Long = 0,     // Duration in seconds (estimated)

    @ColumnInfo(name = "volume")
    val volume: Int = 80,       // Playback volume 0-100

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
