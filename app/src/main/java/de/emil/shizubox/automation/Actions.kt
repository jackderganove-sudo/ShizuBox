package de.emil.shizubox.automation

import android.content.Context
import android.media.AudioManager
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.NetBlock
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.system.BoostService
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.system.Tweaks
import de.emil.shizubox.system.VolumeBoost
import kotlin.math.roundToInt

/**
 * Runs automation actions. Each run returns the action that brings back the state from before
 * (for "undo when the trigger ends"), or null if there is nothing to undo.
 * Shell commands tested on Android 17:
 * - grayscale: `settings put secure accessibility_display_daltonizer_enabled 1` + `accessibility_display_daltonizer 0`
 * - do not disturb: `cmd notification set_dnd priority|off`, current mode in `settings get global zen_mode`
 */
object Actions {
    private val dndModes = mapOf(0 to "off", 1 to "priority", 2 to "none", 3 to "alarms")

    private suspend fun sh(cmd: String) = ShizukuManager.exec(cmd, 15_000)

    suspend fun run(context: Context, a: Action): Action? = when (a.kind) {
        ActionKind.Gaming -> {
            val before = Gaming.active.value
            if (a.on) Gaming.enable() else Gaming.disable()
            a.copy(on = before)
        }
        ActionKind.Volume -> {
            val am = context.getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val before = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (a.value / 100f * max).roundToInt().coerceIn(0, max), 0)
            a.copy(value = (before * 100f / max).roundToInt())
        }
        ActionKind.AppVolume -> {
            val before = de.emil.shizubox.audio.AppVolume.get(a.pkg)
            de.emil.shizubox.audio.AppVolume.set(a.pkg, a.value)
            a.copy(value = before)
        }
        ActionKind.Boost -> {
            val before = VolumeBoost.settings.value.enabled
            VolumeBoost.update { it.copy(enabled = a.on) }
            BoostService.sync(context)
            a.copy(on = before)
        }
        ActionKind.Refresh -> {
            val before = Customization.refreshMode()
            Customization.setRefresh(RefreshMode.entries.getOrElse(a.value) { RefreshMode.Adaptive })
            a.copy(value = before.ordinal)
        }
        ActionKind.Animations -> {
            val before = Customization.animationScale()
            Customization.setAnimationScale(a.value / 100f)
            a.copy(value = (before * 100).roundToInt())
        }
        ActionKind.Grayscale -> {
            val enabled = sh("settings get secure accessibility_display_daltonizer_enabled").out.trim()
            val mode = sh("settings get secure accessibility_display_daltonizer").out.trim()
            sh(
                if (a.on) "settings put secure accessibility_display_daltonizer_enabled 1; settings put secure accessibility_display_daltonizer 0"
                else "settings put secure accessibility_display_daltonizer_enabled 0",
            )
            // Exact restore, also of a colour correction that was set before.
            fun put(key: String, v: String) = if (v == "null" || v.isBlank()) "settings delete secure $key" else "settings put secure $key $v"
            Action(ActionKind.Shell, text = put("accessibility_display_daltonizer", mode) + "; " + put("accessibility_display_daltonizer_enabled", enabled))
        }
        ActionKind.Dnd -> {
            val before = sh("settings get global zen_mode").out.trim().toIntOrNull() ?: 0
            sh("cmd notification set_dnd ${if (a.on) "priority" else "off"}")
            Action(ActionKind.Shell, text = "cmd notification set_dnd ${dndModes[before] ?: "off"}")
        }
        ActionKind.StayAwake -> {
            val before = Tweaks.caffeine()
            Tweaks.setCaffeine(a.on)
            a.copy(on = before)
        }
        ActionKind.NetBlock -> {
            val before = NetBlock.isBlocked(a.pkg)
            NetBlock.set(listOf(a.pkg), a.on)
            a.copy(on = before)
        }
        ActionKind.StartApp -> {
            Apps.launchIntent(a.pkg)?.component?.let { start(context, it.packageName, it.className) }
            null
        }
        ActionKind.StartActivity -> {
            a.text.split('/', limit = 2).takeIf { it.size == 2 }?.let { (p, cls) -> start(context, p, if (cls.startsWith(".")) p + cls else cls) }
            null
        }
        ActionKind.Shell -> {
            sh(a.text)
            null
        }
    }

    /**
     * From the background, Android silently drops plain startActivity calls; the shell may
     * start activities (`am start -n`), so Shizuku goes first.
     */
    private suspend fun start(context: Context, pkg: String, cls: String) {
        if (ShizukuManager.isReady) {
            sh("am start -n $pkg/$cls")
        } else {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .setClassName(pkg, cls).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
