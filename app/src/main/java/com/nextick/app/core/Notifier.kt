package com.nextick.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
    private const val CH_RING_ALERT = "nextick_ring_alert_v2"
    private const val CH_RING_SOUND = "nextick_ring_sound_v2"
    private const val CH_NUDGE_ALERT = "nextick_nudge_alert_v2"
    private const val CH_NUDGE_SOUND = "nextick_nudge_sound_v2"

    const val ID_ONGOING = 4100
    const val ID_RING = 4101
    const val ID_NUDGE = 4102

    @Volatile
    var ongoingActive = false

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val loc = ctx.localised()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        listOf("nextick_ring_haptic", "nextick_ring_silent", "nextick_nudge").forEach {
            runCatching { nm.deleteNotificationChannel(it) }
        }

        nm.createNotificationChannel(
            NotificationChannel(
                CH_ONGOING,
                loc.getString(R.string.channel_ongoing),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CH_RING_ALERT,
                loc.getString(R.string.channel_ring),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(sound, audio)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CH_RING_SOUND,
                loc.getString(R.string.channel_ring),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(sound, audio)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CH_NUDGE_ALERT,
                loc.getString(R.string.channel_nudge),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(sound, audio)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 100, 180)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CH_NUDGE_SOUND,
                loc.getString(R.string.channel_nudge),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(sound, audio)
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
        if (!Store.remindersEnabled) return
        val loc = ctx.localised()
        val channel = if (Store.vibration) CH_RING_ALERT else CH_RING_SOUND
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(loc.getString(R.string.notif_ring_title))
            .setContentText(loc.getString(R.string.notif_ring_body))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .addAction(
                R.drawable.ic_clock,
                loc.getString(R.string.action_dismiss),
                openApp(ctx)
            )
        if (Store.vibration) {
            b.setVibrate(longArrayOf(0, 250, 120, 250))
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_RING, b.build()) }
    }

    fun nudge(ctx: Context) {
        if (!Store.remindersEnabled) return
        val loc = ctx.localised()
        val channel = if (Store.vibration) CH_NUDGE_ALERT else CH_NUDGE_SOUND
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(loc.getString(R.string.notif_nudge_title))
            .setContentText(loc.getString(R.string.notif_nudge_body))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(false)
        if (Store.vibration) {
            b.setVibrate(longArrayOf(0, 180, 100, 180))
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_NUDGE, b.build()) }
    }

    fun cancelRing(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_RING)
    }

    fun cancelNudge(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_NUDGE)
    }

    fun tap(ctx: Context) = vibrate(ctx, 18)
    fun confirm(ctx: Context) = vibrate(ctx, 38)
    fun warning(ctx: Context) = vibrate(ctx, 72)

    private fun vibrate(ctx: Context, ms: Long) {
        if (!Store.vibration) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
