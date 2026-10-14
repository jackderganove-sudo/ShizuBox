package de.emil.shizubox.customization

import android.content.Context
import android.content.SharedPreferences
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Snapshot of everything the Customization page shows. */
data class CustomState(
    val animationScale: Float = 1f,
    /** Refresh rate preset, see [RefreshMode]. */
    val refresh: RefreshMode = RefreshMode.Adaptive,
    val fontScale: Float = 1f,
    val boldText: Boolean = false,
    val physicalDensity: Int = 0,
    val density: Int = 0,
    val physicalWidth: Int = 0,
    val physicalHeight: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val statusBar: StatusBarHide = StatusBarHide(),
) {
    /** Render scale in percent (100 = native resolution). */
    val resolutionPercent: Int
        get() = if (physicalWidth == 0) 100 else (width * 100f / physicalWidth).roundToInt()
}

/**
 * Parts of the status bar to hide. Android 17 ignores the old per-icon `icon_blacklist`
 * (tested, also after a SystemUI restart), so only these groups are possible.
 */
data class StatusBarHide(
    val systemIcons: Boolean = false,
    val clock: Boolean = false,
    val notificationIcons: Boolean = false,
) {
    val any get() = systemIcons || clock || notificationIcons

    /** Arguments for `cmd statusbar send-disable-flag`. */
    fun flags() = listOfNotNull(
        "system-icons".takeIf { systemIcons },
        "clock".takeIf { clock },
        "notification-icons".takeIf { notificationIcons },
    ).ifEmpty { listOf("none") }.joinToString(" ")
}

enum class RefreshMode(val label: String) {
    Adaptive("Adaptiv"),
    Fixed60("60 Hz"),
    AlwaysMax("Immer max."),
}

/** A display change that is reverted unless the user confirms it in time. */
data class PendingDisplayChange(val description: String, val revertAt: Long)

/**
 * Customization commands. All tested on a Pixel 11 Pro with Android 17 (SDK 37.2) via Shizuku.
 * Note: `cmd display set-user-preferred-display-mode` (Pixel "max resolution") throws a
 * SecurityException for the shell on this Android version, so resolution is done via `wm size`.
 */
object Customization {
    const val CONFIRM_SECONDS = 15

    val ANIMATION_SCALES = listOf(0f, 0.5f, 0.75f, 1f, 1.5f, 2f)
    val FONT_SCALES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    val RESOLUTION_PERCENTS = listOf(100, 90, 80, 75)


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var revertJob: Job? = null

    private val _pending = MutableStateFlow<PendingDisplayChange?>(null)
    val pending: StateFlow<PendingDisplayChange?> = _pending.asStateFlow()

    suspend fun read(): CustomState {
        val out = ShizukuManager.exec(
            listOf(
                "settings get global animator_duration_scale",
                "settings get system peak_refresh_rate",
                "settings get system min_refresh_rate",
                "settings get system font_scale",
                "settings get secure font_weight_adjustment",
                "wm density",
                "wm size",
            ).joinToString("; "),
        ).out.lines()
        fun line(i: Int) = out.getOrElse(i) { "" }.trim()

        val density = out.filter { "density" in it }
        val size = out.filter { "size" in it }
        val physicalDensity = density.firstOrNull { it.startsWith("Physical") }?.substringAfter(": ")?.toIntOrNull() ?: 0
        val physicalSize = size.firstOrNull { it.startsWith("Physical") }?.substringAfter(": ").orEmpty()
        val overrideSize = size.firstOrNull { it.startsWith("Override") }?.substringAfter(": ")
        val (pw, ph) = parseSize(physicalSize)
        val (w, h) = parseSize(overrideSize ?: physicalSize)
        val peak = line(1).toFloatOrNull() ?: Float.POSITIVE_INFINITY
        val min = line(2).toFloatOrNull() ?: 0f
        return CustomState(
            animationScale = line(0).toFloatOrNull() ?: 1f,
            refresh = when {
                min >= 120f || min.isInfinite() -> RefreshMode.AlwaysMax
                peak <= 60f -> RefreshMode.Fixed60
                else -> RefreshMode.Adaptive
            },
            fontScale = line(3).toFloatOrNull() ?: 1f,
            boldText = (line(4).toIntOrNull() ?: 0) > 0,
            physicalDensity = physicalDensity,
            density = density.firstOrNull { it.startsWith("Override") }?.substringAfter(": ")?.toIntOrNull() ?: physicalDensity,
            physicalWidth = pw,
            physicalHeight = ph,
            width = w,
            height = h,
            statusBar = loadStatusBar(),
        )
    }

