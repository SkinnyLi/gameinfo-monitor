package com.gameperf.monitor.monitor

import android.content.Context
import android.os.Build
import com.gameperf.monitor.data.PerformanceSnapshot
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 硬件监控器 - 通过 Root 权限读取系统文件获取真实硬件数据
 */
class HardwareMonitor(private val context: Context) {

    private var shell: Shell? = null
    private var lastCpuTimeTotal: Long = 0L
    private var lastCpuTimeIdle: Long = 0L

    /** 检查 Root 权限是否可用 */
    fun checkRoot(): Boolean {
        return try {
            val result = Shell.cmd("su -c 'id'").exec()
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /** 初始化 Shell */
    suspend fun init() {
        withContext(Dispatchers.IO) {
            shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .build()
        }
    }

    /** 采集一次完整性能快照 */
    suspend fun collectSnapshot(): PerformanceSnapshot = withContext(Dispatchers.IO) {
        PerformanceSnapshot(
            fps = readFps(),
            cpuFreqBig = readCpuFreq(CPU_CLUSTER_BIG),
            cpuFreqLittle = readCpuFreq(CPU_CLUSTER_LITTLE),
            cpuFreqPrime = readCpuFreq(CPU_CLUSTER_PRIME),
            cpuUsage = readCpuUsage(),
            cpuTemp = readCpuTemp(),
            batteryTemp = readBatteryTemp(),
            gpuFreq = readGpuFreq(),
            powerUsage = readPowerUsage()
        )
    }

    // ==================== FPS ====================

    /**
     * 通过 dumpsys SurfaceFlinger 获取当前帧率
     * 部分设备可能需要额外权限
     */
    private fun readFps(): Float {
        return try {
            val result = Shell.cmd(
                "dumpsys SurfaceFlinger --latency \"SurfaceView\" 2>/dev/null | head -1"
            ).exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                // 备用方案：通过 gfxinfo 获取
                readFpsFromGfxInfo()
            } else {
                readFpsFromGfxInfo()
            }
        } catch (e: Exception) {
            readFpsFromGfxInfo()
        }
    }

    private fun readFpsFromGfxInfo(): Float {
        return try {
            val result = Shell.cmd("dumpsys gfxinfo $getCurrentForegroundPackage()").exec()
            if (result.isSuccess) {
                parseFpsFromGfxInfo(result.out)
            } else {
                0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    private fun parseFpsFromGfxInfo(lines: List<String>): Float {
        for (line in lines) {
            if (line.contains("Janky frames") || line.contains("90th percentile")) {
                // 解析帧率信息
                val fpsMatch = Regex("""(\d+)fps""").find(line)
                if (fpsMatch != null) {
                    return fpsMatch.groupValues[1].toFloatOrNull() ?: 0f
                }
            }
        }
        // 备用：从帧时间计算
        for (line in lines) {
            if (line.trim().startsWith("Total frames rendered")) {
                val match = Regex("""Total frames rendered:\s*(\d+)""").find(line)
                if (match != null) {
                    return match.groupValues[1].toFloatOrNull() ?: 0f
                }
            }
        }
        return 0f
    }

    private fun getCurrentForegroundPackage(): String {
        return try {
            val result = Shell.cmd("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val line = result.out.first()
                val match = Regex("""([a-zA-Z0-9_.]+)/""").find(line)
                match?.groupValues?.get(1) ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ==================== CPU 频率 ====================

    private fun readCpuFreq(cluster: CpuCluster): Long {
        return try {
            val result = Shell.cmd("cat ${cluster.freqFile}").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.first().trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    // ==================== CPU 使用率 ====================

    private fun readCpuUsage(): Float {
        return try {
            val result = Shell.cmd("cat /proc/stat").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                parseCpuUsage(result.out.first())
            } else {
                0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    private fun parseCpuUsage(line: String): Float {
        val parts = line.split("\\s+".toRegex())
        if (parts.size < 5) return 0f

        val user = parts[1].toLongOrNull() ?: 0
        val nice = parts[2].toLongOrNull() ?: 0
        val system = parts[3].toLongOrNull() ?: 0
        val idle = parts[4].toLongOrNull() ?: 0
        val iowait = if (parts.size > 5) (parts[5].toLongOrNull() ?: 0) else 0

        val totalCurrent = user + nice + system + idle + iowait
        val idleCurrent = idle + iowait

        val totalDiff = totalCurrent - lastCpuTimeTotal
        val idleDiff = idleCurrent - lastCpuTimeIdle

        lastCpuTimeTotal = totalCurrent
        lastCpuTimeIdle = idleCurrent

        return if (totalDiff > 0) {
            ((totalDiff - idleDiff).toFloat() / totalDiff) * 100f
        } else {
            0f
        }
    }

    // ==================== CPU 温度 ====================

    private fun readCpuTemp(): Float {
        // 尝试多个温度传感器路径
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/class/thermal/thermal_zone4/temp",
            "/sys/class/thermal/thermal_zone5/temp",
            "/sys/class/thermal/thermal_zone6/temp",
            "/sys/class/thermal/thermal_zone7/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )

        for (path in thermalPaths) {
            try {
                val result = Shell.cmd("cat $path").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    val raw = result.out.first().trim().toFloatOrNull() ?: continue
                    // 大多数设备返回毫摄氏度
                    return if (raw > 1000) raw / 1000f else raw
                }
            } catch (_: Exception) {
                continue
            }
        }

        // 备用：通过 dumpsys battery 获取（部分设备可用）
        return try {
            val result = Shell.cmd("dumpsys battery").exec()
            if (result.isSuccess) {
                for (line in result.out) {
                    if (line.contains("temperature")) {
                        val match = Regex("""temperature:\s*(\d+)""").find(line)
                        if (match != null) {
                            return match.groupValues[1].toFloatOrNull()?.div(10f) ?: 0f
                        }
                    }
                }
            }
            0f
        } catch (e: Exception) {
            0f
        }
    }

    // ==================== 电池温度 ====================

    private fun readBatteryTemp(): Float {
        return try {
            val result = Shell.cmd("dumpsys battery").exec()
            if (result.isSuccess) {
                for (line in result.out) {
                    if (line.contains("temperature")) {
                        val match = Regex("""temperature:\s*(\d+)""").find(line)
                        if (match != null) {
                            return match.groupValues[1].toFloatOrNull()?.div(10f) ?: 0f
                        }
                    }
                }
            }
            // 备用路径
            val altResult = Shell.cmd("cat /sys/class/power_supply/battery/temp").exec()
            if (altResult.isSuccess && altResult.out.isNotEmpty()) {
                val raw = altResult.out.first().trim().toFloatOrNull() ?: 0f
                return if (raw > 1000) raw / 10f else raw
            }
            0f
        } catch (e: Exception) {
            0f
        }
    }

    // ==================== GPU 频率 ====================

    private fun readGpuFreq(): Long {
        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpuclk",
            "/sys/class/devfreq/qcom,gpuclk/cur_freq",
            "/sys/class/devfreq/soc:qcom,gpu0/cur_freq",
            "/sys/class/devfreq/mali.0/cur_freq",
            "/sys/class/devfreq/fdab0000.gpu/cur_freq",
            "/sys/devices/platform/soc/5000000.gpu/devfreq/5000000.gpu/cur_freq",
            "/sys/class/misc/mali0/device/clock"
        )

        for (path in gpuPaths) {
            try {
                val result = Shell.cmd("cat $path").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    val freq = result.out.first().trim().toLongOrNull() ?: continue
                    // 如果值小于 100000，可能是 MHz，转换为 kHz
                    return if (freq < 100000) freq * 1000 else freq
                }
            } catch (_: Exception) {
                continue
            }
        }

        // 备用：通过 Adreno GPU 驱动
        return try {
            val result = Shell.cmd("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.first().trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    // ==================== 功耗 ====================

    private fun readPowerUsage(): Float {
        val powerPaths = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/ipc/power_now"
        )

        var currentMa = 0f
        var voltageMv = 0f

        for (path in powerPaths) {
            try {
                val result = Shell.cmd("cat $path").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    val value = result.out.first().trim().toFloatOrNull() ?: continue
                    when {
                        path.contains("current") -> {
                            // 通常返回微安(μA)，部分设备返回毫安(mA)
                            currentMa = if (value > 10000) value / 1000f else value
                        }
                        path.contains("voltage") -> {
                            voltageMv = if (value > 10000) value / 1000f else value
                        }
                        path.contains("power") -> {
                            // 直接返回功率 (μW 或 mW)
                            return if (value > 10000) value / 1000f else value
                        }
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }

        // 通过电流 × 电压计算功耗
        return if (currentMa > 0 && voltageMv > 0) {
            currentMa * voltageMv
        } else {
            0f
        }
    }

    // ==================== CPU 集群配置 ====================

    /**
     * CPU 集群定义 - 不同设备路径可能不同
     * 这里提供常见路径，实际使用时可能需要根据设备适配
     */
    enum class CpuCluster(val freqFile: String) {
        // 小核集群 (通常为 CPU0-3)
        LITTLE("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"),
        // 大核集群 (通常为 CPU4-7)
        BIG("/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq"),
        // 超大核 (部分设备有 CPU7 或 CPU8)
        PRIME("/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq")
    }

    companion object {
        private const val TAG = "HardwareMonitor"
    }
}
