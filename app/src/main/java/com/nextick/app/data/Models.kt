package com.nextick.app.data

import android.content.Context
import com.nextick.app.R
import kotlin.math.roundToInt

data class Tag(val id: String, var name: String, var nameKey: String?, var color: Long)

data class Session(
    val id: String,
    val tagId: String,
    val tagKey: String?,
    val tagName: String,
    val tagColor: Long,
    val plannedMinutes: Int,
    val startAt: Long,
    val endAt: Long,
    val completed: Boolean,
    var points: Double? = null,
    var switched: Boolean? = null
) {
    val minutes: Int
        get() = ((endAt - startAt) / 60000L).toInt().coerceAtLeast(1)

    fun previewScore(): Double = (minutes / 10.0 * 10).roundToInt() / 10.0
}

object TagNames {
    private val map = mapOf(
        "study" to R.string.tag_study,
        "work" to R.string.tag_work,
        "daily" to R.string.tag_daily,
        "fun" to R.string.tag_fun
    )

    fun of(context: Context, key: String?, fallback: String): String {
        val res = key?.let { map[it] } ?: return fallback
        return context.getString(res)
    }

    fun isDefaultName(context: Context, key: String?, name: String): Boolean =
        key != null && map[key] != null && name == context.getString(map[key]!!)
}

val TAG_PALETTE = longArrayOf(
    // Keep the original colors first so existing tags preserve their appearance.
    0xFFEF476F, 0xFFF4A259, 0xFFFFC145, 0xFF2FBF71,
    0xFF118AB2, 0xFF7C5CFF, 0xFF00B8A9, 0xFF8D6E63,

    // Extended palette for unique tag colors.
    0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF03A9F4,
    0xFF009688, 0xFF8BC34A, 0xFFCDDC39, 0xFFFF9800,
    0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFF4CAF50,
    0xFF2196F3, 0xFF673AB7, 0xFFFFC107, 0xFF00BCD4,
    0xFF264653, 0xFFE76F51, 0xFFA7C957, 0xFF6A4C93,
    0xFFFF6B6B, 0xFF4D908E, 0xFFF9844A, 0xFF577590
)
