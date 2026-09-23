package com.nextick.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun circle(color: Long): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color.toInt())
}
