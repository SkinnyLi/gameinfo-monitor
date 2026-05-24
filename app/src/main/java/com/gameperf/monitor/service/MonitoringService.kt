package com.gameperf.monitor.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gameperf.monitor.GamePerfApp
import com.gameperf.monitor.R
import com.gameperf.monitor.database.DataPoint
import com.gameperf.monitor.database.Session
import com.gameperf.monitor.monitor.HardwareMonitor
import com.gameperf.monitor.monitor.PerformanceAggregator
import com.gameperf.monitor.monitor.SessionStats
import kotlinx.coroutines.*

/**
 * 前台监控服务 - 在后台持续采集性能数据
 */
class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null
    private var hardwareMonitor: HardwareMonitor? = null
    private val aggregator = PerformanceAggregator()

    private var currentSessionId: Long = -1
    private var currentSessionName: String = ""
    private var sessionStartTime: Long = 0
    private var pendingDataPoints = mutableListOf<DataPoint>()

    // 用于计算会话统计
    private var minFps = Float.MAX_VALUE
    private var maxFps = Float.MIN_VALUE
    private var fpsSum = 0f
    private var fpsCount = 0
    private var cpuUsageSum = 0f
    private var cpuUsageCount = 0
    private var maxCpuTemp = 0f
    private var maxBatteryTemp = 0f
    private var gpuFreqSum = 0L
    private var gpuFreqCount = 0
    private var powerSum = 0f
    private var powerCount = 0

    companion object {
        const val CHANNEL_ID = "monitoring_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.gameperf.monitor.START"
        const val ACTION_STOP = "com.gameperf.monitor.STOP"

        const val EXTRA_SESSION_NAME = "session_name"

        /** 采集间隔（毫秒） */
        const val SAMPLE_INTERVAL = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        hardwareMonitor = HardwareMonitor(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "未命名对局"
                startMonitoring(sessionName)
            }
            ACTION_STOP -> {
                stopMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring(sessionName: String) {
        currentSessionName = sessionName

        // 启动前台通知
        startForeground(NOTIFICATION_ID, createNotification("正在监控: $sessionName"))

        sessionStartTime = System.currentTimeMillis()
        resetSessionStats()
        aggregator.startSession()

        monitorJob = serviceScope.launch {
            hardwareMonitor?.init()
            hardwareMonitor?.let { monitor ->
                while (isActive) {
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            monitor.collectSnapshot()
                        }

                        // 更新聚合器
                        aggregator.updateSnapshot(snapshot)

                        // 保存数据点（使用临时 sessionId，flush 时会更新）
                        val dataPoint = DataPoint(
                            sessionId = currentSessionId,
                            timestamp = snapshot.timestamp,
                            fps = snapshot.fps,
                            cpuFreqBig = snapshot.cpuFreqBig,
                            cpuFreqLittle = snapshot.cpuFreqLittle,
                            cpuFreqPrime = snapshot.cpuFreqPrime,
                            cpuUsage = snapshot.cpuUsage,
                            cpuTemp = snapshot.cpuTemp,
                            batteryTemp = snapshot.batteryTemp,
                            gpuFreq = snapshot.gpuFreq,
                            powerUsage = snapshot.powerUsage
                        )
                        pendingDataPoints.add(dataPoint)

                        // 更新会话统计
                        updateSessionStats(snapshot)

                        // 每 10 个数据点批量写入数据库
                        if (pendingDataPoints.size >= 10) {
                            flushDataPoints()
                        }

                        delay(SAMPLE_INTERVAL)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        delay(SAMPLE_INTERVAL)
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        aggregator.stopSession()

        // 保存剩余数据点并更新会话记录
        serviceScope.launch {
            flushDataPoints()

            // 更新会话记录（使用真实统计数据）
            if (currentSessionId > 0) {
                val stats = getCurrentSessionStats()
                val session = Session(
                    id = currentSessionId,
                    name = currentSessionName,
                    startTime = sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    durationMs = System.currentTimeMillis() - sessionStartTime,
                    avgFps = stats.avgFps,
                    minFps = stats.minFps,
                    maxFps = stats.maxFps,
                    avgCpuUsage = if (cpuUsageCount > 0) cpuUsageSum / cpuUsageCount else 0f,
                    maxCpuTemp = maxCpuTemp,
                    maxBatteryTemp = maxBatteryTemp,
                    avgGpuFreq = if (gpuFreqCount > 0) gpuFreqSum / gpuFreqCount else 0,
                    avgPowerUsage = if (powerCount > 0) powerSum / powerCount else 0f
                )
                val db = (application as GamePerfApp).database
                db.sessionDao().insertSession(session)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun flushDataPoints() {
        if (pendingDataPoints.isEmpty()) return
        val db = (application as GamePerfApp).database

        // 如果是新会话，先创建会话记录获取 ID
        if (currentSessionId <= 0) {
            val tempSession = Session(
                name = currentSessionName,
                startTime = sessionStartTime,
                endTime = sessionStartTime,
                durationMs = 0,
                avgFps = 0f,
                minFps = 0f,
                maxFps = 0f,
                avgCpuUsage = 0f,
                maxCpuTemp = 0f,
                maxBatteryTemp = 0f,
                avgGpuFreq = 0,
                avgPowerUsage = 0f
            )
            currentSessionId = db.sessionDao().insertSession(tempSession)

            // 重新创建数据点列表，使用正确的 sessionId
            val updatedPoints = pendingDataPoints.map { dp ->
                dp.copy(sessionId = currentSessionId)
            }
            pendingDataPoints.clear()
            pendingDataPoints.addAll(updatedPoints)
        }

        db.dataPointDao().insertDataPoints(pendingDataPoints.toList())
        pendingDataPoints.clear()
    }

    private fun resetSessionStats() {
        currentSessionId = -1
        currentSessionName = ""
        minFps = Float.MAX_VALUE
        maxFps = Float.MIN_VALUE
        fpsSum = 0f
        fpsCount = 0
        cpuUsageSum = 0f
        cpuUsageCount = 0
        maxCpuTemp = 0f
        maxBatteryTemp = 0f
        gpuFreqSum = 0L
        gpuFreqCount = 0
        powerSum = 0f
        powerCount = 0
        pendingDataPoints.clear()
    }

    private fun updateSessionStats(snapshot: com.gameperf.monitor.data.PerformanceSnapshot) {
        if (snapshot.fps > 0) {
            fpsSum += snapshot.fps
            fpsCount++
            if (snapshot.fps < minFps) minFps = snapshot.fps
            if (snapshot.fps > maxFps) maxFps = snapshot.fps
        }
        if (snapshot.cpuUsage > 0) {
            cpuUsageSum += snapshot.cpuUsage
            cpuUsageCount++
        }
        if (snapshot.cpuTemp > 0 && snapshot.cpuTemp < 150) {
            if (snapshot.cpuTemp > maxCpuTemp) maxCpuTemp = snapshot.cpuTemp
        }
        if (snapshot.batteryTemp > 0 && snapshot.batteryTemp < 100) {
            if (snapshot.batteryTemp > maxBatteryTemp) maxBatteryTemp = snapshot.batteryTemp
        }
        if (snapshot.gpuFreq > 0) {
            gpuFreqSum += snapshot.gpuFreq
            gpuFreqCount++
        }
        if (snapshot.powerUsage > 0) {
            powerSum += snapshot.powerUsage
            powerCount++
        }
    }

    private fun getCurrentSessionStats(): SessionStats {
        return SessionStats(
            durationMs = System.currentTimeMillis() - sessionStartTime,
            avgFps = if (fpsCount > 0) fpsSum / fpsCount else 0f,
            minFps = if (minFps == Float.MAX_VALUE) 0f else minFps,
            maxFps = if (maxFps == Float.MIN_VALUE) 0f else maxFps
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "性能监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "游戏性能监控后台服务"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val stopIntent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("游戏性能监控")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止监控",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        serviceScope.cancel()
    }
}
