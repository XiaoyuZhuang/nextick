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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val selectedDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

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

        binding.btnPrevDay.setOnClickListener {
            Notifier.tap(requireContext())
            selectedDay.add(Calendar.DAY_OF_YEAR, -1)
            refresh()
        }

        binding.btnNextDay.setOnClickListener {
            Notifier.tap(requireContext())
            selectedDay.add(Calendar.DAY_OF_YEAR, 1)
            refresh()
        }

        binding.dateLabel.setOnClickListener {
            Notifier.confirm(requireContext())
            goToday()
            refresh()
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

    private fun goToday() {
        val now = Calendar.getInstance()
        selectedDay.set(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0
        )
        selectedDay.set(Calendar.MILLISECOND, 0)
    }

    private fun selectedKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(selectedDay.timeInMillis))

    private fun refresh() {
        if (_binding == null) return
        val ctx = requireContext()
        val key = selectedKey()

        binding.dateLabel.text = dayLabel()

        val sessions =
            Store.sessionsOfDay(key)
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

        val slices = groups.values.sortedByDescending { it.minutes }
        val total = slices.sumOf { it.minutes }

        binding.pie.slices = slices
        binding.pie.centerPrimary = Format.minutes(ctx, total)
        binding.pie.centerSecondary = relativeDayName()

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
            row.points.text = points?.let { Format.points(it) } ?: "-"

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

    private fun dayLabel(): String {
        val date = SimpleDateFormat("MM-dd", Locale.getDefault())
            .format(Date(selectedDay.timeInMillis))
        return date + " " + relativeDayName()
    }

    private fun relativeDayName(): String {
        val selected = dayNumber(selectedDay.timeInMillis)
        val today = dayNumber(System.currentTimeMillis())
        return when (selected - today) {
            -1L -> getString(R.string.stats_yesterday)
            0L -> getString(R.string.stats_today)
            1L -> getString(R.string.stats_tomorrow)
            else -> SimpleDateFormat("MM-dd", Locale.getDefault())
                .format(Date(selectedDay.timeInMillis))
        }
    }

    private fun dayNumber(time: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 86_400_000L
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
