package de.emil.shizubox.school

import android.util.Log
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuUnavailableException
import org.json.JSONObject

/**
 * All shell commands of the school mode. Every command was tested on a Pixel 11 Pro
 * with Android 17 (SDK 37.2) through Shizuku in ADB mode.
 */
object SchoolCommands {
    private const val TAG = "SchoolCommands"

    /** Settings changed while the mode is on: namespace, key, value during school mode (null = delete). */
    private val SETTINGS = listOf<Triple<String, String, String?>>(
        Triple("secure", "doze_always_on", "0"),
        Triple("secure", "lock_screen_show_notifications", "0"),
        Triple("secure", "camera_double_tap_power_gesture_disabled", "1"),
        // Android 16+: power double-press is its own gesture (camera or wallet).
        Triple("secure", "double_tap_power_button_gesture_enabled", "0"),
        // Anything that could light up the display on its own.
        Triple("secure", "wake_gesture_enabled", "0"),
        Triple("secure", "double_tap_to_wake", "0"),
        Triple("secure", "doze_enabled", "0"),
        Triple("secure", "doze_pulse_on_pick_up", "0"),
        Triple("secure", "doze_pulse_on_double_tap", "0"),
        Triple("secure", "doze_tap_gesture", "0"),
        // Vibration / haptics.
        Triple("system", "haptic_feedback_enabled", "0"),
        Triple("system", "vibrate_when_ringing", "0"),
        Triple("system", "haptic_feedback_intensity", "0"),
        Triple("system", "ring_vibration_intensity", "0"),
        Triple("system", "notification_vibration_intensity", "0"),
        // A display woken by accident goes dark again quickly.
        Triple("system", "screen_off_timeout", "10000"),
        // Long-press power and Quick Tap (back double-tap) would open the assistant (Gemini).
        Triple("global", "power_button_long_press", "0"),
        Triple("secure", "columbus_enabled", "0"),
        // No fingerprint/face unlock: unlocking would allow pulling down the shade.
        Triple("secure", "fingerprint_keyguard_enabled", "0"),
        Triple("secure", "face_keyguard_enabled", "0"),
        // Accessibility overlays (e.g. MT Island) are drawn above everything, even the lock
        // screen, and are not hidden when their app is suspended. Off for the duration.
        Triple("secure", "enabled_accessibility_services", null),
        Triple("secure", "accessibility_enabled", "0"),
    )

    /**
     * Not changed on purpose, only saved: suspending apps can make the system drop their
     * notification listeners, so these get written back on exit.
     */
    private val PRESERVED = listOf(
        "secure" to "enabled_notification_listeners",
    )

    /** Never suspended, in addition to the current launcher, dialer and keyboard. */
    private val ALWAYS_ALLOWED = setOf(
        "de.emil.shizubox",
        "moe.shizuku.privileged.api",
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.google.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
    )

    private val SELF_GRANTS = listOf(
        "appops set de.emil.shizubox SYSTEM_ALERT_WINDOW allow",
        "cmd deviceidle whitelist +de.emil.shizubox",
        "pm grant de.emil.shizubox android.permission.POST_NOTIFICATIONS",
    )

    private const val STATUSBAR_LOCK =
        "cmd statusbar send-disable-flag statusbar-expansion quick-settings notification-peek " +
            "clock system-icons notification-icons home recents search"

    /** Gives ShizuBox overlay, battery and notification rights it needs for the black screen. */
    suspend fun grantSelfPermissions() {
        run(SELF_GRANTS.joinToString("; "))
    }

    /** Reads everything that will be changed. */
    suspend fun takeSnapshot(): JSONObject {
        val keys = SETTINGS.map { it.first to it.second } + PRESERVED
        val script = buildString {
            for ((ns, key) in keys) append("echo \"$ns/$key=$(settings get $ns $key)\"; ")
            for (key in listOf("airplane_mode_on", "wifi_on", "bluetooth_on", "mobile_data", "zen_mode", "mode_ringer")) {
                append("echo \"global/$key=$(settings get global $key)\"; ")
            }
        }
        val json = JSONObject()
        for (line in run(script).lines()) {
            val (name, value) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: continue
            json.put(name, value)
        }
        return json
    }

