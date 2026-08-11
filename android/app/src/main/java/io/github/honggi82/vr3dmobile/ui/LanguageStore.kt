package io.github.honggi82.vr3dmobile.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageStore {
    private const val PREFERENCES = "vr3d_settings"
    private const val KEY_LANGUAGE = "language"

    fun wrap(base: Context): Context {
        val saved = base.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null) ?: return base
        val locale = Locale.forLanguageTag(saved)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    fun toggle(context: Context) {
        val current = context.resources.configuration.locales[0].language
        val next = if (current == Locale.KOREAN.language) "en" else "ko"
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, next).apply()
    }
}
