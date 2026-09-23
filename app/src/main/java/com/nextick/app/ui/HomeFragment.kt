package com.nextick.app.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
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
import com.nextick.app.service.TimerService

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val chips = ArrayList<Chip>()
    private val durationButtons = ArrayList<MaterialButton>()
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

        binding.btnDismiss.setOnClickListener {
            Notifier.confirm(requireContext())
            TimerCore.dismissRing()
        }

        binding.btnFinish.setOnClickListener {
            Notifier.confirm(requireContext())
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

        binding.tagRows.removeAllViews()
        chips.clear()

        if (selectedTag == null || tags.none { it.id == selectedTag }) {
            selectedTag =
                if (tags.any { it.id == Store.lastTagId }) Store.lastTagId
                else tags.firstOrNull()?.id
        }

        tags.chunked(4).forEach { group ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            binding.tagRows.addView(row)

            group.forEach { tag ->
                val chip = Chip(ctx).apply {
                    id = View.generateViewId()
                    text = TagNames.of(ctx, tag.nameKey, tag.name)
                    isCheckable = true
                    isChecked = tag.id == selectedTag
                    isChipIconVisible = true
                    chipIcon = circle(tag.color)

                    // Keep the color marker deliberately small so a four-column
                    // layout still has enough room for the tag text.
                    chipIconSize = ctx.dp(14).toFloat()
                    chipStartPadding = ctx.dp(6).toFloat()
                    iconStartPadding = 0f
                    iconEndPadding = ctx.dp(4).toFloat()
                    textStartPadding = 0f
                    textEndPadding = 0f
                    chipEndPadding = ctx.dp(6).toFloat()
                    chipMinHeight = ctx.dp(42).toFloat()

                    setTextColor(
                        ContextCompat.getColor(ctx, R.color.text_primary)
                    )
                    textSize = 14f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END

                    setOnClickListener {
                        selectedTag = tag.id
                        chips.forEach { it.isChecked = it === this }
                        Notifier.tap(ctx)
                    }
                }

                row.addView(
                    chip,
                    LinearLayout.LayoutParams(0, ctx.dp(50), 1f).apply {
                        setMargins(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
                    }
                )
                chips.add(chip)
            }

            repeat(4 - group.size) {
                row.addView(
                    Space(ctx),
                    LinearLayout.LayoutParams(0, ctx.dp(50), 1f).apply {
                        setMargins(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
                    }
                )
            }
        }
    }

    private fun buildDurations() {
        if (_binding == null) return
        val ctx = requireContext()

        binding.durationRows.removeAllViews()
        durationButtons.clear()

        Store.durations().chunked(4).forEach { group ->
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
                    isEnabled = TimerCore.phase != TimerCore.Phase.RUNNING
                    setOnClickListener {
                        val sel = selectedTag
                        if (TimerCore.phase == TimerCore.Phase.RUNNING) {
                            Notifier.warning(ctx)
                            return@setOnClickListener
                        }
                        if (sel == null) {
                            Notifier.warning(ctx)
                            return@setOnClickListener
                        }
                        Notifier.tap(ctx)
                        confirmAndStart(sel, min)
                    }
                }
                row.addView(
                    button,
                    LinearLayout.LayoutParams(0, ctx.dp(52), 1f).apply {
                        setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
                    }
                )
                durationButtons.add(button)
            }

            repeat(4 - group.size) {
                row.addView(
                    Space(ctx),
                    LinearLayout.LayoutParams(0, ctx.dp(52), 1f).apply {
                        setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
                    }
                )
            }
        }
    }

    private fun confirmAndStart(tagId: String, minutes: Int) {
        val ctx = requireContext()
        if (TimerCore.phase == TimerCore.Phase.RUNNING) {
            Notifier.warning(ctx)
            return
        }

        val pending = Store.lastUnsettled()

        if (pending != null && pending.tagId == tagId) {
            Notifier.warning(ctx)
            val points = Format.total(pending.previewScore())
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.same_task_dialog_title)
                .setMessage(getString(R.string.same_task_dialog_message, points))
                .setNegativeButton(R.string.switch_task) { _, _ ->
                    Notifier.tap(ctx)
                }
                .setPositiveButton(R.string.continue_and_deduct) { _, _ ->
                    Notifier.confirm(ctx)
                    TimerCore.startSession(tagId, minutes)
                    TimerService.sync(ctx)
                }
                .show()
        } else {
            Notifier.confirm(ctx)
            TimerCore.startSession(tagId, minutes)
            TimerService.sync(ctx)
        }
    }

    private fun render() {
        if (_binding == null) return
        val ctx = requireContext()
        binding.pointsValue.text = Format.total(Store.totalPoints())

        val running = TimerCore.phase == TimerCore.Phase.RUNNING
        durationButtons.forEach { it.isEnabled = !running }

        when (TimerCore.phase) {
            TimerCore.Phase.RUNNING -> {
                val remain = TimerCore.remainingMillis
                val totalMs = TimerCore.plannedSeconds * 1000L
                binding.timerTag.text = TimerCore.tagName
                binding.timerText.text = Format.clock(remain)
                binding.progress.progress =
                    if (totalMs > 0) ((totalMs - remain) * 100 / totalMs).toInt() else 0
                binding.timerState.visibility = View.VISIBLE
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
                binding.timerState.visibility = View.VISIBLE
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
                binding.timerState.visibility = View.GONE
                binding.btnFinish.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
            }
        }
    }
}
