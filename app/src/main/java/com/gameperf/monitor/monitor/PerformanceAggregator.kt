package com.gameperf.monitor.monitor

import com.gameperf.monitor.data.PerformanceSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 帧率计算器 - 通过 SurfaceFlinger 的帧时间戳计算实时 FPS
 */
class FpsCalculator {

    private var frameTimestamps = mutableListOf<Long>()
    private val maxSamples = 60

    /**
     * 添加一个帧时间戳
     */
    fun addFrameTimestamp(timestamp: Long) {
        synchronized(frameTimestamps) {
            frameTimestamps.add(timestamp)
            if (frameTimestamps.size > maxSamples) {
                frameTimestamps.removeAt(0)
            }
        }
    }

    /**
     * 计算当前 FPS
     */
    fun calculateFps(): Float {
        synchronized(frameTimestamps) {
            if (frameTimestamps.size < 2) return 0f
            val duration = (frameTimestamps.last() - frameTimestamps.first()).toFloat() / 1000f
            return if (duration > 0) {
                (frameTimestamps.size - 1) / duration
            } else {
                0f
            }
        }
    }

    fun reset() {
        synchronized(frameTimestamps) {
            frameTimestamps.clear()
        }
    }
}

/**
 * 性能数据聚合器 - 跟踪会话级别的统计数据
 */
class PerformanceAggregator {

    private val _currentFps = MutableStateFlow(0f)
    val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

    private val _currentSnapshot = MutableStateFlow(PerformanceSnapshot())
    val currentSnapshot: StateFlow<PerformanceSnapshot> = _currentSnapshot.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var minFps = Float.MAX_VALUE
    private var maxFps = Float.MIN_VALUE
    private var fpsSum = 0f
    private var fpsCount = 0
    private var sessionStartTime = 0L

    fun startSession() {
        minFps = Float.MAX_VALUE
        maxFps = Float.MIN_VALUE
        fpsSum = 0f
        fpsCount = 0
        sessionStartTime = System.currentTimeMillis()
        _isMonitoring.value = true
    }

    fun stopSession(): SessionStats {
        _isMonitoring.value = false
        return SessionStats(
            durationMs = System.currentTimeMillis() - sessionStartTime,
            avgFps = if (fpsCount > 0) fpsSum / fpsCount else 0f,
            minFps = if (minFps == Float.MAX_VALUE) 0f else minFps,
            maxFps = if (maxFps == Float.MIN_VALUE) 0f else maxFps
        )
    }

    fun updateSnapshot(snapshot: PerformanceSnapshot) {
        _currentSnapshot.value = snapshot

        if (snapshot.fps > 0) {
            _currentFps.value = snapshot.fps
            fpsSum += snapshot.fps
            fpsCount++
            if (snapshot.fps < minFps) minFps = snapshot.fps
            if (snapshot.fps > maxFps) maxFps = snapshot.fps
        }
    }

    fun getCurrentStats(): SessionStats {
        return SessionStats(
            durationMs = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0,
            avgFps = if (fpsCount > 0) fpsSum / fpsCount else 0f,
            minFps = if (minFps == Float.MAX_VALUE) 0f else minFps,
            maxFps = if (maxFps == Float.MIN_VALUE) 0f else maxFps
        )
    }
}

data class SessionStats(
    val durationMs: Long,
    val avgFps: Float,
    val minFps: Float,
    val maxFps: Float
) {
    val formattedDuration: String
        get() {
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }
}
