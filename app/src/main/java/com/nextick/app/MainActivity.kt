package com.nextick.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nextick.app.core.AppVisibility
import com.nextick.app.core.LocaleHelper
import com.nextick.app.core.TimerCore
import com.nextick.app.data.Store
import com.nextick.app.service.TimerService
import com.nextick.app.ui.HomeFragment
import com.nextick.app.ui.SettingsFragment
import com.nextick.app.ui.StatsFragment

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Store.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            val home = HomeFragment()
            val stats = StatsFragment()
            val settings = SettingsFragment()
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.container, home, TAG_HOME)
                .add(R.id.container, stats, TAG_STATS)
                .add(R.id.container, settings, TAG_SETTINGS)
                .hide(stats)
                .hide(settings)
                .commit()
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        requestNotificationPermission()
    }

    private fun showTab(itemId: Int) {
        val tag = when (itemId) {
            R.id.tab_stats -> TAG_STATS
            R.id.tab_settings -> TAG_SETTINGS
            else -> TAG_HOME
        }
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        listOf(TAG_HOME, TAG_STATS, TAG_SETTINGS).forEach { name ->
            val f = fm.findFragmentByTag(name) ?: return@forEach
            if (name == tag) tx.show(f) else tx.hide(f)
        }
        tx.commit()
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
        TimerCore.init(applicationContext)
        TimerService.start(this)
        // 打开软件 = 取消到点提醒
        if (TimerCore.phase == TimerCore.Phase.RINGING) TimerCore.dismissRing()
    }

    override fun onStop() {
        AppVisibility.visible = false
        super.onStop()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
    }

    companion object {
        private const val TAG_HOME = "home"
        private const val TAG_STATS = "stats"
        private const val TAG_SETTINGS = "settings"
    }
}
