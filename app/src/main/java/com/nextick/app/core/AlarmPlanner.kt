package com.nextick.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nextick.app.service.AlarmReceiver

object AlarmPlanner {
    private const val REQ = 8123

    fun planNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pending(ctx)
        val now = System.currentTimeMillis()
        val at = when (TimerCore.phase) {
            TimerCore.Phase.RUNNING -> TimerCore.endAt
            TimerCore.Phase.RINGING -> (TimerCore.lastPingAt + 10_000L).coerceAtLeast(now + 2_000L)
            TimerCore.Phase.IDLE -> (TimerCore.lastPingAt + 60_000L).coerceAtLeast(now + 2_000L)
        }
        am.cancel(pi)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ,
            Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
