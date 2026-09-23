package com.nextick.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextick.app.R
import com.nextick.app.core.Format
import com.nextick.app.core.Notifier
import com.nextick.app.data.Session
import com.nextick.app.data.Store
import com.nextick.app.data.TagNames
import com.nextick.app.databinding.FragmentStatsBinding
import com.nextick.app.databinding.ItemSessionBinding

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

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
        binding.pie.setOnClickListener {
            Notifier.tap(requireContext())
        }
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
            Store.sessionsOfDay(Store.todayKey())
                .sortedByDescending { it.startAt }

        val groups = LinkedHashMap<String, PieChartView.Slice>()

        sessions.forEach { session ->
            val current = groups[session.tagId]
            groups[session.tagId] = PieChartView.Slice(
                label = TagNames.of(ctx, session.tagKey, session.tagName),
                minutes = (current?.minutes ?: 0) + session.minutes,
                color = session.tagColor
            )
        }

        val slices =
            groups.values.sortedByDescending { it.minutes }

        val total = slices.sumOf { it.minutes }

        binding.pie.slices = slices
        binding.pie.centerPrimary = Format.minutes(ctx, total)
        binding.pie.centerSecondary = getString(R.string.stats_today)

        binding.timelineList.removeAllViews()

        sessions.forEach { session ->
            val row = ItemSessionBinding.inflate(
                layoutInflater,
                binding.timelineList,
                false
            )

            row.timeRange.text =
                Format.range(session.startAt, session.endAt)

            row.tagName.text =
                TagNames.of(ctx, session.tagKey, session.tagName)

            row.duration.text =
                if (session.completed) {
                    Format.minutes(ctx, session.minutes)
                } else {
                    getString(
                        R.string.duration_early,
                        Format.minutes(ctx, session.minutes)
                    )
                }

            row.colorDot.background = circle(session.tagColor)

            val points = session.points
            row.points.text =
                points?.let { Format.points(it) } ?: "-"

            row.points.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if ((points ?: 0.0) >= 0) {
                        R.color.positive
                    } else {
                        R.color.negative
                    }
                )
            )

            row.root.setOnClickListener {
                Notifier.tap(ctx)
                confirmDelete(session)
            }

            binding.timelineList.addView(row.root)
        }

        binding.emptyText.visibility =
            if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(session: Session) {
        val ctx = requireContext()
        val tag = TagNames.of(
            ctx,
            session.tagKey,
            session.tagName
        )

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.delete_session_title)
            .setMessage(
                getString(
                    R.string.delete_session_message,
                    Format.range(session.startAt, session.endAt),
                    tag,
                    Format.minutes(ctx, session.minutes)
                )
            )
            .setNegativeButton(R.string.cancel) { _, _ ->
                Notifier.tap(ctx)
            }
            .setPositiveButton(R.string.delete_session_confirm) { _, _ ->
                if (Store.deleteSession(session.id)) {
                    Notifier.warning(ctx)
                    refresh()
                }
            }
            .show()
    }
}
