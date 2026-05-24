package com.gameperf.monitor.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 性能数据点 - 每秒采集一次的数据
 */
@Entity(
    tableName = "data_points",
    primaryKeys = ["sessionId", "timestamp"]
)
data class DataPoint(
    val sessionId: Long,
    val timestamp: Long,
    val fps: Float,
    val cpuFreqBig: Long,
    val cpuFreqLittle: Long,
    val cpuFreqPrime: Long,
    val cpuUsage: Float,
    val cpuTemp: Float,
    val batteryTemp: Float,
    val gpuFreq: Long,
    val powerUsage: Float
)
