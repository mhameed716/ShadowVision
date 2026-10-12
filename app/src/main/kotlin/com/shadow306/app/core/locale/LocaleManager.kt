package com.shadow306.app.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.shadow306.app.core.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Handles in-app language switching (Arabic / English / System).
 *
 * Uses AndroidX AppCompatDelegate for per-app locales (Android 13+)
 * and falls back to manual Configuration override for older OS.
 */
class LocaleManager(
    private val context: Context,
    private val prefs: AppPreferences
) {
    companion object {
        const val LANG_SYSTEM = "system"
        const val LANG_AR = "ar"
        const val LANG_EN = "en"
    }

    suspend fun currentLanguage(): String = prefs.languageFlow.first()

    suspend fun setLanguage(code: String) {
        prefs.setLanguage(code)
        applyLanguage(code)
    }

    fun applyLanguage(code: String) {
        when (code) {
            LANG_AR, LANG_EN -> {
                val localeList = LocaleListCompat.forLanguageTags(code)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
            LANG_SYSTEM -> {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }

    fun isRtl(code: String): Boolean {
        val locale = when (code) {
            LANG_AR -> Locale("ar")
            LANG_EN -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
        return isRtlLocale(locale)
    }

    private fun isRtlLocale(locale: Locale): Boolean {
        val dir = Character.getDirectionality(locale.displayName[0])
        // More reliable: use first char direction or known RTL langs
        return locale.language in setOf("ar", "fa", "he", "ur", "ps", "sd")
    }
}
