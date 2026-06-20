package com.potato.liftinsight.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageContext {
    fun wrap(context: Context, languageMode: AppLanguageMode): Context {
        val languageTag = languageMode.languageTag ?: run {
            val systemLocale = context.resources.configuration.locales[0]
            Locale.setDefault(systemLocale)
            return context
        }
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
