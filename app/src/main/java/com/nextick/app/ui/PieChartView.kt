package com.nextick.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nextick.app.R
import com.nextick.app.core.Format
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Slice(
        val label: String,
        val minutes: Int,
        val color: Long
    )

    var slices: List<Slice> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var centerPrimary: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var centerSecondary: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private data class LabelEntry(
        val slice: Slice,
        val angle: Float,
        val right: Boolean,
        var y: Float
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f

        val radius = size * 0.245f
        val stroke = size * 0.125f
        val outer = radius + stroke / 2f

        val oval = RectF(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        ringPaint.strokeWidth = stroke
        ringPaint.color = ContextCompat.getColor(context, R.color.outline)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        val total = slices.sumOf { it.minutes }
        val labels = ArrayList<LabelEntry>()

        if (total > 0) {
            var start = -90f

            slices.forEach { slice ->
                val sweep = 360f * slice.minutes / total
                ringPaint.color = slice.color.toInt()
                canvas.drawArc(oval, start, sweep, false, ringPaint)

                val mid = start + sweep / 2f
                val rad = Math.toRadians(mid.toDouble())
                val right = cos(rad) >= 0
                labels.add(
                    LabelEntry(
                        slice = slice,
                        angle = mid,
                        right = right,
                        y = cy + sin(rad).toFloat() * (outer + dp(18f))
                    )
                )
                start += sweep
            }
        }

        titlePaint.textSize = size * 0.115f
        titlePaint.color = ContextCompat.getColor(context, R.color.text_primary)
        val fm = titlePaint.fontMetrics
        canvas.drawText(
            centerPrimary,
            cx,
            cy - (fm.ascent + fm.descent) / 2f,
            titlePaint
        )

        subPaint.textSize = size * 0.05f
        subPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
        canvas.drawText(
            centerSecondary,
            cx,
            cy + size * 0.105f,
            subPaint
        )

        drawLabels(canvas, labels, cx, cy, outer)
    }

    private fun drawLabels(
        canvas: Canvas,
        labels: List<LabelEntry>,
        cx: Float,
        cy: Float,
        outer: Float
    ) {
        val minY = dp(18f)
        val maxY = height - dp(14f)
        val gap = dp(23f)

        val left = labels.filter { !it.right }.sortedBy { it.y }.toMutableList()
        val right = labels.filter { it.right }.sortedBy { it.y }.toMutableList()

        adjust(left, minY, maxY, gap)
        adjust(right, minY, maxY, gap)

        (left + right).forEach { entry ->
            val rad = Math.toRadians(entry.angle.toDouble())
            val sx = cx + cos(rad).toFloat() * outer
            val sy = cy + sin(rad).toFloat() * outer

            val elbowX =
                cx + if (entry.right) outer + dp(14f) else -(outer + dp(14f))

            val text = buildString {
                append(entry.slice.label)
                append(" · ")
                append(Format.minutes(context, entry.slice.minutes))
            }

            labelPaint.color =
                ContextCompat.getColor(context, R.color.text_primary)

            if (entry.right) {
                labelPaint.textAlign = Paint.Align.RIGHT
            } else {
                labelPaint.textAlign = Paint.Align.LEFT
            }

            val labelX = if (entry.right) {
                width - dp(8f)
            } else {
                dp(8f)
            }

            val textWidth = labelPaint.measureText(text)
            val lineEndX = if (entry.right) {
                labelX - textWidth - dp(5f)
            } else {
                labelX + textWidth + dp(5f)
            }

            linePaint.color = entry.slice.color.toInt()
            canvas.drawLine(sx, sy, elbowX, entry.y, linePaint)
            canvas.drawLine(elbowX, entry.y, lineEndX, entry.y, linePaint)

            val baseline = entry.y - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(text, labelX, baseline, labelPaint)
        }
    }

    private fun adjust(
        entries: MutableList<LabelEntry>,
        minY: Float,
        maxY: Float,
        gap: Float
    ) {
        if (entries.isEmpty()) return

        entries[0].y = entries[0].y.coerceIn(minY, maxY)

        for (i in 1 until entries.size) {
            entries[i].y =
                maxOf(entries[i].y, entries[i - 1].y + gap)
        }

        if (entries.last().y > maxY) {
            entries.last().y = maxY
            for (i in entries.size - 2 downTo 0) {
                entries[i].y =
                    minOf(entries[i].y, entries[i + 1].y - gap)
            }
        }

        if (entries.first().y < minY) {
            val shift = minY - entries.first().y
            entries.forEach { it.y += shift }
        }
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