    /** Launchable and third-party apps minus everything needed for emergencies and the exit. */
    suspend fun packagesToSuspend(shizukuPackage: String?): Set<String> {
        val launchable = run("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
            .lines().map { it.trim() }.filter { '/' in it }.map { it.substringBefore('/') }
        val thirdParty = run("pm list packages -3").lines().map { it.removePrefix("package:").trim() }
        val allowed = ALWAYS_ALLOWED.toMutableSet()
        shizukuPackage?.let(allowed::add)
        allowed += resolvePackage("-a android.intent.action.MAIN -c android.intent.category.HOME")
        allowed += resolvePackage("-a android.intent.action.DIAL")
        run("settings get secure default_input_method").substringBefore('/').trim().let(allowed::add)
        return (launchable + thirdParty).filter { it.isNotEmpty() && it !in allowed }.toSet()
    }

    private suspend fun resolvePackage(intentArgs: String): List<String> =
        run("cmd package resolve-activity --brief $intentArgs").lines().lastOrNull()
            ?.takeIf { '/' in it }?.substringBefore('/')?.trim()?.let(::listOf) ?: emptyList()

    /** Switches everything into school mode. Order matters: WLAN off before airplane mode,
     *  otherwise Android remembers "WLAN stays on in airplane mode". */
    suspend fun apply(suspend: Set<String>) {
        val script = buildList {
            if (suspend.isNotEmpty()) add("pm suspend ${suspend.joinToString(" ")} > /dev/null")
            for ((ns, key, value) in SETTINGS) {
                add(if (value == null) "settings delete $ns $key" else "settings put $ns $key $value")
            }
            add("cmd audio set-ringer-mode SILENT")
            add("cmd notification set_dnd none")
            add(STATUSBAR_LOCK)
            add("cmd wifi set-wifi-enabled disabled")
            add("cmd bluetooth_manager disable")
            add("svc data disable")
            add("cmd connectivity airplane-mode enable")
        }
        run(script.joinToString("; "))
    }

    /** The steps that bring back exactly what [takeSnapshot] recorded (only keys still in the snapshot). */
    fun restoreSteps(snapshot: JSONObject, suspended: Set<String>): List<RestorePlan.Step> =
        RestorePlan.steps(snapshot, suspended, SETTINGS.map { it.first to it.second } + PRESERVED)

    /**
     * One restore step with its own hard limit; success = exit code 0. Without Shizuku nothing
     * further can work: [RestorePlan.Abort] leaves the rest for the next attempt.
     */
    suspend fun runRestoreStep(step: RestorePlan.Step): Boolean {
        val cmd = step.command ?: return true
        val r = try {
            ShizukuManager.exec(cmd, timeoutMs = 5000, limitMs = step.limitMs)
        } catch (_: ShizukuUnavailableException) {
            throw RestorePlan.Abort()
        }
        if (!r.ok) Log.w(TAG, "restore step exit ${r.code}: ${cmd.take(120)} ${r.err.take(300)}")
        return r.ok
    }

    /** Only the status bar lock of [apply], e.g. again after a reboot. */
    suspend fun lockStatusBar() {
        run(STATUSBAR_LOCK)
    }

    /** Closes shade, quick settings and the power menu if someone managed to open them. */
    suspend fun closeSystemDialogs() {
        run("cmd statusbar collapse; am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null")
    }

    suspend fun screenOff() {
        run("input keyevent KEYCODE_SLEEP")
    }

    private suspend fun run(script: String): String {
        val r = ShizukuManager.exec(script, timeoutMs = 5000)
        if (!r.ok || r.err.isNotEmpty()) Log.w(TAG, "exit ${r.code}: ${r.err.take(500)}")
        return r.out
    }
}
