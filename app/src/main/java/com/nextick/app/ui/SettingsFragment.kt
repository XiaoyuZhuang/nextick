package com.nextick.app.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextick.app.BuildConfig
import com.nextick.app.R
import com.nextick.app.ThemeMode
import com.nextick.app.core.AlarmPlanner
import com.nextick.app.core.UpdateChecker
import com.nextick.app.data.Store
import com.nextick.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.versionText.text =
            getString(R.string.current_version, BuildConfig.VERSION_NAME)

        when (Store.darkMode) {
            "day" -> binding.rbModeDay.isChecked = true
            "night" -> binding.rbModeNight.isChecked = true
            else -> binding.rbModeSystem.isChecked = true
        }
        binding.groupDisplay.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbModeDay -> "day"
                R.id.rbModeNight -> "night"
                else -> "system"
            }
            if (mode != Store.darkMode) {
                Store.darkMode = mode
                ThemeMode.apply(mode)
            }
        }

        when (Store.language) {
            "zh" -> binding.rbLangZh.isChecked = true
            "en" -> binding.rbLangEn.isChecked = true
            else -> binding.rbLangSystem.isChecked = true
        }
        binding.groupLang.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.rbLangZh -> "zh"
                R.id.rbLangEn -> "en"
                else -> "system"
            }
            if (lang != Store.language) {
                Store.language = lang
                activity?.recreate()
            }
        }

        binding.switchVib.isChecked = Store.vibration
        binding.switchVib.setOnCheckedChangeListener { _, checked ->
            Store.vibration = checked
        }

        updateIntervalButtons()
        binding.btnRingInterval.setOnClickListener {
            editNumber(
                title = getString(R.string.ring_interval_title),
                message = getString(R.string.ring_interval_edit_hint),
                current = Store.ringIntervalSeconds,
                min = 5,
                max = 3600
            ) { value ->
                Store.ringIntervalSeconds = value
                updateIntervalButtons()
                AlarmPlanner.planNext(requireContext())
            }
        }
        binding.btnNudgeInterval.setOnClickListener {
            editNumber(
                title = getString(R.string.nudge_interval_title),
                message = getString(R.string.nudge_interval_edit_hint),
                current = Store.nudgeIntervalMinutes,
                min = 1,
                max = 1440
            ) { value ->
                Store.nudgeIntervalMinutes = value
                updateIntervalButtons()
                AlarmPlanner.planNext(requireContext())
            }
        }

        binding.btnCheckUpdate.setOnClickListener { checkUpdate() }
    }

    private fun updateIntervalButtons() {
        if (_binding == null) return
        binding.btnRingInterval.text =
            getString(R.string.seconds_value, Store.ringIntervalSeconds)
        binding.btnNudgeInterval.text =
            getString(R.string.minutes_value, Store.nudgeIntervalMinutes)
    }

    private fun editNumber(
        title: String,
        message: String,
        current: Int,
        min: Int,
        max: Int,
        onSaved: (Int) -> Unit
    ) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            selectAll()
            setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), ctx.dp(8))
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null) onSaved(value.coerceIn(min, max))
            }
            .show()
    }

    private fun checkUpdate() {
        binding.updateStatus.text = getString(R.string.update_checking)
        UpdateChecker.check(
            BuildConfig.GITHUB_REPO,
            BuildConfig.VERSION_NAME
        ) { result ->
            if (_binding == null) return@check
            result.onSuccess { info ->
                if (info.newer) {
                    binding.updateStatus.text = ""
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(getString(R.string.update_found, info.tag))
                        .setPositiveButton(R.string.update_open) { _, _ ->
                            startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(info.url)
                                )
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    binding.updateStatus.text = getString(R.string.update_none)
                }
            }.onFailure {
                binding.updateStatus.text = getString(R.string.update_error)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
