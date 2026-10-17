package de.emil.shizubox.hilight

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Colour and pattern for one app's notifications. */
data class AppRule(val pkg: String, val color: Int, val pattern: String = Pattern.Pulse.key, val faceDown: Boolean = false, val on: Boolean = true)

/** Colour and pattern for one sender (matched against the sender name of messages). */
data class ContactRule(val name: String, val color: Int, val pattern: String = Pattern.Pulse.key, val faceDown: Boolean = false)

data class HiLightConfig(
    val enabled: Boolean = false,
    /** 0.1–1.0 */
    val brightness: Float = 0.8f,
    val faceDownOnly: Boolean = false,
    /** Notification effects: at most 60 s (HiLight Studio's limit). */
    val alertSeconds: Int = 15,
    /** Ambient effects (automation, tile test): default 30 s, at most 5 min. */
    val ambientSeconds: Int = 30,
    val ownProgress: Boolean = true,
    /** "pkg" or "pkg/channel" whose progress notifications are shown. */
    val progressSources: Set<String> = DEFAULT_PROGRESS,
    val appRules: List<AppRule> = DEFAULT_APPS,
    val contactRules: List<ContactRule> = emptyList(),
    val charging: Boolean = false,
    val privacy: Boolean = false,
    val privacyCamera: Boolean = true,
    val privacyMic: Boolean = true,
    val privacyColor: Int = 0xFFFF5252.toInt(),
    val quietDnd: Boolean = true,
    val quietNight: Boolean = true,
    val nightFrom: Int = 22 * 60,
    val nightTo: Int = 7 * 60,
    val quietPowerSave: Boolean = true,
    val quietLowBattery: Boolean = true,
    val lowBatteryPercent: Int = 15,
) {
    companion object {
        /**
         * Tested on the Pixel 11 Pro: system updates post their progress from Play services in the
         * channel `system_update.default_notification_channel`; Play Store downloads come from com.android.vending.
         */
        const val SYSTEM_UPDATE = "com.google.android.gms/system_update.default_notification_channel"
        const val PLAY_STORE = "com.android.vending"
        val DEFAULT_PROGRESS = setOf(SYSTEM_UPDATE, PLAY_STORE)
        val DEFAULT_APPS = listOf(
            AppRule("com.whatsapp", 0xFF25D366.toInt()),
            AppRule("com.snapchat.android", 0xFFFFFC00.toInt()),
            AppRule("com.bycsdrive.android", 0xFF1E88E5.toInt()),
        )
    }
}

/** HiLight settings and rules: device-protected prefs "hilight", part of the backup. */
object HiLightSettings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(HiLightConfig())
    val state: StateFlow<HiLightConfig> = _state.asStateFlow()
    val current get() = _state.value

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("hilight", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        val d = HiLightConfig()
        _state.value = runCatching { prefs.getString("config", null)?.let { decode(JSONObject(it)) } }.getOrNull() ?: d
    }

    fun update(f: (HiLightConfig) -> HiLightConfig) {
        val c = f(_state.value)
        _state.value = c
        prefs.edit().putString("config", encode(c).toString()).apply()
    }

    private fun encode(c: HiLightConfig) = JSONObject()
        .put("enabled", c.enabled).put("brightness", c.brightness.toDouble()).put("faceDownOnly", c.faceDownOnly)
        .put("alertSeconds", c.alertSeconds).put("ambientSeconds", c.ambientSeconds).put("ownProgress", c.ownProgress)
        .put("progressSources", JSONArray(c.progressSources.toList()))
        .put("appRules", JSONArray(c.appRules.map { JSONObject().put("pkg", it.pkg).put("color", it.color).put("pattern", it.pattern).put("faceDown", it.faceDown).put("on", it.on) }))
        .put("contactRules", JSONArray(c.contactRules.map { JSONObject().put("name", it.name).put("color", it.color).put("pattern", it.pattern).put("faceDown", it.faceDown) }))
        .put("charging", c.charging).put("privacy", c.privacy).put("privacyCamera", c.privacyCamera).put("privacyMic", c.privacyMic).put("privacyColor", c.privacyColor)
        .put("quietDnd", c.quietDnd).put("quietNight", c.quietNight).put("nightFrom", c.nightFrom).put("nightTo", c.nightTo)
        .put("quietPowerSave", c.quietPowerSave).put("quietLowBattery", c.quietLowBattery).put("lowBatteryPercent", c.lowBatteryPercent)

    private fun decode(o: JSONObject): HiLightConfig {
        val d = HiLightConfig()
        fun <T> list(key: String, f: (JSONObject) -> T): List<T>? = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { runCatching { f(a.getJSONObject(it)) }.getOrNull() } }
        return HiLightConfig(
            enabled = o.optBoolean("enabled", d.enabled),
            brightness = o.optDouble("brightness", d.brightness.toDouble()).toFloat().coerceIn(0.1f, 1f),
            faceDownOnly = o.optBoolean("faceDownOnly", d.faceDownOnly),
            alertSeconds = o.optInt("alertSeconds", d.alertSeconds).coerceIn(3, 60),
            ambientSeconds = o.optInt("ambientSeconds", d.ambientSeconds).coerceIn(5, 300),
            ownProgress = o.optBoolean("ownProgress", d.ownProgress),
            progressSources = o.optJSONArray("progressSources")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: d.progressSources,
            appRules = list("appRules") { AppRule(it.getString("pkg"), it.getInt("color"), it.optString("pattern", "pulse"), it.optBoolean("faceDown"), it.optBoolean("on", true)) } ?: d.appRules,
            contactRules = list("contactRules") { ContactRule(it.getString("name"), it.getInt("color"), it.optString("pattern", "pulse"), it.optBoolean("faceDown")) } ?: d.contactRules,
            charging = o.optBoolean("charging", d.charging),
            privacy = o.optBoolean("privacy", d.privacy),
            privacyCamera = o.optBoolean("privacyCamera", d.privacyCamera),
            privacyMic = o.optBoolean("privacyMic", d.privacyMic),
            privacyColor = o.optInt("privacyColor", d.privacyColor),
            quietDnd = o.optBoolean("quietDnd", d.quietDnd),
            quietNight = o.optBoolean("quietNight", d.quietNight),
            nightFrom = o.optInt("nightFrom", d.nightFrom),
            nightTo = o.optInt("nightTo", d.nightTo),
            quietPowerSave = o.optBoolean("quietPowerSave", d.quietPowerSave),
            quietLowBattery = o.optBoolean("quietLowBattery", d.quietLowBattery),
            lowBatteryPercent = o.optInt("lowBatteryPercent", d.lowBatteryPercent),
        )
    }
}
