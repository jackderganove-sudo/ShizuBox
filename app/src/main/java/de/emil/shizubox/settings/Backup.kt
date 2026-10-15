package de.emil.shizubox.settings

import android.content.Context
import android.content.SharedPreferences
import de.emil.shizubox.BuildConfig
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.installer.VirusTotal
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.updates.UpdateWorker
import de.emil.shizubox.updates.Updates
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Export/import of all ShizuBox settings as one JSON file.
 * Runtime state is left out on purpose (school mode snapshot, active gaming snapshot):
 * restoring it on another day could switch things the user does not expect.
 */
object Backup {
    private const val FORMAT = "shizubox-backup"

    /** Preference files and the keys that are never part of a backup. */
    private val PREFS = mapOf(
        "app_settings" to emptySet(),
        "customization" to emptySet(),
        "gaming" to setOf("snapshot"),
        "terminal" to emptySet(),
        "freeze_profiles" to emptySet(),
        "updates" to emptySet(),
        "virustotal" to emptySet(),
        "tiles" to emptySet(),
        "activity_launcher" to emptySet(),
        "downloader" to emptySet(),
        "boost" to setOf("enabled", "dose_before_reset"),
        // Only the settings: the history itself never leaves the device.
        "notif_history" to setOf("enabled"),
        "net_block" to emptySet(),
    )

    /** Only exported when the user asks for it. */
    private val SECRETS = mapOf("updates" to setOf("github_token"), "virustotal" to setOf("api_key"))

    data class Result(val prefs: Int, val trackedApps: Int)

    private fun dp(context: Context) = context.createDeviceProtectedStorageContext()
    private fun prefs(context: Context, name: String) = dp(context).getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun trackedFile(context: Context) = File(dp(context).filesDir, "tracked_apps.json")

    fun export(context: Context, includeSecrets: Boolean): String {
        val prefsJson = JSONObject()
        for ((name, excluded) in PREFS) {
            val secret = SECRETS[name].orEmpty()
            val o = JSONObject()
            prefs(context, name).all.forEach { (k, v) ->
                if (k in excluded || (!includeSecrets && k in secret) || v == null) return@forEach
                o.put(k, typed(v))
            }
            if (o.length() > 0) prefsJson.put(name, o)
        }
        val school = SchoolMode.state.value
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("created", System.currentTimeMillis())
            .put("prefs", prefsJson)
            .put("tracked_apps", runCatching { trackedFile(context).readText() }.getOrDefault(""))
            .put("school", JSONObject().put("auto_end", school.autoEndMinutes).put("wait_for_shizuku", school.waitForShizukuAfterReboot))
            .put("language", Language.current(context))
            .toString(2)
    }

    suspend fun import(context: Context, text: String): Result {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "not a ShizuBox backup" }
        val prefsJson = root.optJSONObject("prefs") ?: JSONObject()
        val tracked = root.optString("tracked_apps")
        // Validate before anything is overwritten.
        val apps = if (tracked.isBlank()) 0 else JSONArray(tracked).length()
        var count = 0
        for ((name, excluded) in PREFS) {
            val o = prefsJson.optJSONObject(name) ?: continue
            val p = prefs(context, name)
            val secret = SECRETS[name].orEmpty()
            val edit = p.edit()
            // Replace everything except excluded keys and secrets the backup does not contain.
            p.all.keys.filter { it !in excluded && (it !in secret || o.has(it)) }.forEach { edit.remove(it) }
            o.keys().forEach { k -> if (k !in excluded) put(edit, k, o.getJSONObject(k)) }
            edit.commit()
            count++
        }
        if (tracked.isNotBlank()) trackedFile(context).writeText(tracked)

        // Reload everything that keeps its settings in memory.
        AppSettings.init(context)
        Profiles.init(context)
        Gaming.init(context)
        VirusTotal.init(context)
        de.emil.shizubox.apps.ActivityLauncher.reload()
        de.emil.shizubox.downloader.DownloaderSettings.load()
        de.emil.shizubox.system.VolumeBoost.load()
        de.emil.shizubox.notifications.NotifHistory.load()
        de.emil.shizubox.apps.NetBlock.load()
        runCatching { de.emil.shizubox.apps.NetBlock.restore(force = true) }
        Updates.init(context)
        UpdateWorker.schedule(context)
        root.optJSONObject("school")?.let { s ->
            SchoolMode.setAutoEndMinutes(s.optInt("auto_end", SchoolMode.state.value.autoEndMinutes))
            SchoolMode.setWaitForShizukuAfterReboot(s.optBoolean("wait_for_shizuku", SchoolMode.state.value.waitForShizukuAfterReboot))
        }
        root.optString("language").takeIf { root.has("language") && it != Language.current(context) }?.let { Language.set(context, it) }
        return Result(count, apps)
    }

    private fun typed(v: Any): JSONObject = when (v) {
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Long -> JSONObject().put("t", "l").put("v", v)
        is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
        is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
        else -> JSONObject().put("t", "s").put("v", v.toString())
    }

    private fun put(edit: SharedPreferences.Editor, key: String, o: JSONObject) {
        when (o.optString("t")) {
            "b" -> edit.putBoolean(key, o.getBoolean("v"))
            "i" -> edit.putInt(key, o.getInt("v"))
            "l" -> edit.putLong(key, o.getLong("v"))
            "f" -> edit.putFloat(key, o.getDouble("v").toFloat())
            "ss" -> edit.putStringSet(key, o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            else -> edit.putString(key, o.optString("v"))
        }
    }
}
