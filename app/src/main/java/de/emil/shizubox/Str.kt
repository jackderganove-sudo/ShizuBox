package de.emil.shizubox

import android.content.Context
import androidx.annotation.StringRes

/**
 * Translated strings from anywhere (Compose, services, workers, error messages).
 * Uses the application context, which follows the per-app language (Android 13+);
 * changing the language recreates the activities, so screens pick it up immediately.
 */
object Str {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

fun t(@StringRes id: Int, vararg args: Any?): String = Str.context.getString(id, *args)

fun tp(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any?): String =
    Str.context.resources.getQuantityString(id, count, *args)
