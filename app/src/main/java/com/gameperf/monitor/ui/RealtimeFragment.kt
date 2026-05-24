package com.gameperf.monitor.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gameperf.monitor.data.PerformanceSnapshot
import com.gameperf.monitor.databinding.FragmentRealtimeBinding
import com.gameperf.monitor.monitor.HardwareMonitor
import com.gameperf.monitor.monitor.PerformanceAggregator
import com.gameperf.monitor.service.MonitoringService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 实时监控面板 Fragment
 */
class RealtimeFragment : Fragment() {

    private var _binding: FragmentRealtimeBinding? = null
    private val binding get() = _binding!!

    private val aggregator = PerformanceAggregator()
    private var hardwareMonitor: HardwareMonitor? = null
    private var updateJob: Job? = null
    private var isMonitoring = false

    private val monitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 可以接收服务发来的数据更新广播
        }
    }

    fun setMonitoringState(monitoring: Boolean) {
        isMonitoring = monitoring
        if (monitoring) {
            startUpdating()
        } else {
            stopUpdating()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRealtimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hardwareMonitor = HardwareMonitor(requireContext())

        // 注册广播接收器
        val filter = IntentFilter("com.gameperf.monitor.DATA_UPDATE")
        try {
            requireContext().registerReceiver(monitorReceiver, filter)
        } catch (_: Exception) {}
    }

    private fun startUpdating() {
        if (updateJob?.isActive == true) return

        updateJob = viewLifecycleOwner.lifecycleScope.launch {
            hardwareMonitor?.let { monitor ->
                // 初始化
                try {
                    monitor.init()
                } catch (e: Exception) {
                    Toast.makeText(context, "Root 权限不可用", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                while (isActive && isMonitoring) {
                    try {
                        val snapshot = monitor.collectSnapshot()
                        updateUI(snapshot)
                        delay(500) // UI 更新频率 500ms
                    } catch (e: Exception) {
                        delay(1000)
                    }
                }
            }
        }
    }

    private fun stopUpdating() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateUI(snapshot: PerformanceSnapshot) {
        activity?.runOnUiThread {
            // FPS
            binding.tvCurrentFps.text = if (snapshot.fps > 0) {
                String.format("%.0f", snapshot.fps)
            } else {
                "--"
            }

            // FPS 颜色
            binding.tvCurrentFps.setTextColor(
                when {
                    snapshot.fps >= 55 -> resources.getColor(com.gameperf.monitor.R.color.fps_color, null)
                    snapshot.fps >= 30 -> resources.getColor(com.gameperf.monitor.R.color.orange, null)
                    snapshot.fps > 0 -> resources.getColor(com.gameperf.monitor.R.color.red, null)
                    else -> resources.getColor(com.gameperf.monitor.R.color.text_secondary, null)
                }
            )

            // CPU 使用率
            binding.tvCpuUsage.text = if (snapshot.cpuUsage > 0) {
                String.format("%.0f%%", snapshot.cpuUsage)
            } else {
                "--"
            }

            // CPU 温度
            binding.tvCpuTemp.text = if (snapshot.cpuTemp > 0) {
                String.format("%.1f°C", snapshot.cpuTemp)
            } else {
                "--"
            }

            // CPU 频率 (显示最高核心)
            val maxFreq = snapshot.maxCpuFreq
            binding.tvCpuFreq.text = if (maxFreq > 0) {
                String.format("%d MHz", maxFreq / 1000)
            } else {
                "--"
            }

            // GPU 频率
            binding.tvGpuFreq.text = if (snapshot.gpuFreq > 0) {
                String.format("%d MHz", snapshot.gpuFreq / 1000)
            } else {
                "--"
            }

            // 电池温度
            binding.tvBatteryTemp.text = if (snapshot.batteryTemp > 0) {
                String.format("%.1f°C", snapshot.batteryTemp)
            } else {
                "--"
            }

            // 功耗
            binding.tvPower.text = if (snapshot.powerUsage > 0) {
                String.format("%.0f mW", snapshot.powerUsage)
            } else {
                "--"
            }

            // 核心频率详情
            binding.tvPrimeFreq.text = if (snapshot.cpuFreqPrime > 0) {
                String.format("%d MHz", snapshot.cpuFreqPrime / 1000)
            } else {
                "N/A"
            }
            binding.tvBigFreq.text = if (snapshot.cpuFreqBig > 0) {
                String.format("%d MHz", snapshot.cpuFreqBig / 1000)
            } else {
                "N/A"
            }
            binding.tvLittleFreq.text = if (snapshot.cpuFreqLittle > 0) {
                String.format("%d MHz", snapshot.cpuFreqLittle / 1000)
            } else {
                "N/A"
            }

            // 更新会话统计
            val stats = aggregator.getCurrentStats()
            binding.tvAvgFps.text = String.format("%.1f", stats.avgFps)
            binding.tvMinFps.text = String.format("%.0f", stats.minFps)
            binding.tvMaxFps.text = String.format("%.0f", stats.maxFps)
            binding.tvDuration.text = stats.formattedDuration
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopUpdating()
        try {
            requireContext().unregisterReceiver(monitorReceiver)
        } catch (_: Exception) {}
        _binding = null
    }
}
