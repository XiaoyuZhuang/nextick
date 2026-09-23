package com.nextick.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nextick.app.core.Notifier
import com.nextick.app.core.TimerCore

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            TimerCore.tick()
            Notifier.updateOngoing(this@TimerService)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        TimerCore.init(applicationContext)
        Notifier.ensureChannels(this)
        Notifier.ongoingActive = true
        startForeground(Notifier.ID_ONGOING, Notifier.buildOngoing(this))
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TimerCore.tick()
        Notifier.updateOngoing(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        Notifier.ongoingActive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TimerService::class.java))
        }
    }
}
