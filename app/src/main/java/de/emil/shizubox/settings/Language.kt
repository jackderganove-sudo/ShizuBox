package de.emil.shizubox.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** Per-app language (Android 13+), the same setting as Settings → Apps → ShizuBox → Language. */
object Language {
    /** Language tag to its own name; "" = follow the system. */
    val OPTIONS = listOf(
        "" to null,
        "de" to "Deutsch",
        "en" to "English",
        "tr" to "Türkçe",
        "es" to "Español",
        "fr" to "Français",
    )

    val supported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): String {
        if (!supported) return ""
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) "" else locales[0].language
    }

    /** Recreates the activities with the new language. */
    fun set(context: Context, tag: String) {
        if (!supported) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}
