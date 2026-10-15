package de.emil.shizubox.apps

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import de.emil.shizubox.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class DebloatRating { Safe, Caution, DontTouch }

data class DebloatItem(
    val pkg: String,
    val label: String,
    val rating: DebloatRating,
    /** Our own short explanation (string resource), if there is one. */
    val explainRes: Int?,
    /** Description from the Universal Android Debloater list (English). */
    val uad: String,
    val frozen: Boolean,
    /** Core part of the system: can't be frozen here at all. */
    val locked: Boolean,
)

data class DebloatLogEntry(val pkg: String, val label: String, val time: Long, val undone: Boolean)

/**
 * Debloat suggestions for preinstalled apps. Basis: the list of the Universal Android Debloater
 * (UAD-ng, GPL-3.0, `uad_lists.json` as of commit 76992ec, 2026-10-11) trimmed to Google/AOSP and
 * the packages of a Pixel (assets/debloat_uad.json). Some ratings are stricter for Pixels, see
 * [overrides]. On Android 17 system apps can only be frozen (`pm disable-user`), never removed.
 * Every freeze is logged and can be undone one by one or all at once.
 */
object Debloat {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _log = MutableStateFlow<List<DebloatLogEntry>>(emptyList())
    val log: StateFlow<List<DebloatLogEntry>> = _log.asStateFlow()

    /** Never offered: phone, settings, System UI, Shizuku, Play services/Store, the shell, ShizuBox. */
    private val lockedFixed = setOf(
        "android", "com.android.phone", "com.google.android.dialer", "com.android.server.telecom", "com.android.settings",
        "com.android.systemui", "com.android.shell", "moe.shizuku.privileged.api", "com.google.android.gms", "com.google.android.gsf",
        "com.android.vending", "com.google.android.permissioncontroller", "com.google.android.packageinstaller",
        "com.android.providers.settings", "com.android.networkstack", "com.google.android.networkstack", "com.android.bluetooth",
        "com.google.android.bluetooth", "com.android.nfc", "com.google.android.ext.services", "com.google.android.apps.nexuslauncher",
    )

    /** Pixel-specific ratings where the UAD rating is too relaxed (or too strict). */
    private val overrides = mapOf(
        "com.google.android.as" to DebloatRating.Caution, // Live Caption, Now Playing, smart replies
        "com.google.android.as.oss" to DebloatRating.Caution,
        "com.google.android.odad" to DebloatRating.Caution, // Play Protect
        "com.google.android.grilservice" to DebloatRating.DontTouch, // Pixel modem/telephony
        "com.google.android.apps.scone" to DebloatRating.Caution, // 5G/LTE switching
        "com.google.android.apps.work.clouddpc" to DebloatRating.Caution, // work profile
        "com.google.android.apps.restore" to DebloatRating.Caution,
        "com.google.android.apps.adm" to DebloatRating.Caution, // Find My Device: theft protection
        "com.google.android.gms.supervision" to DebloatRating.Caution,
        "com.google.android.carriersetup" to DebloatRating.Caution,
        "com.google.android.factoryota" to DebloatRating.Caution,
        "com.google.android.adservices.api" to DebloatRating.Caution,
        "com.google.mainline.adservices" to DebloatRating.Caution,
        "com.google.pixel.digitalkey.timesync" to DebloatRating.Caution,
        "com.google.android.ondevicepersonalization.services" to DebloatRating.Caution,
        "com.google.android.federatedcompute" to DebloatRating.Caution,
    )

