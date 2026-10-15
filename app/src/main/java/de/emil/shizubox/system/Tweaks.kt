package de.emil.shizubox.system

import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t

enum class NightMode(private val labelRes: Int, val arg: String) {
    Light(R.string.theme_light, "no"),
    Dark(R.string.theme_dark, "yes"),
    Auto(R.string.night_auto, "auto"),
    ;
    val label get() = t(labelRes)
}

data class TweakState(
    val stayAwake: Boolean = false,
    val showTouches: Boolean = false,
    val pointerLocation: Boolean = false,
    val batterySaver: Boolean = false,
    val night: NightMode? = null,
    val screenTimeoutMs: Int = 0,
)

/**
 * Small system switches. All tested as the shell user on Android 17 (Pixel 11 Pro):
 * `settings put` for the values below and `cmd uimode night`. Battery saver goes through
 * the `low_power` setting because `cmd power set-mode` had no effect while charging.
 */
object Tweaks {
    val SCREEN_TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)

    /** AC + USB + wireless, the same value as the developer option. */
    private const val STAY_ON_ALL = 7

    suspend fun read(): TweakState {
        val out = exec(
            listOf(
                "settings get global stay_on_while_plugged_in",
                "settings get system show_touches",
                "settings get system pointer_location",
                "settings get global low_power",
                "cmd uimode night",
                "settings get system screen_off_timeout",
            ).joinToString("; "),
        ).lines()
        fun line(i: Int) = out.getOrElse(i) { "" }.trim()
        return TweakState(
            stayAwake = (line(0).toIntOrNull() ?: 0) != 0,
            showTouches = line(1) == "1",
            pointerLocation = line(2) == "1",
            batterySaver = line(3) == "1",
            night = when (line(4).substringAfter(": ", "").trim()) {
                "yes" -> NightMode.Dark
                "no" -> NightMode.Light
                "auto", "custom" -> NightMode.Auto
                else -> null
            },
            screenTimeoutMs = line(5).toIntOrNull() ?: 0,
        )
    }

    suspend fun stayAwake(): Boolean = (exec("settings get global stay_on_while_plugged_in").trim().toIntOrNull() ?: 0) != 0

    /** Screen timeout used as "never" (tested on Android 17: accepted and kept). */
    private const val CAFFEINE_TIMEOUT = Int.MAX_VALUE

    /** Caffeine: screen stays on no matter if charging – the screen timeout is set to "never". */
    suspend fun caffeine(): Boolean = (exec("settings get system screen_off_timeout").trim().toLongOrNull() ?: 0) >= CAFFEINE_TIMEOUT

    /** Turning it off restores the timeout that was set before. */
    suspend fun setCaffeine(on: Boolean) {
        val prefs = de.emil.shizubox.Str.context.createDeviceProtectedStorageContext()
            .getSharedPreferences("tiles", android.content.Context.MODE_PRIVATE)
        if (on) {
            val current = exec("settings get system screen_off_timeout").trim().toIntOrNull()
            if (current != null && current < CAFFEINE_TIMEOUT) prefs.edit().putInt("caffeine_prev", current).apply()
            setScreenTimeout(CAFFEINE_TIMEOUT)
        } else {
            setScreenTimeout(prefs.getInt("caffeine_prev", 120_000))
        }
    }

    suspend fun setStayAwake(on: Boolean) = exec("settings put global stay_on_while_plugged_in ${if (on) STAY_ON_ALL else 0}")
    suspend fun setShowTouches(on: Boolean) = exec("settings put system show_touches ${if (on) 1 else 0}")
    suspend fun setPointerLocation(on: Boolean) = exec("settings put system pointer_location ${if (on) 1 else 0}")
    suspend fun setBatterySaver(on: Boolean) = exec("settings put global low_power ${if (on) 1 else 0}")
    suspend fun setNight(mode: NightMode) = exec("cmd uimode night ${mode.arg}")
    suspend fun setScreenTimeout(ms: Int) = exec("settings put system screen_off_timeout $ms")

    private suspend fun exec(cmd: String) = ShizukuManager.exec(cmd, timeoutMs = 5000).out
}

fun timeoutLabel(ms: Int) = when {
    ms < 60_000 -> t(R.string.dur_sec, ms / 1000)
    ms < 3_600_000 -> t(R.string.dur_min, ms / 60_000)
    else -> t(R.string.dur_hours, ms / 3_600_000)
}
