package com.nextick.app.core

import android.content.Context
import com.nextick.app.data.Session
import com.nextick.app.data.Store
import com.nextick.app.data.TagNames
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

object TimerCore {
    enum class Phase { IDLE, RUNNING, RINGING }

    interface Listener { fun onTimerChanged() }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private var app: Context? = null

    var phase: Phase = Phase.IDLE; private set
    var tagId: String = ""; private set
    var tagName: String = ""; private set
    var tagKey: String? = null; private set
    var tagColor: Long = 0L; private set
    var plannedSeconds: Int = 0; private set
    var startedAt: Long = 0L; private set
    var endAt: Long = 0L; private set
    var phaseStartedAt: Long = 0L; private set
    var lastPingAt: Long = 0L; private set

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        Store.init(app!!)
        restore()
    }

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    val remainingMillis: Long
        get() = if (phase == Phase.RUNNING) {
            (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        } else 0L

    val elapsedMillis: Long
        get() = if (phase == Phase.RUNNING) plannedSeconds * 1000L - remainingMillis else 0L

    fun startSession(tagId: String, minutes: Int) {
        val ctx = app ?: return

        // A running countdown must never be replaced by an accidental second tap.
        // To change tasks intentionally, finish the current session first.
        if (phase == Phase.RUNNING) return

        val tag = Store.tag(tagId) ?: return
        settle(tagId)

        val now = System.currentTimeMillis()
        this.tagId = tag.id
        this.tagKey = tag.nameKey
        this.tagName = TagNames.of(ctx, tag.nameKey, tag.name)
        this.tagColor = tag.color
        this.plannedSeconds = minutes * 60
        this.startedAt = now
        this.endAt = now + plannedSeconds * 1000L
        this.phase = Phase.RUNNING
        this.phaseStartedAt = now
        this.lastPingAt = 0L

        Store.lastTagId = tag.id
        Notifier.cancelRing(ctx)
        Notifier.cancelNudge(ctx)
        commit(ctx)
    }

    fun dismissRing() {
        val ctx = app ?: return
        if (phase != Phase.RINGING) return
        val now = System.currentTimeMillis()
        phase = Phase.IDLE
        phaseStartedAt = now
        lastPingAt = now
        Notifier.cancelRing(ctx)
        commit(ctx)
    }

    fun finishEarly() {
        val ctx = app ?: return
        if (phase != Phase.RUNNING) return
        record(completed = false)
        val now = System.currentTimeMillis()
        phase = Phase.IDLE
        phaseStartedAt = now
        lastPingAt = now
        commit(ctx)
    }

    fun reminderPolicyChanged(enabled: Boolean) {
        val ctx = app ?: return
        val now = System.currentTimeMillis()
        if (!enabled) {
            if (phase == Phase.RINGING) phase = Phase.IDLE
            Notifier.cancelRing(ctx)
            Notifier.cancelNudge(ctx)
        }
        if (phase != Phase.RUNNING) {
            phaseStartedAt = now
            lastPingAt = now
        }
        commit(ctx)
    }

    fun tick() {
        val ctx = app ?: return
        val now = System.currentTimeMillis()
        var dirty = false

        if (!Store.remindersEnabled) {
            when (phase) {
                Phase.RUNNING -> if (now >= endAt) {
                    record(completed = true)
                    phase = Phase.IDLE
                    phaseStartedAt = now
                    lastPingAt = now
                    dirty = true
                }
                Phase.RINGING -> {
                    phase = Phase.IDLE
                    phaseStartedAt = now
                    lastPingAt = now
                    Notifier.cancelRing(ctx)
                    dirty = true
                }
                Phase.IDLE -> Unit
            }
            if (dirty) commit(ctx) else fire()
            return
        }

        when (phase) {
            Phase.RUNNING -> if (now >= endAt) {
                record(completed = true)
                phase = Phase.RINGING
                phaseStartedAt = now
                lastPingAt = now
                Notifier.ring(ctx)
                dirty = true
            }
            Phase.RINGING -> if (now - lastPingAt >= Store.ringIntervalSeconds * 1000L) {
                lastPingAt = now
                Notifier.ring(ctx)
                dirty = true
            }
            Phase.IDLE -> if (now - lastPingAt >= Store.nudgeIntervalSeconds * 1000L) {
                lastPingAt = now
                Notifier.nudge(ctx)
                dirty = true
            }
        }

        if (dirty) commit(ctx) else fire()
    }

    private fun record(completed: Boolean) {
        val now = System.currentTimeMillis()
        val end = if (completed) endAt else now
        Store.addSession(
            Session(
                id = UUID.randomUUID().toString(),
                tagId = tagId,
                tagKey = tagKey,
                tagName = tagName,
                tagColor = tagColor,
                plannedMinutes = plannedSeconds / 60,
                startAt = startedAt,
                endAt = end.coerceAtLeast(startedAt + 1000L),
                completed = completed
            )
        )
    }

    private fun settle(newTagId: String) {
        val pending = Store.sessions().filter { it.points == null }
        if (pending.isEmpty()) return
        pending.dropLast(1).forEach { Store.settle(it.id, 0.0, false) }
        val last = pending.last()
        val switched = last.tagId != newTagId
        val base = last.previewScore()
        val delta = if (switched) base else -base
        Store.settle(last.id, delta, switched)
        Store.addPoints(Store.dayOf(last.endAt), delta)
    }

    private fun commit(ctx: Context) {
        persist()
        AlarmPlanner.planNext(ctx)
        Notifier.updateOngoing(ctx)
        fire()
    }

    private fun fire() {
        listeners.forEach { it.onTimerChanged() }
    }

    private fun persist() {
        val o = JSONObject()
        o.put("phase", phase.name)
        o.put("tagId", tagId)
        o.put("tagName", tagName)
        o.put("tagKey", tagKey ?: "")
        o.put("tagColor", tagColor)
        o.put("plannedSeconds", plannedSeconds)
        o.put("startedAt", startedAt)
        o.put("endAt", endAt)
        o.put("phaseStartedAt", phaseStartedAt)
        o.put("lastPingAt", lastPingAt)
        Store.saveTimerState(o.toString())
    }

    private fun restore() {
        val raw = Store.timerState() ?: run {
            lastPingAt = System.currentTimeMillis()
            return
        }
        runCatching {
            val o = JSONObject(raw)
            phase = Phase.valueOf(o.optString("phase", Phase.IDLE.name))
            tagId = o.optString("tagId")
            tagName = o.optString("tagName")
            tagKey = o.optString("tagKey").ifEmpty { null }
            tagColor = o.optLong("tagColor")
            plannedSeconds = o.optInt("plannedSeconds")
            startedAt = o.optLong("startedAt")
            endAt = o.optLong("endAt")
            phaseStartedAt = o.optLong("phaseStartedAt")
            lastPingAt = o.optLong("lastPingAt")
        }
        if (phase == Phase.IDLE && lastPingAt == 0L) {
            lastPingAt = System.currentTimeMillis()
        }
    }
}
