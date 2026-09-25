package com.nextick.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nextick.app.data.Store
import com.nextick.app.service.AlarmReceiver

object AlarmPlanner {
    private const val REQ = 8123

    fun planNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pending(ctx)
        val now = System.currentTimeMillis()

        val at = when {
            TimerCore.phase == TimerCore.Phase.RUNNING -> {
                // Always wake at the end so the session is recorded even when reminders are muted.
                TimerCore.endAt
            }

            !Store.remindersEnabled -> {
                am.cancel(pi)
                return
            }

            !TimerCore.remindersEffectiveNow(now) -> {
                // Sleep outside the daily reminder window, then resume one nudge interval
                // after the next active window begins.
                Store.nextReminderWindowStartMillis(now) +
                    Store.nudgeIntervalSeconds * 1000L
            }

            TimerCore.phase == TimerCore.Phase.RINGING -> {
                (TimerCore.lastPingAt + Store.ringIntervalSeconds * 1000L)
                    .coerceAtLeast(now + 2_000L)
            }

            else -> {
                (TimerCore.lastPingAt + Store.nudgeIntervalSeconds * 1000L)
                    .coerceAtLeast(now + 2_000L)
            }
        }

        am.cancel(pi)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pending(ctx))
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            REQ,
            Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
