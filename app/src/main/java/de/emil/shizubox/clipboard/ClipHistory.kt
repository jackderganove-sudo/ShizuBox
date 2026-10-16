package de.emil.shizubox.clipboard

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.emil.shizubox.IClipCallback
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Clip(val id: Long, val text: String, val source: String, val time: Long, val pinned: Boolean) {
    val isLink get() = Regex("""^(https?://|www\.)\S+$""").matches(text.trim())
}

data class ClipSettings(val enabled: Boolean = false, val keepDays: Int = 30, val lock: Boolean = false, val excluded: Set<String> = emptySet())

/**
 * Clipboard history. Android 17 only lets the focused app or the keyboard read the clipboard –
 * but the shell user (Shizuku) holds READ_CLIPBOARD_IN_BACKGROUND (checked on the Pixel). So the
 * Shizuku service registers a clipboard listener (no polling) and hands each new clip to
 * ShizuBox. Never stored: clips marked sensitive (EXTRA_IS_SENSITIVE, e.g. passwords from a
 * keyboard or password manager), anything copied from a password manager, excluded apps.
 * Local only, not in the backup (only the settings are).
 */
object ClipHistory {
    /** Password managers: never recorded, whatever they mark. */
    private val PASSWORD_MANAGERS = setOf(
        "com.x8bit.bitwarden", "com.bitwarden.authenticator", "com.agilebits.onepassword", "com.lastpass.lpandroid", "com.dashlane",
        "com.kunzisoft.keepass.free", "com.kunzisoft.keepass.libre", "com.kunzisoft.keepass.pro", "keepass2android.keepass2android",
        "keepass2android.keepass2android_nonet", "proton.android.pass", "com.enpass", "io.enpass.app", "com.nordpass.android.app",
        "com.callpod.android_apps.keeper", "org.keepassdx", "com.google.android.gms.auth", "com.android.credentialmanager",
    )

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val db: SQLiteDatabase by lazy { Helper(appContext).writableDatabase }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(ClipSettings())
    val settings: StateFlow<ClipSettings> = _settings.asStateFlow()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val callback = object : IClipCallback.Stub() {
        override fun onClip(text: String, sourcePackage: String, sensitive: Boolean, time: Long) = record(text, sourcePackage, sensitive, time)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("clipboard", Context.MODE_PRIVATE)
        load()
        // Listen while it's on and Shizuku runs; register again after every Shizuku (re)connect.
        de.emil.shizubox.util.Unlock.whenUnlocked(appContext) {
            scope.launch {
                combine(_settings, ShizukuManager.status) { s, st -> s.enabled && st == ShizukuStatus.Ready }.collect { on ->
                    _listening.value = if (on) ShizukuManager.watchClipboard(callback) else { ShizukuManager.unwatchClipboard(); false }
                }
            }
        }
    }

    fun load() {
        _settings.value = ClipSettings(
            enabled = prefs.getBoolean("enabled", false),
            keepDays = prefs.getInt("keep_days", 30),
            lock = prefs.getBoolean("lock", false),
            excluded = prefs.getStringSet("excluded", emptySet()).orEmpty(),
        )
    }

    fun update(block: (ClipSettings) -> ClipSettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit().putBoolean("enabled", s.enabled).putInt("keep_days", s.keepDays).putBoolean("lock", s.lock).putStringSet("excluded", s.excluded).apply()
    }

    private fun record(text: String, source: String, sensitive: Boolean, time: Long) {
        val s = _settings.value
        if (!s.enabled || sensitive || text.isBlank()) return
        if (source in PASSWORD_MANAGERS || source in s.excluded) return
        scope.launch {
            // The same text again (e.g. copied twice) only moves to the top.
            val updated = db.update("c", ContentValues().apply { put("time", time); put("source", source) }, "text = ?", arrayOf(text))
            if (updated == 0) db.insert("c", null, ContentValues().apply { put("text", text.take(20_000)); put("source", source); put("time", time); put("pinned", 0) })
            prune()
            _version.value++
        }
    }

    private fun prune() {
        val days = _settings.value.keepDays
        if (days > 0) db.delete("c", "pinned = 0 AND time < ?", arrayOf((System.currentTimeMillis() - days * 86_400_000L).toString()))
        // At most 2000 unpinned entries.
        db.execSQL("DELETE FROM c WHERE pinned = 0 AND id NOT IN (SELECT id FROM c WHERE pinned = 0 ORDER BY time DESC LIMIT 2000)")
    }

    /**
     * Android shows "Shell pasted from your clipboard" once per new clip (the reader is the shell
     * user). The only switch is the system setting "Show clipboard access" – it applies to all
     * apps. Tested: `settings put secure clipboard_show_access_notifications 0` hides it; the
     * previous value is kept and restored when this is turned off again.
     */
    suspend fun setHideAccessToast(hide: Boolean) {
        if (hide) {
            if (!prefs.contains("toast_before")) {
                val before = ShizukuManager.exec("settings get secure clipboard_show_access_notifications").out.trim()
                prefs.edit().putString("toast_before", before).apply()
            }
            ShizukuManager.exec("settings put secure clipboard_show_access_notifications 0")
        } else {
            val before = prefs.getString("toast_before", "null") ?: "null"
            ShizukuManager.exec(
                if (before == "null" || before.isBlank()) "settings delete secure clipboard_show_access_notifications"
                else "settings put secure clipboard_show_access_notifications $before",
            )
            prefs.edit().remove("toast_before").apply()
        }
        _version.value++
    }

    fun hidesAccessToast() = prefs.contains("toast_before")

    suspend fun clips(query: String = ""): List<Clip> = withContext(Dispatchers.IO) {
        val (where, args) = if (query.isBlank()) "" to emptyArray() else "WHERE text LIKE ?" to arrayOf("%${query.trim()}%")
        db.rawQuery("SELECT id, text, source, time, pinned FROM c $where ORDER BY pinned DESC, time DESC LIMIT 500", args).use { c ->
            buildList { while (c.moveToNext()) add(Clip(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4) == 1)) }
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        db.update("c", ContentValues().apply { put("pinned", if (pinned) 1 else 0) }, "id = ?", arrayOf(id.toString()))
        _version.value++
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { db.delete("c", "id = ?", arrayOf(id.toString())); _version.value++ }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { db.delete("c", "pinned = 0", null); _version.value++ }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "clipboard.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE c (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, source TEXT NOT NULL, time INTEGER NOT NULL, pinned INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX c_time ON c(time)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
