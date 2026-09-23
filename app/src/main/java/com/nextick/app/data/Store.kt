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
        repairHistoricalScores()
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
        list.forEach { tag -> arr.put(tagToJson(tag)) }
        sp.edit().putString(KEY_TAGS, arr.toString()).apply()
    }

    private fun tagToJson(tag: Tag): JSONObject =
        JSONObject().apply {
            put("id", tag.id)
            put("name", tag.name)
            put("key", tag.nameKey ?: "")
            put("color", tag.color)
        }

    private fun defaultDurations(): List<Int> =
        listOf(5, 10, 15, 20, 25, 30, 40)

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
            val session = Session(
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
                session.points = o.optDouble("points", 0.0)
                session.switched = o.optBoolean("switched", false)
            }
            out.add(session)
        }
        return out
    }

    fun addSession(session: Session) {
        val list = sessions()
        list.add(session)
        writeSessions(list)
    }

    fun settleLatestForNextTag(nextTagId: String) {
        val list = sessions()
        if (list.isEmpty()) return

        var changed = repairHistoricalScoresInMemory(list)

        val last = list.last()
        if (last.points == null || kotlin.math.abs(last.points ?: 0.0) < 1e-9) {
            val switched = last.tagId != nextTagId
            val base = last.previewScore()
            last.points = if (switched) base else -base
            last.switched = switched
            changed = true
        }

        if (changed) {
            writeSessions(list)
            rebuildPointsFromSessions(list)
        }
    }

    private fun repairHistoricalScores() {
        val list = sessions()
        if (repairHistoricalScoresInMemory(list)) {
            writeSessions(list)
            rebuildPointsFromSessions(list)
        }
    }

    private fun repairHistoricalScoresInMemory(list: MutableList<Session>): Boolean {
        if (list.size < 2) return false
        var changed = false

        for (i in 0 until list.lastIndex) {
            val current = list[i]
            val next = list[i + 1]

            if (current.points == null || kotlin.math.abs(current.points ?: 0.0) < 1e-9) {
                val switched = current.tagId != next.tagId
                val base = current.previewScore()
                current.points = if (switched) base else -base
                current.switched = switched
                changed = true
            }
        }

        return changed
    }

    fun deleteSession(id: String): Boolean {
        val list = sessions()
        val removed = list.firstOrNull { it.id == id } ?: return false
        list.removeAll { it.id == id }

        // Deleting a record changes the transition between its neighbours.
        // Recompute every transition that has a recorded next session.
        for (i in 0 until list.lastIndex) {
            val current = list[i]
            val next = list[i + 1]
            val switched = current.tagId != next.tagId
            val base = current.previewScore()
            current.points = if (switched) base else -base
            current.switched = switched
        }

        writeSessions(list)
        rebuildPointsFromSessions(list)

        if (lastTagId == removed.tagId && list.none { it.tagId == removed.tagId }) {
            lastTagId = ""
        }
        return true
    }

    fun lastUnsettled(): Session? =
        sessions().lastOrNull { it.points == null }

    fun sessionsOfDay(day: String): List<Session> =
        sessions().filter { dayOf(it.endAt) == day }

    private fun writeSessions(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { session -> arr.put(sessionToJson(session)) }
        sp.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    private fun sessionToJson(session: Session): JSONObject =
        JSONObject().apply {
            put("id", session.id)
            put("tagId", session.tagId)
            put("tagKey", session.tagKey ?: "")
            put("tagName", session.tagName)
            put("tagColor", session.tagColor)
            put("planned", session.plannedMinutes)
            put("start", session.startAt)
            put("end", session.endAt)
            put("completed", session.completed)
            put("scored", session.points != null)
            put("points", session.points ?: 0.0)
            put("switched", session.switched ?: false)
        }

    private fun pointsObject(): JSONObject =
        JSONObject(sp.getString(KEY_POINTS, "{}"))

    fun pointsFor(day: String): Double =
        pointsObject().optDouble(day, 0.0)

    fun totalPoints(): Double {
        val o = pointsObject()
        var total = 0.0
        val keys = o.keys()
        while (keys.hasNext()) {
            total += o.optDouble(keys.next(), 0.0)
        }
        return total
    }

    fun addPoints(day: String, delta: Double) {
        val o = pointsObject()
        o.put(day, o.optDouble(day, 0.0) + delta)
        sp.edit().putString(KEY_POINTS, o.toString()).apply()
    }

    private fun rebuildPointsFromSessions(list: List<Session>) {
        val o = JSONObject()
        list.forEach { session ->
            val value = session.points ?: return@forEach
            val day = dayOf(session.endAt)
            o.put(day, o.optDouble(day, 0.0) + value)
        }
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
        set(value) = sp.edit()
            .putInt("ring_interval_seconds", value.coerceIn(5, 86400))
            .apply()

    var nudgeIntervalSeconds: Int
        get() {
            if (sp.contains("nudge_interval_seconds")) {
                return sp.getInt("nudge_interval_seconds", 60).coerceIn(5, 86400)
            }
            val legacyMinutes =
                sp.getInt("nudge_interval_minutes", 1).coerceIn(1, 1440)
            return (legacyMinutes * 60).coerceIn(5, 86400)
        }
        set(value) = sp.edit()
            .putInt("nudge_interval_seconds", value.coerceIn(5, 86400))
            .apply()

    var lastTagId: String
        get() = sp.getString("last_tag", "") ?: ""
        set(value) = sp.edit().putString("last_tag", value).apply()

    fun timerState(): String? =
        sp.getString(KEY_TIMER, null)

    fun saveTimerState(json: String) {
        sp.edit().putString(KEY_TIMER, json).apply()
    }

    fun exportAllJson(appVersion: String): String {
        val tagsArray = JSONArray()
        tags().forEach { tagsArray.put(tagToJson(it)) }

        val durationArray = JSONArray()
        durations().forEach { durationArray.put(it) }

        val sessionsArray = JSONArray()
        sessions().forEach { sessionsArray.put(sessionToJson(it)) }

        val timerJson: Any = timerState()?.let { raw ->
            runCatching { JSONObject(raw) }.getOrElse { raw }
        } ?: JSONObject.NULL

        val settings = JSONObject().apply {
            put("darkMode", darkMode)
            put("language", language)
            put("vibration", vibration)
            put("remindersEnabled", remindersEnabled)
            put("ringIntervalSeconds", ringIntervalSeconds)
            put("nudgeIntervalSeconds", nudgeIntervalSeconds)
            put("lastTagId", lastTagId)
        }

        return JSONObject().apply {
            put("format", "NextTick backup")
            put("schemaVersion", 1)
            put("appVersion", appVersion)
            put("exportedAt", System.currentTimeMillis())
            put("tags", tagsArray)
            put("durationsMinutes", durationArray)
            put("sessions", sessionsArray)
            put("pointsByDay", pointsObject())
            put("totalPoints", totalPoints())
            put("settings", settings)
            put("timerState", timerJson)
        }.toString(2)
    }
}
