package com.gameperf.monitor.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.gameperf.monitor.GamePerfApp
import com.gameperf.monitor.R
import com.gameperf.monitor.database.Session
import com.gameperf.monitor.databinding.ActivityMainBinding
import com.gameperf.monitor.monitor.HardwareMonitor
import com.gameperf.monitor.service.MonitoringService
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var realtimeFragment: RealtimeFragment
    private lateinit var sessionsFragment: SessionsFragment
    private var isMonitoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 Fragment
        realtimeFragment = RealtimeFragment()
        sessionsFragment = SessionsFragment()

        // 设置 ViewPager
        val fragments = listOf(realtimeFragment, sessionsFragment)
        val titles = listOf("实时监控", "历史对局")

        binding.viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()

        // ViewPager 切换时刷新列表
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 1) {
                    sessionsFragment.loadSessions()
                }
            }
        })

        // FAB 开始/停止监控
        binding.fabStartStop.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                showStartDialog()
            }
        }

        // 会话点击事件
        sessionsFragment.setOnSessionClickListener { session ->
            val intent = Intent(this, SessionDetailActivity::class.java)
            intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.id)
            startActivity(intent)
        }

        sessionsFragment.setOnSessionLongClickListener { session ->
            showDeleteDialog(session)
        }

        // 请求通知权限
        requestNotificationPermission()

        // 检查 Root
        checkRootAccess()
    }

    private fun showStartDialog() {
        val editText = EditText(this).apply {
            hint = "请输入对局名称"
            setPadding(48, 32, 48, 32)
            setTextColor(resources.getColor(R.color.text_primary, theme))
            setHintTextColor(resources.getColor(R.color.text_secondary, theme))
        }

        AlertDialog.Builder(this)
            .setTitle("开始监控")
            .setView(editText)
            .setPositiveButton("开始") { _, _ ->
                val name = editText.text.toString().ifBlank { "未命名对局" }
                startMonitoring(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startMonitoring(sessionName: String) {
        // 检查 Root
        val monitor = HardwareMonitor(this)
        if (!monitor.checkRoot()) {
            Toast.makeText(this, "Root 权限不可用，请确认设备已 Root", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_START
            putExtra(MonitoringService.EXTRA_SESSION_NAME, sessionName)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isMonitoring = true
        binding.fabStartStop.setImageResource(android.R.drawable.ic_media_pause)
        realtimeFragment.setMonitoringState(true)
        Toast.makeText(this, "开始监控: $sessionName", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_STOP
        }
        startService(intent)

        isMonitoring = false
        binding.fabStartStop.setImageResource(android.R.drawable.ic_media_play)
        realtimeFragment.setMonitoringState(false)
        Toast.makeText(this, "监控已停止，对局已保存", Toast.LENGTH_SHORT).show()

        // 延迟刷新列表
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            sessionsFragment.loadSessions()
        }
    }

    private fun showDeleteDialog(session: Session) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确认删除对局「${session.name}」的记录？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val db = (application as GamePerfApp).database
                    db.sessionDao().deleteDataPointsBySessionId(session.id)
                    db.sessionDao().deleteSessionById(session.id)
                    sessionsFragment.loadSessions()
                    Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(this).apply {
                if (!areNotificationsEnabled()) {
                    // 可以在这里请求权限，但前台服务权限通常在 manifest 中声明
                }
            }
        }
    }

    private fun checkRootAccess() {
        lifecycleScope.launch {
            val monitor = HardwareMonitor(this@MainActivity)
            val hasRoot = monitor.checkRoot()
            if (!hasRoot) {
                Toast.makeText(
                    this@MainActivity,
                    "未检测到 Root 权限，硬件数据采集将不可用",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复时刷新列表
        sessionsFragment.loadSessions()
    }
}
