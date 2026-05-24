package com.gameperf.monitor.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gameperf.monitor.database.Session
import com.gameperf.monitor.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class SessionAdapter(
    private val onItemClick: (Session) -> Unit,
    private val onItemLongClick: (Session) -> Unit
) : ListAdapter<Session, SessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: Session) {
            binding.tvSessionName.text = session.name
            binding.tvSessionDate.text = dateFormat.format(Date(session.startTime))
            binding.tvAvgFps.text = String.format("%.1f", session.avgFps)
            binding.tvMinFps.text = String.format("%.0f", session.minFps)
            binding.tvMaxFps.text = String.format("%.0f", session.maxFps)

            val seconds = session.durationMs / 1000
            val minutes = seconds / 60
            val remainSeconds = seconds % 60
            binding.tvDuration.text = String.format("%02d:%02d", minutes, remainSeconds)

            binding.tvCpuInfo.text = "CPU: %.0f%%".format(session.avgCpuUsage)
            binding.tvTempInfo.text = "温度: %.1f°C".format(session.maxCpuTemp)
            binding.tvPowerInfo.text = "功耗: %.0fmW".format(session.avgPowerUsage)

            binding.root.setOnClickListener { onItemClick(session) }
            binding.root.setOnLongClickListener {
                onItemLongClick(session)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionDiffCallback : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem == newItem
        }
    }
}
