package com.nextick.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.nextick.app.core.AlarmPlanner
import com.nextick.app.core.Notifier
import com.nextick.app.core.TimerCore
import com.nextick.app.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ThemeMode.apply(Store.darkMode)
        TimerCore.init(this)
        Notifier.ensureChannels(this)
        AlarmPlanner.planNext(this)
    }
}

object ThemeMode {
    fun apply(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "day" -> AppCompatDelegate.MODE_NIGHT_NO
                "night" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
