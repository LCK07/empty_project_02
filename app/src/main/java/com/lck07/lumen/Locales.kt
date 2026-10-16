package com.lck07.lumen

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app language: the device's, or English/German chosen in Settings. Android 13+ keeps the
 * choice itself (it also shows in the system's per-app language settings); older systems, like
 * Fire OS 8, get the chosen locale applied to the activity's context.
 */
object Locales {
    private const val PREFS = "lumen_locale"
    private const val KEY = "tag"

    /** The language tag chosen in the app, or null to follow the device. */
    fun chosen(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /** Applies a new choice; the activity is recreated so every screen picks it up. */
    fun choose(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (tag == null) remove(KEY) else putString(KEY, tag) }.apply()
        }
    }

    /** For Android 12 and older: the base context with the chosen locale. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = chosen(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale); setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