    private fun parseSize(s: String): Pair<Int, Int> {
        val parts = s.trim().split('x')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    suspend fun setAnimationScale(scale: Float) = run(
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .joinToString("; ") { "settings put global $it $scale" },
    )

    suspend fun setRefresh(mode: RefreshMode) = run(
        when (mode) {
            RefreshMode.Adaptive -> "settings put system peak_refresh_rate Infinity; settings put system min_refresh_rate 0.0"
            RefreshMode.Fixed60 -> "settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 0.0"
            RefreshMode.AlwaysMax -> "settings put system peak_refresh_rate Infinity; settings put system min_refresh_rate Infinity"
        },
    )

    suspend fun setFontScale(scale: Float) = run("settings put system font_scale $scale")

    /** Same value the system "Bold text" switch writes. */
    suspend fun setBoldText(bold: Boolean) = run("settings put secure font_weight_adjustment ${if (bold) 300 else 0}")

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        // Device-protected: the app also starts before the first unlock (school mode boot).
        prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("customization", Context.MODE_PRIVATE)
    }

    private fun loadStatusBar() = StatusBarHide(
        systemIcons = prefs.getBoolean("hide_system_icons", false),
        clock = prefs.getBoolean("hide_clock", false),
        notificationIcons = prefs.getBoolean("hide_notification_icons", false),
    )

    suspend fun setStatusBar(hide: StatusBarHide) {
        prefs.edit()
            .putBoolean("hide_system_icons", hide.systemIcons)
            .putBoolean("hide_clock", hide.clock)
            .putBoolean("hide_notification_icons", hide.notificationIcons)
            .apply()
        applyStatusBar()
    }

    /**
     * The flags live in system_server only until the next reboot, so this is called again
     * whenever Shizuku (re)connects and after the school mode released its own lock.
     */
    suspend fun applyStatusBar() {
        val hide = loadStatusBar()
        run("cmd statusbar send-disable-flag ${hide.flags()}")
    }

    fun hasStatusBarCustomization() = ::prefs.isInitialized && loadStatusBar().any

    /** Display size (DPI). Reverted after [CONFIRM_SECONDS] unless [confirm] is called. */
    suspend fun setDensity(current: CustomState, density: Int) {
        val revert = densityCommand(current.density, current.physicalDensity)
        run(densityCommand(density, current.physicalDensity))
        scheduleRevert("Anzeigegröße $density dpi", revert)
    }

    /** Render resolution in percent of the panel, with proportionally scaled DPI so things keep their size. */
    suspend fun setResolution(current: CustomState, percent: Int) {
        val revert = sizeCommand(current.width, current.height, current) + "; " +
            densityCommand(current.density, current.physicalDensity)
        val w = even(current.physicalWidth * percent / 100f)
        val h = even(current.physicalHeight * percent / 100f)
        val relativeDensity = current.density.toFloat() / current.physicalDensity
        val d = (current.physicalDensity * percent / 100f * relativeDensity).roundToInt()
        run(sizeCommand(w, h, current) + "; " + densityCommand(d, current.physicalDensity))
        scheduleRevert("Auflösung $percent % (${w}×$h)", revert)
    }

    fun confirm() {
        revertJob?.cancel()
        _pending.value = null
    }

    /** Undo immediately. */
    fun revertNow() {
        revertJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                pendingRevert?.let { cmd -> scope.launch { run(cmd) } }
            }
        }
        _pending.value = null
    }

    private var pendingRevert: String? = null

    private fun scheduleRevert(description: String, revertCommand: String) {
        revertJob?.cancel()
        pendingRevert = revertCommand
        _pending.value = PendingDisplayChange(description, System.currentTimeMillis() + CONFIRM_SECONDS * 1000L)
        revertJob = scope.launch {
            delay(CONFIRM_SECONDS * 1000L)
            run(revertCommand)
            _pending.value = null
        }
    }

    /** Default DPI for the current render resolution (what "Standard" means). */
    fun baseDensity(s: CustomState) = (s.physicalDensity * s.resolutionPercent / 100f).roundToInt()

    /** `wm density reset` always goes back to the panel's physical DPI. */
    private fun densityCommand(density: Int, physical: Int) =
        if (density == physical) "wm density reset" else "wm density $density"

    private fun sizeCommand(w: Int, h: Int, s: CustomState) =
        if (w == s.physicalWidth && h == s.physicalHeight) "wm size reset" else "wm size ${w}x$h"

    private fun even(v: Float) = (v / 2).roundToInt() * 2

    private suspend fun run(cmd: String) {
        ShizukuManager.exec(cmd, timeoutMs = 5000)
    }
}
