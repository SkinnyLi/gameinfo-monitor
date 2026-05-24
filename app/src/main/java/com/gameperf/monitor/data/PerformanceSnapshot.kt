package com.gameperf.monitor.data

/**
 * 单次采样的性能数据点
 */
data class PerformanceSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val fps: Float = 0f,
    val cpuFreqBig: Long = 0,       // 大核频率 kHz
    val cpuFreqLittle: Long = 0,    // 小核频率 kHz
    val cpuFreqPrime: Long = 0,     // 超大核频率 kHz
    val cpuUsage: Float = 0f,       // 0-100%
    val cpuTemp: Float = 0f,        // °C
    val batteryTemp: Float = 0f,    // °C
    val gpuFreq: Long = 0,          // kHz
    val powerUsage: Float = 0f      // mW
) {
    /** 返回最高 CPU 核心频率 */
    val maxCpuFreq: Long
        get() = maxOf(cpuFreqBig, cpuFreqLittle, cpuFreqPrime)
}
