package com.screenpulse.player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val taskType: String = "SCHEDULED_PLAYBACK",  // SCHEDULED_PLAYBACK or INTERSTITIAL
    val scheduleMode: String = "ALWAYS",          // ALWAYS, DAILY_PERIOD, WEEKLY_PERIOD
    val startTime: String = "",                    // HH:mm format
    val endTime: String = "",                      // HH:mm format
    val daysOfWeek: String = "",                   // comma-separated: "1,2,3,4,5"
    val orderMode: String = "SEQUENTIAL",          // SEQUENTIAL or RANDOM
    val repeatMode: String = "LOOP",               // ONCE, LOOP, N_TIMES
    val repeatCount: Int = 0,
    val groupIds: String = "[]",                   // JSON array of group IDs
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
