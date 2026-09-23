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
import kotlin.math.min

class PieChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    data class Slice(val label: String, val minutes: Int, val color: Long)

    var slices: List<Slice> = emptyList(); set(value) { field = value; invalidate() }
    var centerPrimary: String = ""; set(value) { field = value; invalidate() }
    var centerSecondary: String = ""; set(value) { field = value; invalidate() }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); val size = min(w, h) * 0.84f
        val stroke = size * 0.15f; val radius = (size - stroke) / 2f; val cx = w / 2f; val cy = h / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        ringPaint.strokeWidth = stroke; ringPaint.color = ContextCompat.getColor(context, R.color.outline)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        val total = slices.sumOf { it.minutes }
        if (total > 0) {
            var start = -90f
            slices.forEach { s ->
                val sweep = 360f * s.minutes / total; ringPaint.color = s.color.toInt()
                canvas.drawArc(oval, start, sweep, false, ringPaint); start += sweep
            }
        }
        titlePaint.textSize = size * 0.16f; titlePaint.color = ContextCompat.getColor(context, R.color.text_primary)
        val fm = titlePaint.fontMetrics
        canvas.drawText(centerPrimary, cx, cy - (fm.ascent + fm.descent) / 2f, titlePaint)
        subPaint.textSize = size * 0.07f; subPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
        canvas.drawText(centerSecondary, cx, cy + size * 0.15f, subPaint)
    }
}
