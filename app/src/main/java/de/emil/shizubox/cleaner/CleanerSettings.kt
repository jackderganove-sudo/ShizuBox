package de.emil.shizubox.cleaner

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Categories of the system cleaner; [safe] ones may be cleaned automatically by default. */
enum class SysCat(val safe: Boolean) {
    Thumbnails(true), EmptyDirs(true), LogsTemp(true), Partial(true), OwnLeftovers(true), OldApks(false), ForeignTrash(false),
}

/** What a scheduled/quick clean may touch: the system categories plus these. */
object AutoKeys {
    const val APP_CACHES = "AppCaches"
    const val CORPSES = "Corpses"
    const val DUPLICATES = "Duplicates"
    val DEFAULT = SysCat.entries.filter { it.safe }.map { it.name }.toSet() + APP_CACHES
}

data class CleanerConfig(
    val trashDays: Int = 7,
    val excludedPaths: Set<String> = emptySet(),
    val excludedApps: Set<String> = emptySet(),
    /** AI models (AICore) hide in its "cache"; never cleaned unless switched on here. */
    val includeAiCore: Boolean = false,
    val sysCats: Set<String> = SysCat.entries.map { it.name }.toSet(),
    val dupeFolders: Set<String> = DEFAULT_DUPE_FOLDERS,
    val dupeMinKb: Int = 100,
    val dupeSimilar: Boolean = false,
    val schedule: Boolean = false,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val scheduleDays: Int = 1 shl 6,
    val scheduleMinute: Int = 3 * 60,
    val scheduleCharging: Boolean = true,
    val autoKeys: Set<String> = AutoKeys.DEFAULT,
) {
    companion object {
        val DEFAULT_DUPE_FOLDERS = setOf(
            "DCIM", "Pictures", "Download", "Movies", "Music",
            "Android/media/com.whatsapp/WhatsApp/Media", "Android/media/com.snapchat.android",
        )
    }
}

/** Cleaner settings (device-protected prefs "cleaner", part of the backup – trash and results are not). */
object CleanerSettings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(CleanerConfig())
    val state: StateFlow<CleanerConfig> = _state.asStateFlow()
    val current get() = _state.value

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("cleaner", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        val d = CleanerConfig()
        _state.value = CleanerConfig(
            trashDays = prefs.getInt("trash_days", d.trashDays),
            excludedPaths = prefs.getStringSet("excl_paths", d.excludedPaths)!!.toSet(),
            excludedApps = prefs.getStringSet("excl_apps", d.excludedApps)!!.toSet(),
            includeAiCore = prefs.getBoolean("include_aicore", d.includeAiCore),
            sysCats = prefs.getStringSet("sys_cats", d.sysCats)!!.toSet(),
            dupeFolders = prefs.getStringSet("dupe_folders", d.dupeFolders)!!.toSet(),
            dupeMinKb = prefs.getInt("dupe_min_kb", d.dupeMinKb),
            dupeSimilar = prefs.getBoolean("dupe_similar", d.dupeSimilar),
            schedule = prefs.getBoolean("schedule", d.schedule),
            scheduleDays = prefs.getInt("schedule_days", d.scheduleDays),
            scheduleMinute = prefs.getInt("schedule_minute", d.scheduleMinute),
            scheduleCharging = prefs.getBoolean("schedule_charging", d.scheduleCharging),
            autoKeys = prefs.getStringSet("auto_keys", d.autoKeys)!!.toSet(),
        )
    }

    fun update(f: (CleanerConfig) -> CleanerConfig) {
        val c = f(_state.value)
        _state.value = c
        prefs.edit()
            .putInt("trash_days", c.trashDays)
            .putStringSet("excl_paths", c.excludedPaths)
            .putStringSet("excl_apps", c.excludedApps)
            .putBoolean("include_aicore", c.includeAiCore)
            .putStringSet("sys_cats", c.sysCats)
            .putStringSet("dupe_folders", c.dupeFolders)
            .putInt("dupe_min_kb", c.dupeMinKb)
            .putBoolean("dupe_similar", c.dupeSimilar)
            .putBoolean("schedule", c.schedule)
            .putInt("schedule_days", c.scheduleDays)
            .putInt("schedule_minute", c.scheduleMinute)
            .putBoolean("schedule_charging", c.scheduleCharging)
            .putStringSet("auto_keys", c.autoKeys)
            .apply()
    }
}
