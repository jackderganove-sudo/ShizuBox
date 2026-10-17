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
        // Only the settings: the history itself never leaves the device; on/off see PrivacySwitch.
        "notif_history" to setOf("enabled"),
        "net_block" to emptySet(),
        "automations" to emptySet(),
        "debloat" to emptySet(),
        // Only the settings; the access log itself stays on the device.
        "battery_report" to setOf("last_snapshot"),
        // Only the settings; copied texts never leave the device. on/off see PrivacySwitch;
        // toast_before is this phone's previous system value (device state), it never travels.
        "clipboard" to setOf("enabled", "toast_before"),
        "app_volume" to emptySet(),
        // Only the settings (exclusions, schedule, categories); trash and scan results stay on the device.
        "cleaner" to emptySet(),
        "hilight" to emptySet(),
        // Days and time of the planned school mode; "skip the next one" is runtime state.
        "school_schedule" to setOf("skip_until"),
        "privacy" to setOf("enabled", "since_CAMERA", "since_RECORD_AUDIO", "since_FINE_LOCATION", "since_COARSE_LOCATION"),
    )

    /**
     * Features that record private data or change a system-wide privacy setting. An import never
     * switches them on (Daniel's review 0.11.1) – the backup only remembers that they were on, and
     * the user is asked afterwards. [target] is the [de.emil.shizubox.search.SearchIndex] entry.
     */
    enum class PrivacySwitch(val prefs: String, val key: String, val target: String) {
        Clipboard("clipboard", "enabled", "clipboard"),
        ClipboardHint("clipboard", "toast_before", "clipboard"),
        NotifHistory("notif_history", "enabled", "notifhistory"),
        PrivacyLog("privacy", "enabled", "privacy");

        /** On on this phone? The hint is hidden while ShizuBox keeps the previous system value. */
        fun isOn(p: SharedPreferences) = if (this == ClipboardHint) p.contains(key) else p.getBoolean(key, false)
    }

    /** Only exported when the user asks for it. */
    private val SECRETS = mapOf("updates" to setOf("github_token"), "virustotal" to setOf("api_key"))

    /** [offers]: privacy features that were on in the backup but are off here – ask the user. */
    data class Result(val prefs: Int, val trackedApps: Int, val offers: List<PrivacySwitch> = emptyList())

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
            .put("privacy_switches", JSONObject().apply { PrivacySwitch.entries.forEach { put(it.name, it.isOn(prefs(context, it.prefs))) } })
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
        // Older backups (≤ 0.11.0) still carry the switches inside "prefs".
        val switches = root.optJSONObject("privacy_switches")
        val offers = PrivacySwitch.entries.filter { s ->
            val inBackup = if (switches != null) switches.optBoolean(s.name) else prefsJson.optJSONObject(s.prefs)?.let { o ->
                if (s == PrivacySwitch.ClipboardHint) o.has(s.key) else o.optJSONObject(s.key)?.optBoolean("v") == true
            } == true
            inBackup && !s.isOn(prefs(context, s.prefs))
        }
        // Two phases (Daniel's review, 0.11.1): first decode and check everything, then write – a
        // broken entry used to leave a half-imported mix of old and new settings.
        val plan = PREFS.mapNotNull { (name, excluded) ->
            val o = prefsJson.optJSONObject(name) ?: return@mapNotNull null
            name to o.keys().asSequence().filter { it !in excluded }.associateWith { k -> decode(o.getJSONObject(k)) }
        }
        val school = root.optJSONObject("school")?.let { s ->
            s.optInt("auto_end", SchoolMode.state.value.autoEndMinutes).coerceAtLeast(0) to
                s.optBoolean("wait_for_shizuku", SchoolMode.state.value.waitForShizukuAfterReboot)
        }
        var count = 0
        for ((name, values) in plan) {
            val excluded = PREFS.getValue(name)
            val p = prefs(context, name)
            val secret = SECRETS[name].orEmpty()
            val edit = p.edit()
            // Replace everything except excluded keys and secrets the backup does not contain.
            p.all.keys.filter { it !in excluded && (it !in secret || it in values) }.forEach { edit.remove(it) }
            values.forEach { (k, v) -> putValue(edit, k, v) }
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
        de.emil.shizubox.automation.Rules.load()
        de.emil.shizubox.apps.Debloat.load()
        de.emil.shizubox.privacy.PrivacyLog.load()
        de.emil.shizubox.battery.BatteryReport.load()
        de.emil.shizubox.clipboard.ClipHistory.load()
        de.emil.shizubox.audio.AppVolume.load()
        de.emil.shizubox.cleaner.CleanerSettings.load()
        de.emil.shizubox.hilight.HiLightSettings.load()
        de.emil.shizubox.school.SchoolSchedule.init(context)
        runCatching { de.emil.shizubox.cleaner.CleanerSchedule.schedule(context) }
        runCatching { de.emil.shizubox.apps.NetBlock.restore(force = true) }
        Updates.init(context)
        UpdateWorker.schedule(context)
        school?.let { (autoEnd, wait) ->
            SchoolMode.setAutoEndMinutes(autoEnd)
            SchoolMode.setWaitForShizukuAfterReboot(wait)
        }
        root.optString("language").takeIf { root.has("language") && it != Language.current(context) }?.let { Language.set(context, it) }
        return Result(count, apps, offers)
    }

    private fun typed(v: Any): JSONObject = when (v) {
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Long -> JSONObject().put("t", "l").put("v", v)
        is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
        is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
        else -> JSONObject().put("t", "s").put("v", v.toString())
    }

    /** One typed value of the backup; throws on anything malformed (checked before writing). */
    private fun decode(o: JSONObject): Any = when (val t = o.getString("t")) {
        "b" -> o.getBoolean("v")
        "i" -> o.getInt("v")
        "l" -> o.getLong("v")
        "f" -> o.getDouble("v").toFloat()
        "ss" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        "s" -> o.getString("v")
        else -> error("unknown type $t")
    }

    @Suppress("UNCHECKED_CAST")
    private fun putValue(edit: SharedPreferences.Editor, key: String, v: Any) {
        when (v) {
            is Boolean -> edit.putBoolean(key, v)
            is Int -> edit.putInt(key, v)
            is Long -> edit.putLong(key, v)
            is Float -> edit.putFloat(key, v)
            is Set<*> -> edit.putStringSet(key, v as Set<String>)
            else -> edit.putString(key, v.toString())
        }
    }
}
