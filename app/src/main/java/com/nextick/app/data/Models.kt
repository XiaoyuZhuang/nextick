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
    0xFFEF476F, 0xFFF4A259, 0xFFFFC145, 0xFF2FBF71,
    0xFF118AB2, 0xFF7C5CFF, 0xFF00B8A9, 0xFF8D6E63
)
