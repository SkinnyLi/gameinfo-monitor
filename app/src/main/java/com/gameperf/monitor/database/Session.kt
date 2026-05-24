package com.gameperf.monitor.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 监控会话 - 代表一次完整的游戏对局
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val avgFps: Float,
    val minFps: Float,
    val maxFps: Float,
    val avgCpuUsage: Float,
    val maxCpuTemp: Float,
    val maxBatteryTemp: Float,
    val avgGpuFreq: Long,
    val avgPowerUsage: Float
)
