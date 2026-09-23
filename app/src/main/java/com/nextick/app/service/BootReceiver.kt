package com.nextick.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nextick.app.core.AlarmPlanner
import com.nextick.app.core.TimerCore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TimerCore.init(context)
        TimerCore.tick()
        AlarmPlanner.planNext(context)
        runCatching { TimerService.start(context) }
    }
}
