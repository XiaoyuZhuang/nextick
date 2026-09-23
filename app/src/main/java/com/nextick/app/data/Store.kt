package com.nextick.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Store {
    private const val KEY_TAGS = "tags"
    private const val KEY_DURATIONS = "durations"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_POINTS = "points"
    private const val KEY_TIMER = "timer_state"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("nextick_data", Context.MODE_PRIVATE)
        if (!sp.contains(KEY_TAGS)) writeTags(defaultTags())
        if (!sp.contains(KEY_DURATIONS)) saveDurations(defaultDurations())
    }

    fun dayOf(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))

    fun todayKey(): String = dayOf(System.currentTimeMillis())

    private fun defaultTags(): List<Tag> = listOf(
        Tag(UUID.randomUUID().toString(), "", "study", 0xFF118AB2),
        Tag(UUID.randomUUID().toString(), "", "work", 0xFF2FBF71),
        Tag(UUID.randomUUID().toString(), "", "daily", 0xFF8D6E63),
        Tag(UUID.randomUUID().toString(), "", "fun", 0xFFEF476F)
    )

    fun tags(): MutableList<Tag> {
        val arr = JSONArray(sp.getString(KEY_TAGS, "[]"))
        val out = ArrayList<Tag>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Tag(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name"),
                    nameKey = o.optString("key").ifEmpty { null },
                    color = o.optLong("color", 0xFF8D6E63)
                )
            )
        }
        return out
    }

    fun tag(id: String): Tag? = tags().firstOrNull { it.id == id }

    fun saveTags(list: List<Tag>) = writeTags(list)

    private fun writeTags(list: List<Tag>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("key", t.nameKey ?: "")
                put("color", t.color)
            })
        }
        sp.edit().putString(KEY_TAGS, arr.toString()).apply()
    }

    private fun defaultDurations(): List<Int> = listOf(5, 10, 15, 20, 25, 30, 40)

    fun durations(): List<Int> {
        val raw = sp.getString(KEY_DURATIONS, null) ?: return defaultDurations()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { arr.optInt(it) }
                .filter { it > 0 }
                .distinct()
                .sorted()
        }.getOrDefault(defaultDurations())
    }

    fun saveDurations(values: List<Int>) {
        val clean = values.filter { it in 1..999 }.distinct().sorted()
        val arr = JSONArray()
        clean.forEach { arr.put(it) }
        sp.edit().putString(KEY_DURATIONS, arr.toString()).apply()
    }

    fun sessions(): MutableList<Session> {
        val arr = JSONArray(sp.getString(KEY_SESSIONS, "[]"))
        val out = ArrayList<Session>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val s = Session(
                id = o.optString("id"),
                tagId = o.optString("tagId"),
                tagKey = o.optString("tagKey", o.optString("key")).ifEmpty { null },
                tagName = o.optString("tagName", o.optString("name")),
                tagColor = if (o.has("tagColor")) o.optLong("tagColor") else o.optLong("color"),
                plannedMinutes = o.optInt("planned"),
                startAt = o.optLong("start"),
                endAt = o.optLong("end"),
                completed = o.optBoolean("completed")
            )
            if (o.optBoolean("scored", false)) {
                s.points = o.optDouble("points", 0.0)
                s.switched = o.optBoolean("switched", false)
            }
            out.add(s)
        }
        return out
    }

    fun addSession(s: Session) {
        val list = sessions()
        list.add(s)
        writeSessions(list)
    }

    fun settle(id: String, points: Double, switched: Boolean) {
        val list = sessions()
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            list[i].points = points
            list[i].switched = switched
            writeSessions(list)
        }
    }

    fun lastUnsettled(): Session? = sessions().lastOrNull { it.points == null }

    fun sessionsOfDay(day: String): List<Session> =
        sessions().filter { dayOf(it.endAt) == day }

    private fun writeSessions(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("tagId", s.tagId)
                put("tagKey", s.tagKey ?: "")
                put("tagName", s.tagName)
                put("tagColor", s.tagColor)
                put("planned", s.plannedMinutes)
                put("start", s.startAt)
                put("end", s.endAt)
                put("completed", s.completed)
                put("scored", s.points != null)
                put("points", s.points ?: 0.0)
                put("switched", s.switched ?: false)
            })
        }
        sp.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun pointsFor(day: String): Double =
        JSONObject(sp.getString(KEY_POINTS, "{}")).optDouble(day, 0.0)

    fun addPoints(day: String, delta: Double) {
        val o = JSONObject(sp.getString(KEY_POINTS, "{}"))
        o.put(day, o.optDouble(day, 0.0) + delta)
        sp.edit().putString(KEY_POINTS, o.toString()).apply()
    }

    var darkMode: String
        get() = sp.getString("dark", "system") ?: "system"
        set(value) = sp.edit().putString("dark", value).apply()

    var language: String
        get() = sp.getString("lang", "system") ?: "system"
        set(value) = sp.edit().putString("lang", value).apply()

    var vibration: Boolean
        get() = sp.getBoolean("vibration", true)
        set(value) = sp.edit().putBoolean("vibration", value).apply()

    var remindersEnabled: Boolean
        get() = sp.getBoolean("reminders_enabled", true)
        set(value) = sp.edit().putBoolean("reminders_enabled", value).apply()

    var ringIntervalSeconds: Int
        get() = sp.getInt("ring_interval_seconds", 10).coerceIn(5, 86400)
        set(value) = sp.edit().putInt("ring_interval_seconds", value.coerceIn(5, 86400)).apply()

    var nudgeIntervalSeconds: Int
        get() {
            if (sp.contains("nudge_interval_seconds")) {
                return sp.getInt("nudge_interval_seconds", 60).coerceIn(5, 86400)
            }
            val legacyMinutes = sp.getInt("nudge_interval_minutes", 1).coerceIn(1, 1440)
            return (legacyMinutes * 60).coerceIn(5, 86400)
        }
        set(value) = sp.edit().putInt("nudge_interval_seconds", value.coerceIn(5, 86400)).apply()

    var lastTagId: String
        get() = sp.getString("last_tag", "") ?: ""
        set(value) = sp.edit().putString("last_tag", value).apply()

    fun timerState(): String? = sp.getString(KEY_TIMER, null)

    fun saveTimerState(json: String) {
        sp.edit().putString(KEY_TIMER, json).apply()
    }
}
