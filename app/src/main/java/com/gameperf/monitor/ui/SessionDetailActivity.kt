package com.gameperf.monitor.ui

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gameperf.monitor.GamePerfApp
import com.gameperf.monitor.R
import com.gameperf.monitor.database.DataPoint
import com.gameperf.monitor.database.Session
import com.gameperf.monitor.databinding.ActivitySessionDetailBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch

/**
 * 对局详情页 - 展示各指标曲线图
 */
class SessionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }

    private lateinit var binding: com.gameperf.monitor.databinding.ActivitySessionDetailBinding
    private var session: Session? = null
    private var dataPoints: List<DataPoint> = emptyList()
    private var currentChartType = 0

    private val chartTypes = arrayOf(
        "帧率", "CPU 频率", "CPU 使用率", "CPU 温度",
        "电池温度", "GPU 频率", "功耗"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.gameperf.monitor.databinding.ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        if (sessionId == -1L) {
            finish()
            return
        }

        // 返回按钮
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 图表标签
        chartTypes.forEach { title ->
            binding.tabCharts.addTab(binding.tabCharts.newTab().setText(title))
        }

        binding.tabCharts.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let { currentChartType = it.position }
                updateChart()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        // 加载数据
        loadData(sessionId)
    }

    private fun loadData(sessionId: Long) {
        lifecycleScope.launch {
            val db = (application as GamePerfApp).database
            session = db.sessionDao().getSession(sessionId)
            dataPoints = db.dataPointDao().getDataPointsForSession(sessionId)

            session?.let { s ->
                supportActionBar?.title = s.name
                binding.tvAvgFps.text = String.format("%.1f", s.avgFps)
                binding.tvMinFps.text = String.format("%.0f", s.minFps)
                binding.tvMaxFps.text = String.format("%.0f", s.maxFps)

                val seconds = s.durationMs / 1000
                val minutes = seconds / 60
                val remainSeconds = seconds % 60
                binding.tvDuration.text = String.format("%02d:%02d", minutes, remainSeconds)
            }

            updateChart()
        }
    }

    private fun updateChart() {
        if (dataPoints.isEmpty() || session == null) return

        val chart = binding.chart
        chart.clear()
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.isScaleEnabled = true
        chart.setPinchZoom(true)
        chart.legend.isEnabled = true
        chart.legend.textColor = Color.WHITE
        chart.legend.textSize = 12f

        // X 轴 - 时间
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.WHITE
        xAxis.textSize = 10f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val startTime = session!!.startTime
                val timeMs = startTime + value.toLong() * 1000
                val seconds = (timeMs - startTime) / 1000
                val m = seconds / 60
                val s = seconds % 60
                return String.format("%02d:%02d", m, s)
            }
        }

        // Y 轴
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.WHITE
        leftAxis.textSize = 10f
        leftAxis.gridColor = Color.parseColor("#30363D")
        leftAxis.setDrawGridLines(true)

        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        chart.setBackgroundColor(Color.parseColor("#0D1117"))

        // 根据图表类型生成数据
        val entries = when (currentChartType) {
            0 -> dataPoints.mapIndexed { index, dp ->
                Entry(index.toFloat(), dp.fps)
            }
            1 -> dataPoints.mapIndexed { index, dp ->
                // 显示最高 CPU 频率 (kHz -> MHz)
                val maxFreq = maxOf(dp.cpuFreqBig, dp.cpuFreqLittle, dp.cpuFreqPrime)
                Entry(index.toFloat(), if (maxFreq > 0) maxFreq / 1000f else 0f)
            }
            2 -> dataPoints.mapIndexed { index, dp ->
                Entry(index.toFloat(), dp.cpuUsage)
            }
            3 -> dataPoints.mapIndexed { index, dp ->
                Entry(index.toFloat(), dp.cpuTemp)
            }
            4 -> dataPoints.mapIndexed { index, dp ->
                Entry(index.toFloat(), dp.batteryTemp)
            }
            5 -> dataPoints.mapIndexed { index, dp ->
                Entry(index.toFloat(), if (dp.gpuFreq > 0) dp.gpuFreq / 1000f else 0f)
            }
            6 -> dataPoints.mapIndexed { index, dp ->
                Entry(index.toFloat(), dp.powerUsage)
            }
            else -> emptyList()
        }

        if (entries.isEmpty()) return

        val color = when (currentChartType) {
            0 -> getColor(R.color.fps_color)
            1 -> getColor(R.color.cpu_color)
            2 -> getColor(R.color.cpu_color)
            3 -> getColor(R.color.temp_color)
            4 -> getColor(R.color.battery_color)
            5 -> getColor(R.color.gpu_color)
            6 -> getColor(R.color.power_color)
            else -> Color.WHITE
        }

        val label = chartTypes[currentChartType]
        val unit = when (currentChartType) {
            0 -> " FPS"
            1 -> " MHz"
            2 -> " %"
            3 -> " °C"
            4 -> " °C"
            5 -> " MHz"
            6 -> " mW"
            else -> ""
        }

        val dataSet = LineDataSet(entries, "$label$unit").apply {
            this.color = color
            this.setCircleColor(color)
            this.lineWidth = 2f
            this.circleRadius = 1.5f
            this.setDrawCircleHole(false)
            this.valueTextSize = 9f
            this.valueTextColor = Color.WHITE
            this.setDrawValues(false) // 数据点多时不显示数值
            this.mode = LineDataSet.Mode.CUBIC_BEZIER
            this.fillAlpha = 30
            this.setDrawFilled(true)
            this.fillColor = color
        }

        // 如果是 FPS，添加最低帧率参考线
        if (currentChartType == 0) {
            val minFpsEntries = dataPoints.mapIndexed { index, _ ->
                Entry(index.toFloat(), 30f) // 30fps 参考线
            }
            val minLine = LineDataSet(minFpsEntries, "30 FPS 参考").apply {
                this.color = Color.RED
                this.lineWidth = 1f
                this.enableDashedLine(10f, 5f, 0f)
                this.setDrawValues(false)
                this.setDrawCircles(false)
            }
            chart.data = LineData(dataSet, minLine)
        } else {
            chart.data = LineData(dataSet)
        }

        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun getColor(resId: Int): Int {
        return resources.getColor(resId, theme)
    }
}
