package de.emil.shizubox.system

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class GamingOptions(
    val blockNotifications: Boolean = true,
    val noHeadsUp: Boolean = true,
    val maxRefresh: Boolean = true,
    val fixedBrightness: Boolean = true,
    val killBackground: Boolean = true,
)

data class Game(val pkg: String, val label: String)

/**
 * Gaming mode: fewer interruptions, maximum smoothness. Like the school mode, every changed
 * setting is saved first and restored exactly when it is turned off.
 */
object Gaming {
    private lateinit var prefs: SharedPreferences
    private lateinit var pm: PackageManager

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _options = MutableStateFlow(GamingOptions())
    val options: StateFlow<GamingOptions> = _options.asStateFlow()

    /** namespace/key of every setting gaming mode may touch. */
    private val KEYS = listOf(
        "global/zen_mode",
        "global/heads_up_notifications_enabled",
        "system/peak_refresh_rate",
        "system/min_refresh_rate",
        "system/screen_brightness_mode",
    )

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("gaming", Context.MODE_PRIVATE)
        pm = context.packageManager
        _active.value = prefs.getString("snapshot", null) != null
        _options.value = GamingOptions(
            blockNotifications = prefs.getBoolean("block_notifications", true),
            noHeadsUp = prefs.getBoolean("no_heads_up", true),
            maxRefresh = prefs.getBoolean("max_refresh", true),
            fixedBrightness = prefs.getBoolean("fixed_brightness", true),
            killBackground = prefs.getBoolean("kill_background", true),
        )
    }

    fun setOptions(o: GamingOptions) {
        _options.value = o
        prefs.edit()
            .putBoolean("block_notifications", o.blockNotifications)
            .putBoolean("no_heads_up", o.noHeadsUp)
            .putBoolean("max_refresh", o.maxRefresh)
            .putBoolean("fixed_brightness", o.fixedBrightness)
            .putBoolean("kill_background", o.killBackground)
            .apply()
    }

    /** Installed apps that declare themselves as games. */
    fun games(): List<Game> = pm.getInstalledApplications(0)
        .filter { it.category == ApplicationInfo.CATEGORY_GAME && it.enabled }
        .map { Game(it.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.label.lowercase() }

    fun launchIntent(pkg: String) = pm.getLaunchIntentForPackage(pkg)

    suspend fun enable() {
        if (_active.value) return
        val read = KEYS.joinToString("; ") { k ->
            val (ns, key) = k.split('/')
            "echo \"$k=$(settings get $ns $key)\""
        }
        val snapshot = JSONObject()
        ShizukuManager.exec(read).out.lines().forEach { line ->
            val (k, v) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            snapshot.put(k, v)
        }
        prefs.edit().putString("snapshot", snapshot.toString()).commit()

        val o = _options.value
        val cmds = buildList {
            if (o.blockNotifications) add("cmd notification set_dnd priority")
            if (o.noHeadsUp) add("settings put global heads_up_notifications_enabled 0")
            if (o.maxRefresh) add("settings put system peak_refresh_rate Infinity; settings put system min_refresh_rate Infinity")
            if (o.fixedBrightness) add("settings put system screen_brightness_mode 0")
            // Kills cached background processes only, nothing the user currently sees.
            if (o.killBackground) add("am kill-all")
        }
        if (cmds.isNotEmpty()) ShizukuManager.exec(cmds.joinToString("; "))
        _active.value = true
    }

    suspend fun disable() {
        val raw = prefs.getString("snapshot", null) ?: run { _active.value = false; return }
        val s = JSONObject(raw)
        fun v(k: String) = s.optString(k, "null")
        val cmds = buildList {
            add(
                "cmd notification set_dnd " + when (v("global/zen_mode")) {
                    "1" -> "priority"
                    "2" -> "none"
                    "3" -> "alarms"
                    else -> "off"
                },
            )
            for (k in KEYS.drop(1)) {
                val (ns, key) = k.split('/')
                val value = v(k)
                add(if (value == "null") "settings delete $ns $key" else "settings put $ns $key $value")
            }
        }
        ShizukuManager.exec(cmds.joinToString("; "))
        prefs.edit().remove("snapshot").commit()
        _active.value = false
    }
}
