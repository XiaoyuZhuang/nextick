package com.nextick.app.ui

import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextick.app.R
import com.nextick.app.data.Store
import com.nextick.app.data.TAG_PALETTE
import com.nextick.app.data.Tag
import com.nextick.app.data.TagNames
import java.util.UUID

object TagManager {
    private class Row(val source: Tag?, val swatch: android.view.View, val name: EditText, var colorIndex: Int)

    fun show(fragment: Fragment, onSaved: () -> Unit) {
        val ctx = fragment.requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), 0)
        }
        val rows = ArrayList<Row>()

        fun paint(row: Row) { row.swatch.background = circle(TAG_PALETTE[row.colorIndex]) }

        fun addRow(source: Tag?, index: Int) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ctx.dp(4), 0, ctx.dp(4))
            }
            val swatch = android.view.View(ctx)
            val colorIndex = source?.let { t -> TAG_PALETTE.indexOfFirst { it == t.color } }
                ?.takeIf { it >= 0 } ?: (index % TAG_PALETTE.size)
            val name = EditText(ctx).apply {
                hint = ctx.getString(R.string.tag_name_hint)
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                setText(source?.let { TagNames.of(ctx, it.nameKey, it.name) } ?: ctx.getString(R.string.new_tag))
                setSelectAllOnFocus(true)
            }
            val remove = TextView(ctx).apply {
                text = ctx.getString(R.string.delete)
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.negative))
                setPadding(ctx.dp(10), ctx.dp(10), 0, ctx.dp(10))
            }
            val row = Row(source, swatch, name, colorIndex)
            paint(row)

            swatch.setOnClickListener {
                row.colorIndex = (row.colorIndex + 1) % TAG_PALETTE.size
                paint(row)
            }
            remove.setOnClickListener {
                container.removeView(rowLayout)
                rows.remove(row)
            }

            rowLayout.addView(swatch, LinearLayout.LayoutParams(ctx.dp(26), ctx.dp(26)))
            rowLayout.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dp(12)
            })
            rowLayout.addView(remove)
            container.addView(rowLayout)
            rows.add(row)
        }

        Store.tags().forEachIndexed { i, t -> addRow(t, i) }

        val addButton = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = ctx.getString(R.string.add_tag)
            setOnClickListener {
                if (rows.size >= 12) return@setOnClickListener
                addRow(null, rows.size)
            }
        }
        container.addView(addButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(8) })

        val scroll = ScrollView(ctx).apply { addView(container) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.manage_tags_title)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                save(ctx, rows)
                onSaved()
            }
            .show()
    }

    private fun save(ctx: android.content.Context, rows: List<Row>) {
        val result = ArrayList<Tag>()
        rows.forEach { row ->
            val name = row.name.text.toString().trim()
            if (name.isEmpty()) return@forEach
            val src = row.source
            val key = src?.nameKey?.takeIf { TagNames.isDefaultName(ctx, it, name) }
            result.add(Tag(
                id = src?.id ?: UUID.randomUUID().toString(),
                name = name,
                nameKey = key,
                color = TAG_PALETTE[row.colorIndex]
            ))
        }
        if (result.isEmpty()) {
            result.add(Tag(UUID.randomUUID().toString(), ctx.getString(R.string.new_tag), null, TAG_PALETTE[0]))
        }
        Store.saveTags(result)
    }
}
