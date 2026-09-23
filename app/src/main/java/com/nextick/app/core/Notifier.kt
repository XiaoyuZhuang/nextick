package com.nextick.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nextick.app.MainActivity
import com.nextick.app.R
import com.nextick.app.data.Store

object Notifier {
    private const val CH_ONGOING = "nextick_ongoing"
    private const val CH_RING_HAPTIC = "nextick_ring_haptic"
    private const val CH_RING_SILENT = "nextick_ring_silent"
    private const val CH_NUDGE = "nextick_nudge"

    const val ID_ONGOING = 4100
    const val ID_RING = 4101
    const val ID_NUDGE = 4102

    @Volatile
    var ongoingActive = false

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val loc = ctx.localised()

        nm.createNotificationChannel(
            NotificationChannel(
                CH_ONGOING, loc.getString(R.string.channel_ongoing),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_RING_HAPTIC, loc.getString(R.string.channel_ring),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 120, 80, 120)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_RING_SILENT, loc.getString(R.string.channel_ring),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_NUDGE, loc.getString(R.string.channel_nudge),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun buildOngoing(ctx: Context): Notification {
        val loc = ctx.localised()
        val text = if (TimerCore.phase == TimerCore.Phase.RUNNING) {
            loc.getString(
                R.string.notif_ongoing_running,
                TimerCore.tagName,
                Format.clock(TimerCore.remainingMillis)
            )
        } else {
            loc.getString(R.string.notif_ongoing_idle)
        }
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(loc.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateOngoing(ctx: Context) {
        if (!ongoingActive) return
        runCatching {
            NotificationManagerCompat.from(ctx).notify(ID_ONGOING, buildOngoing(ctx))
        }
    }

    fun ring(ctx: Context) {
        if (AppVisibility.visible) {
            pulse(ctx)
            return
        }
        val loc = ctx.localised()
        val channel = if (Store.vibration) CH_RING_HAPTIC else CH_RING_SILENT
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(loc.getString(R.string.notif_ring_title))
            .setContentText(loc.getString(R.string.notif_ring_body))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(false)
            .addAction(
                R.drawable.ic_clock,
                loc.getString(R.string.action_dismiss),
                openApp(ctx)
            )
        if (Store.vibration) b.setVibrate(longArrayOf(0, 120, 80, 120))
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_RING, b.build()) }
        pulse(ctx)
    }

    fun nudge(ctx: Context) {
        if (AppVisibility.visible) return
        val loc = ctx.localised()
        val b = NotificationCompat.Builder(ctx, CH_NUDGE)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(loc.getString(R.string.notif_nudge_title))
            .setContentText(loc.getString(R.string.notif_nudge_body))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(false)
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_NUDGE, b.build()) }
    }

    fun cancelRing(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_RING)
    }

    fun cancelNudge(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_NUDGE)
    }

    fun tap(ctx: Context) = vibrate(ctx, 18)

    fun pulse(ctx: Context) = vibrate(ctx, 90)

    private fun vibrate(ctx: Context, ms: Long) {
        if (!Store.vibration) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
