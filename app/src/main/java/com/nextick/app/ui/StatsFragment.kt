package com.nextick.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextick.app.R
import com.nextick.app.core.Format
import com.nextick.app.core.Notifier
import com.nextick.app.data.Store
import com.nextick.app.data.TagNames
import com.nextick.app.databinding.FragmentStatsBinding

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val adapter = SessionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.timeline.layoutManager = LinearLayoutManager(requireContext())
        binding.timeline.adapter = adapter
        binding.pie.setOnClickListener { Notifier.tap(requireContext()) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun refresh() {
        if (_binding == null) return
        val ctx = requireContext()
        val sessions =
            Store.sessionsOfDay(Store.todayKey()).sortedByDescending { it.startAt }

        val groups = LinkedHashMap<String, PieChartView.Slice>()
        sessions.forEach { session ->
            val current = groups[session.tagId]
            groups[session.tagId] = PieChartView.Slice(
                label = TagNames.of(ctx, session.tagKey, session.tagName),
                minutes = (current?.minutes ?: 0) + session.minutes,
                color = session.tagColor
            )
        }

        val slices = groups.values.sortedByDescending { it.minutes }
        val total = slices.sumOf { it.minutes }

        binding.pie.slices = slices
        binding.pie.centerPrimary = Format.minutes(ctx, total)
        binding.pie.centerSecondary = getString(R.string.stats_today)
        binding.totalText.text =
            getString(R.string.total_label, Format.minutes(ctx, total))

        binding.legend.removeAllViews()

        slices.forEach { slice ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ctx.dp(6), 0, ctx.dp(6))
                isClickable = true
                setOnClickListener { Notifier.tap(ctx) }
            }

            val dot = View(ctx).apply {
                background = circle(slice.color)
            }
            row.addView(
                dot,
                LinearLayout.LayoutParams(ctx.dp(12), ctx.dp(12))
            )

            val label = TextView(ctx).apply {
                text = slice.label
                textSize = 14f
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        ctx,
                        R.color.text_primary
                    )
                )
                setPadding(ctx.dp(10), 0, 0, 0)
            }
            row.addView(
                label,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val value = TextView(ctx).apply {
                text = Format.minutes(ctx, slice.minutes)
                textSize = 13f
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        ctx,
                        R.color.text_secondary
                    )
                )
            }
            row.addView(value)

            binding.legend.addView(row)
        }

        adapter.submit(sessions)
        binding.emptyText.visibility =
            if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }
}
