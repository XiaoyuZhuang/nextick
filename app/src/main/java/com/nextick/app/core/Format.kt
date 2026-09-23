package com.nextick.app.core

import android.content.Context
import com.nextick.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {
    fun clock(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
    }

    fun minutes(ctx: Context, mins: Int): String =
        if (mins >= 60) ctx.getString(R.string.duration_hm, mins / 60, mins % 60)
        else ctx.getString(R.string.duration_m, mins)

    fun points(value: Double): String {
        val sign = if (value > 0.001) "+" else if (value < -0.001) "\u2212" else ""
        return sign + String.format(Locale.getDefault(), "%.1f", abs(value))
    }

    fun total(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    fun clockTime(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    fun range(start: Long, end: Long): String = clockTime(start) + " - " + clockTime(end)
}
