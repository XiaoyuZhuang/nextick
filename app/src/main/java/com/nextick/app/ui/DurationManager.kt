package com.nextick.app.ui

import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextick.app.R
import com.nextick.app.data.Store

object DurationManager {
    fun show(fragment: Fragment, onSaved: () -> Unit) {
        val ctx = fragment.requireContext()
        val values = Store.durations().toMutableList()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), 0)
        }

        fun refresh() {
            container.removeAllViews()
            values.sorted().forEach { minutes ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, ctx.dp(4), 0, ctx.dp(4))
                }
                val value = TextView(ctx).apply {
                    text = ctx.getString(R.string.duration_m, minutes)
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                }
                val remove = TextView(ctx).apply {
                    text = ctx.getString(R.string.delete)
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.negative))
                    setPadding(ctx.dp(16), ctx.dp(10), 0, ctx.dp(10))
                    setOnClickListener {
                        values.remove(minutes)
                        refresh()
                    }
                }
                row.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(remove)
                container.addView(row)
            }

            val add = MaterialButton(
                ctx,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = ctx.getString(R.string.add_duration)
                setOnClickListener { showAddDialog(fragment, values) { refresh() } }
            }
            container.addView(
                add,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dp(8) }
            )
        }

        refresh()
        val scroll = ScrollView(ctx).apply { addView(container) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.manage_durations_title)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                if (values.isEmpty()) {
                    Toast.makeText(ctx, R.string.duration_keep_one, Toast.LENGTH_SHORT).show()
                } else {
                    Store.saveDurations(values)
                    onSaved()
                }
            }
            .show()
    }

    private fun showAddDialog(
        fragment: Fragment,
        values: MutableList<Int>,
        onChanged: () -> Unit
    ) {
        val ctx = fragment.requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = ctx.getString(R.string.duration_minutes_hint)
            setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), ctx.dp(8))
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.add_duration)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null && value in 1..999 && value !in values) {
                    values.add(value)
                    onChanged()
                }
            }
            .show()
    }
}