    /** Our own explanations for the apps people ask about most. */
    private val explanations = mapOf(
        "com.google.android.youtube" to R.string.db_youtube,
        "com.google.android.apps.youtube.music" to R.string.db_ytmusic,
        "com.google.android.videos" to R.string.db_videos,
        "com.google.android.gm" to R.string.db_gmail,
        "com.google.android.apps.maps" to R.string.db_maps,
        "com.google.android.apps.docs" to R.string.db_drive,
        "com.google.android.apps.photos" to R.string.db_photos,
        "com.google.android.keep" to R.string.db_keep,
        "com.google.android.calendar" to R.string.db_calendar,
        "com.google.android.calculator" to R.string.db_calculator,
        "com.google.android.apps.recorder" to R.string.db_recorder,
        "com.google.android.apps.tips" to R.string.db_tips,
        "com.google.android.apps.bard" to R.string.db_gemini,
        "com.google.android.apps.weather" to R.string.db_weather,
        "com.google.ar.core" to R.string.db_arcore,
        "com.google.android.apps.tachyon" to R.string.db_meet,
        "com.google.android.apps.betterbug" to R.string.db_betterbug,
        "com.google.android.apps.pixel.support" to R.string.db_support,
        "com.android.egg" to R.string.db_egg,
        "com.android.traceur" to R.string.db_traceur,
        "com.android.hotwordenrollment.okgoogle" to R.string.db_hotword,
        "com.android.hotwordenrollment.xgoogle" to R.string.db_hotword,
        "com.google.android.as" to R.string.db_asi,
        "com.google.android.apps.adm" to R.string.db_adm,
        "com.google.android.apps.nbu.files" to R.string.db_files,
        "com.google.android.apps.aiwallpapers" to R.string.db_aiwall,
        "com.google.android.accessibility.soundamplifier" to R.string.db_amplifier,
        "com.android.providers.partnerbookmarks" to R.string.db_bookmarks,
        "com.google.android.feedback" to R.string.db_feedback,
        "com.google.android.gms.supervision" to R.string.db_familylink,
        "com.google.android.odad" to R.string.db_playprotect,
        "com.google.android.tag" to R.string.db_tag,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("debloat", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        _log.value = runCatching {
            val arr = JSONArray(prefs.getString("log", "[]"))
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { DebloatLogEntry(it.getString("pkg"), it.optString("label"), it.getLong("time"), it.optBoolean("undone")) }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLog(list: List<DebloatLogEntry>) {
        _log.value = list
        prefs.edit().putString("log", JSONArray(list.map {
            JSONObject().put("pkg", it.pkg).put("label", it.label).put("time", it.time).put("undone", it.undone)
        }).toString()).apply()
    }

    /** Packages that are locked on this device: fixed list plus the current launcher and keyboard. */
    private fun locked(): Set<String> {
        val pm = appContext.packageManager
        val home = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        }.getOrNull()
        val ime = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        return lockedFixed + setOfNotNull(home, ime, appContext.packageName)
    }

    private fun isLocked(pkg: String, locked: Set<String>) = pkg in locked || "shizuku" in pkg.lowercase()

    private fun rating(pkg: String, removal: String) = overrides[pkg] ?: when (removal) {
        "Recommended" -> DebloatRating.Safe
        "Advanced" -> DebloatRating.Caution
        else -> DebloatRating.DontTouch
    }

    /** Preinstalled apps of this device that are on the list, safest first. */
    suspend fun items(): List<DebloatItem> = withContext(Dispatchers.IO) {
        val arr = JSONArray(appContext.assets.open("debloat_uad.json").bufferedReader().use { it.readText() })
        val uad = (0 until arr.length()).associate { i -> arr.getJSONObject(i).let { it.getString("p") to (it.getString("r") to it.getString("d")) } }
        val locked = locked()
        Apps.list().filter { it.isSystem && it.pkg in uad }.map { a ->
            val (removal, desc) = uad.getValue(a.pkg)
            val lock = isLocked(a.pkg, locked)
            DebloatItem(
                pkg = a.pkg, label = a.label, rating = if (lock) DebloatRating.DontTouch else rating(a.pkg, removal),
                explainRes = explanations[a.pkg], uad = desc, frozen = a.frozen, locked = lock,
            )
        }.sortedWith(compareBy({ it.rating }, { it.explainRes == null }, { it.label.lowercase() }))
    }

    /** Freezes and logs; "don't touch" and locked apps are refused here as a second safety net. */
    suspend fun freeze(items: Collection<DebloatItem>) {
        val ok = items.filter { !it.locked && it.rating != DebloatRating.DontTouch }
        if (ok.isEmpty()) return
        Apps.freeze(ok.map { it.pkg })
        val now = System.currentTimeMillis()
        saveLog(ok.map { DebloatLogEntry(it.pkg, it.label, now, false) } + _log.value.filterNot { e -> ok.any { it.pkg == e.pkg } && !e.undone })
    }

    suspend fun undo(entries: Collection<DebloatLogEntry>) {
        if (entries.isEmpty()) return
        Apps.unfreeze(entries.map { it.pkg })
        val pk = entries.map { it.pkg }.toSet()
        saveLog(_log.value.map { if (it.pkg in pk && !it.undone) it.copy(undone = true) else it })
    }

    suspend fun undoAll() = undo(_log.value.filter { !it.undone })

    /** Forgets entries that were already undone. */
    fun clearUndone() = saveLog(_log.value.filter { !it.undone })
}
