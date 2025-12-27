package com.example.restock_pg_dispositivo_moveis

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object ThemeUtils {

    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_LOCALE = "selected_locale"

    private const val THEME_LIGHT = 0
    private const val THEME_DARK = 1
    private const val THEME_DEFAULT = 2

    fun applySavedThemeAndLocale(context: Context) {
        applySavedTheme(context)
        applySavedLocale(context)
    }

    private fun applySavedTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt(KEY_THEME, THEME_DEFAULT)
        applyTheme(savedTheme)
    }

    fun applyAndSaveTheme(context: Context, theme: Int) {
        applyTheme(theme)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_THEME, theme).apply()
    }

    private fun applyTheme(theme: Int) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_DEFAULT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun applySavedLocale(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLocale = prefs.getString(KEY_LOCALE, "system")
        applyLocale(savedLocale ?: "system")
    }

    fun applyAndSaveLocale(context: Context, locale: String) {
        applyLocale(locale)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOCALE, locale).apply()
    }

    private fun applyLocale(locale: String) {
        val localeList = when (locale) {
            "pt" -> LocaleListCompat.forLanguageTags("pt-PT")
            "en" -> LocaleListCompat.forLanguageTags("en-US")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
