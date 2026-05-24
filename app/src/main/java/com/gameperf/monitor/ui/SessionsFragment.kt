package com.gameperf.monitor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gameperf.monitor.GamePerfApp
import com.gameperf.monitor.database.Session
import com.gameperf.monitor.databinding.FragmentSessionsBinding
import kotlinx.coroutines.launch

/**
 * 历史对局列表 Fragment
 */
class SessionsFragment : Fragment() {

    private var _binding: FragmentSessionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SessionAdapter
    private var onSessionClick: ((Session) -> Unit)? = null
    private var onSessionLongClick: ((Session) -> Unit)? = null

    fun setOnSessionClickListener(listener: (Session) -> Unit) {
        onSessionClick = listener
    }

    fun setOnSessionLongClickListener(listener: (Session) -> Unit) {
        onSessionLongClick = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SessionAdapter(
            onItemClick = { session -> onSessionClick?.invoke(session) },
            onItemLongClick = { session -> onSessionLongClick?.invoke(session) }
        )

        binding.recyclerSessions.adapter = adapter
        loadSessions()
    }

    fun loadSessions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = (requireContext().applicationContext as GamePerfApp).database
            val sessions = db.sessionDao().getAllSessions()

            if (sessions.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerSessions.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerSessions.visibility = View.VISIBLE
                adapter.submitList(sessions)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
