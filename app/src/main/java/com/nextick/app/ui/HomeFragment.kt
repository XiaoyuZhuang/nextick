package com.nextick.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextick.app.R
import com.nextick.app.core.Format
import com.nextick.app.core.Notifier
import com.nextick.app.core.TimerCore
import com.nextick.app.data.Store
import com.nextick.app.data.TagNames
import com.nextick.app.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val chips = ArrayList<Chip>()
    private var selectedTag: String? = null

    private val listener = object : TimerCore.Listener {
        override fun onTimerChanged() {
            if (_binding != null) render()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildDurations()
        binding.pointsHint.text = getString(R.string.points_rule)
        binding.btnDismiss.setOnClickListener {
            Notifier.tap(requireContext())
            TimerCore.dismissRing()
        }
        binding.btnFinish.setOnClickListener {
            Notifier.tap(requireContext())
            TimerCore.finishEarly()
        }
        binding.btnManageTags.setOnClickListener {
            Notifier.tap(requireContext())
            TagManager.show(this) { rebuildTags() }
        }
        binding.btnManageDurations.setOnClickListener {
            Notifier.tap(requireContext())
            DurationManager.show(this) { buildDurations() }
        }
    }

    override fun onResume() {
        super.onResume()
        TimerCore.addListener(listener)
        rebuildTags()
        buildDurations()
        render()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            rebuildTags()
            buildDurations()
            render()
        }
    }

    override fun onPause() {
        TimerCore.removeListener(listener)
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun rebuildTags() {
        if (_binding == null) return
        val ctx = requireContext()
        val tags = Store.tags()
        binding.tagGroup.removeAllViews()
        chips.clear()

        if (selectedTag == null || tags.none { it.id == selectedTag }) {
            selectedTag =
                if (tags.any { it.id == Store.lastTagId }) Store.lastTagId
                else tags.firstOrNull()?.id
        }

        tags.forEach { t ->
            val chip = Chip(ctx).apply {
                id = View.generateViewId()
                text = TagNames.of(ctx, t.nameKey, t.name)
                isCheckable = true
                isChecked = t.id == selectedTag
                chipIcon = circle(t.color)
                isChipIconVisible = true
                setOnClickListener {
                    selectedTag = t.id
                    Notifier.tap(ctx)
                    updateHint()
                }
            }
            binding.tagGroup.addView(chip)
            chips.add(chip)
        }
        updateHint()
    }

    private fun buildDurations() {
        if (_binding == null) return
        val ctx = requireContext()
        binding.durationRows.removeAllViews()
        val durations = Store.durations()
        val rows = durations.chunked(4)

        rows.forEach { group ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            binding.durationRows.addView(row)

            group.forEach { min ->
                val button = MaterialButton(
                    ctx,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = min.toString()
                    textSize = 20f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ctx.dp(52),
                        1f
                    ).apply {
                        setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
                    }
                    setOnClickListener {
                        val sel = selectedTag
                        if (sel == null) {
                            binding.hint.text = getString(R.string.hint_pick_tag_first)
                            return@setOnClickListener
                        }
                        Notifier.tap(ctx)
                        confirmAndStart(sel, min)
                    }
                }
                row.addView(button)
            }

            repeat(4 - group.size) {
                val spacer = Space(ctx)
                row.addView(
                    spacer,
                    LinearLayout.LayoutParams(0, ctx.dp(52), 1f).apply {
                        setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
                    }
                )
            }
        }
    }

    private fun confirmAndStart(tagId: String, minutes: Int) {
        val pending = Store.lastUnsettled()
        if (pending != null && pending.tagId == tagId) {
            val points = Format.total(pending.previewScore())
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.same_task_dialog_title)
                .setMessage(getString(R.string.same_task_dialog_message, points))
                .setNegativeButton(R.string.switch_task, null)
                .setPositiveButton(R.string.continue_and_deduct) { _, _ ->
                    TimerCore.startSession(tagId, minutes)
                }
                .show()
        } else {
            TimerCore.startSession(tagId, minutes)
        }
    }

    private fun render() {
        if (_binding == null) return
        val ctx = requireContext()
        binding.pointsValue.text = Format.total(Store.pointsFor(Store.todayKey()))

        when (TimerCore.phase) {
            TimerCore.Phase.RUNNING -> {
                val remain = TimerCore.remainingMillis
                val totalMs = TimerCore.plannedSeconds * 1000L
                binding.timerTag.text = TimerCore.tagName
                binding.timerText.text = Format.clock(remain)
                binding.progress.progress =
                    if (totalMs > 0) ((totalMs - remain) * 100 / totalMs).toInt() else 0
                binding.timerState.text = getString(
                    R.string.state_running,
                    Format.minutes(ctx, TimerCore.plannedSeconds / 60)
                )
                binding.btnFinish.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
            }

            TimerCore.Phase.RINGING -> {
                binding.timerTag.text = TimerCore.tagName
                binding.timerText.text = Format.clock(0)
                binding.progress.progress = 100
                binding.timerState.text = getString(R.string.state_ringing)
                binding.btnFinish.visibility = View.GONE
                binding.btnDismiss.visibility = View.VISIBLE
            }

            TimerCore.Phase.IDLE -> {
                val tag = selectedTag?.let { Store.tag(it) }
                binding.timerTag.text =
                    tag?.let { TagNames.of(ctx, it.nameKey, it.name) } ?: "—"
                binding.timerText.text = "--:--"
                binding.progress.progress = 0
                binding.timerState.text = getString(R.string.state_idle)
                binding.btnFinish.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
            }
        }
        updateHint()
    }

    private fun updateHint() {
        if (_binding == null) return
        val pending = Store.lastUnsettled()
        val sel = selectedTag
        binding.hint.text =
            if (pending != null && sel != null && pending.tagId == sel) {
                getString(R.string.hint_same_tag, Format.total(pending.previewScore()))
            } else {
                getString(R.string.hint_two_taps)
            }
    }
}
