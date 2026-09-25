package com.nextick.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextick.app.BuildConfig
import com.nextick.app.R
import com.nextick.app.ThemeMode
import com.nextick.app.core.AlarmPlanner
import com.nextick.app.core.Notifier
import com.nextick.app.core.TimerCore
import com.nextick.app.core.UpdateChecker
import com.nextick.app.data.Store
import com.nextick.app.databinding.FragmentSettingsBinding
import com.nextick.app.service.TimerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val ctx = context ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult

            runCatching {
                val output = ctx.contentResolver.openOutputStream(uri)
                    ?: error("Cannot open export file")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(Store.exportAllJson(BuildConfig.VERSION_NAME))
                }
            }.onSuccess {
                Notifier.confirm(ctx)
                Toast.makeText(ctx, R.string.export_success, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Notifier.warning(ctx)
                Toast.makeText(ctx, R.string.export_error, Toast.LENGTH_SHORT).show()
            }
        }

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
        val ctx = requireContext()

        binding.versionText.text =
            getString(R.string.current_version, BuildConfig.VERSION_NAME)

        when (Store.darkMode) {
            "day" -> binding.rbModeDay.isChecked = true
            "night" -> binding.rbModeNight.isChecked = true
            else -> binding.rbModeSystem.isChecked = true
        }

        binding.groupDisplay.setOnCheckedChangeListener { _, checkedId ->
            Notifier.tap(ctx)
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
            Notifier.tap(ctx)
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
            if (checked) {
                Store.vibration = true
                Notifier.confirm(ctx)
            } else {
                Notifier.tap(ctx)
                Store.vibration = false
            }
        }

        binding.switchReminders.isChecked = Store.remindersEnabled
        binding.switchReminders.setOnCheckedChangeListener { _, checked ->
            Notifier.confirm(ctx)
            Store.remindersEnabled = checked
            TimerCore.reminderPolicyChanged(checked)
            if (!checked) {
                AlarmPlanner.cancel(ctx)
            } else {
                AlarmPlanner.planNext(ctx)
            }
            TimerService.sync(ctx)
        }

        updateReminderWindowButtons()
        binding.btnWindowStart.setOnClickListener {
            Notifier.tap(ctx)
            pickReminderTime(isStart = true)
        }
        binding.btnWindowEnd.setOnClickListener {
            Notifier.tap(ctx)
            pickReminderTime(isStart = false)
        }

        updateIntervalButtons()

        binding.btnRingInterval.setOnClickListener {
            Notifier.tap(ctx)
            editNumber(
                title = getString(R.string.ring_interval_title),
                message = getString(R.string.ring_interval_edit_hint),
                current = Store.ringIntervalSeconds,
                min = 5,
                max = 86400
            ) { value ->
                Store.ringIntervalSeconds = value
                updateIntervalButtons()
                AlarmPlanner.planNext(ctx)
            }
        }

        binding.btnNudgeInterval.setOnClickListener {
            Notifier.tap(ctx)
            editNumber(
                title = getString(R.string.nudge_interval_title),
                message = getString(R.string.nudge_interval_edit_hint),
                current = Store.nudgeIntervalSeconds,
                min = 5,
                max = 86400
            ) { value ->
                Store.nudgeIntervalSeconds = value
                updateIntervalButtons()
                AlarmPlanner.planNext(ctx)
            }
        }

        binding.btnExportData.setOnClickListener {
            Notifier.tap(ctx)
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
                .format(Date())
            exportLauncher.launch("NextTick-backup-$stamp.json")
        }

        binding.btnCheckUpdate.setOnClickListener {
            Notifier.tap(ctx)
            checkUpdate()
        }

        binding.btnOpenDownload.setOnClickListener {
            Notifier.tap(ctx)
            openDownloadPage()
        }
    }

    private fun pickReminderTime(isStart: Boolean) {
        val ctx = requireContext()
        val current =
            if (isStart) Store.reminderWindowStartMinutes
            else Store.reminderWindowEndMinutes

        TimePickerDialog(
            ctx,
            { _, hour, minute ->
                val value = hour * 60 + minute
                if (isStart) {
                    Store.reminderWindowStartMinutes = value
                } else {
                    Store.reminderWindowEndMinutes = value
                }

                Notifier.confirm(ctx)
                updateReminderWindowButtons()
                TimerCore.tick()
                AlarmPlanner.planNext(ctx)
                TimerService.sync(ctx)
            },
            current / 60,
            current % 60,
            true
        ).show()
    }

    private fun formatTime(minutes: Int): String =
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutes / 60,
            minutes % 60
        )

    private fun updateReminderWindowButtons() {
        if (_binding == null) return
        binding.btnWindowStart.text =
            formatTime(Store.reminderWindowStartMinutes)
        binding.btnWindowEnd.text =
            formatTime(Store.reminderWindowEndMinutes)
    }

    private fun openDownloadPage() {
        val url = UpdateChecker.releasesUrl(BuildConfig.GITHUB_REPO)
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }

    private fun updateIntervalButtons() {
        if (_binding == null) return
        binding.btnRingInterval.text =
            getString(R.string.seconds_value, Store.ringIntervalSeconds)
        binding.btnNudgeInterval.text =
            getString(R.string.seconds_value, Store.nudgeIntervalSeconds)
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
            .setNegativeButton(R.string.cancel) { _, _ ->
                Notifier.tap(ctx)
            }
            .setPositiveButton(R.string.done) { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null) {
                    Notifier.confirm(ctx)
                    onSaved(value.coerceIn(min, max))
                } else {
                    Notifier.warning(ctx)
                }
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
                            Notifier.confirm(requireContext())
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(info.url)
                                )
                            )
                        }
                        .setNegativeButton(R.string.cancel) { _, _ ->
                            Notifier.tap(requireContext())
                        }
                        .show()
                } else {
                    binding.updateStatus.text = getString(R.string.update_none)
                    Notifier.confirm(requireContext())
                }
            }.onFailure {
                binding.updateStatus.text = getString(R.string.update_error)
                Notifier.warning(requireContext())

                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.update_error_fallback)
                    .setPositiveButton(R.string.open_download_page) { _, _ ->
                        openDownloadPage()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
