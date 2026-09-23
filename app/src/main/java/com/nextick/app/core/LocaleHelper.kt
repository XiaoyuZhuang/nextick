package com.nextick.app.core

import android.content.Context
import android.content.res.Configuration
import com.nextick.app.data.Store
import java.util.Locale

object LocaleHelper {
    fun wrap(context: Context, language: String): Context {
        if (language == "system") return context
        val locale = if (language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

/** 服务、通知等非 Activity 上下文也按设置取文案 */
fun Context.localised(): Context = LocaleHelper.wrap(this, Store.language)
