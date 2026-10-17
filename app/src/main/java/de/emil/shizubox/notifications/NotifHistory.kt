package de.emil.shizubox.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What happened to a stored message after it was shown. */
enum class NState {
    /** Still in the notification shade. */
    Live,
    /** Opened or swiped away by the user, or simply replaced. */
    Gone,
    /** The app removed the notification before the user touched it (deleted, unsent, or read elsewhere). */
    Withdrawn,
    /** The message disappeared from an updated conversation while newer ones stayed: deleted by the sender. */
    Deleted,
}

data class NEntry(
    val id: Long,
    val pkg: String,
    /** Chat name for messages, otherwise the notification title. */
    val conv: String,
    val sender: String,
    val text: String,
    val time: Long,
    val state: NState,
    val message: Boolean,
    val image: String?,
)

data class NApp(val pkg: String, val count: Int, val last: Long, val deleted: Int)
data class NConv(val pkg: String, val conv: String, val count: Int, val last: Long, val lastText: String, val deleted: Int)

data class NHistorySettings(
    val enabled: Boolean = false,
    /** 0 = keep forever. */
    val keepDays: Int = 30,
    val lock: Boolean = false,
    val excluded: Set<String> = emptySet(),
)

/**
 * Local notification history. Stays on the device: kept in its own database outside the
 * normal backup (only the settings are backed up).
 */
object NotifHistory {
    /** Tested on Android 17: `cmd notification allow_listener <component>` grants access immediately. */
    fun component(context: Context) = ComponentName(context, HistoryListener::class.java)

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    // Personal data: credential-protected storage, so only opened after the first unlock
    // (the app itself also starts before that, for school mode).
    private val db: SQLiteDatabase by lazy { Helper(appContext).writableDatabase }
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private var lastPrune = 0L

    private val _settings = MutableStateFlow(NHistorySettings())
    val settings: StateFlow<NHistorySettings> = _settings.asStateFlow()
    /** Bumped on every change, so open lists reload. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("notif_history", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        _settings.value = NHistorySettings(
            enabled = prefs.getBoolean("enabled", false),
            keepDays = prefs.getInt("keep_days", 30),
            lock = prefs.getBoolean("lock", false),
            excluded = prefs.getStringSet("excluded", emptySet()).orEmpty(),
        )
    }

    fun update(block: (NHistorySettings) -> NHistorySettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit().putBoolean("enabled", s.enabled).putInt("keep_days", s.keepDays).putBoolean("lock", s.lock)
            .putStringSet("excluded", s.excluded).apply()
    }

    fun hasAccess(context: Context) =
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))

    /** Grants (or revokes) listener access through Shizuku. False if that was not possible. */
    suspend fun setAccess(context: Context, on: Boolean): Boolean {
        if (!ShizukuManager.isReady) return false
        val cn = component(context).flattenToString()
        ShizukuManager.exec(if (on) "cmd notification allow_listener $cn" else "cmd notification disallow_listener $cn")
        return hasAccess(context) == on
    }

    // ------------------------------------------------------------------ recording

    private fun excluded(pkg: String) = pkg == appContext.packageName || pkg in _settings.value.excluded

    fun posted(sbn: StatusBarNotification) {
        if (!_settings.value.enabled || excluded(sbn.packageName)) return
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return
        // Extract on the calling thread (the bundle may be recycled), write on the db thread.
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (style != null && style.messages.isNotEmpty()) {
            val conv = cleanConv(style.conversationTitle?.toString() ?: title)
            val msgs = style.messages.map { m ->
                Triple(m.person?.name?.toString() ?: style.user.name?.toString().orEmpty(), m, m.text?.toString().orEmpty())
            }
            scope.launch { storeMessages(sbn.packageName, sbn.key, conv, msgs) }
        } else {
            val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return
            val picture = picture(n)
            val time = if (n.`when` > 0) n.`when` else sbn.postTime
            scope.launch { storePlain(sbn.packageName, sbn.key, title.ifBlank { label(sbn.packageName) }, text, time, picture) }
        }
    }

    fun removed(sbn: StatusBarNotification, reason: Int) {
        if (!_settings.value.enabled) return
        val appCancel = reason == NotificationListenerService.REASON_APP_CANCEL || reason == NotificationListenerService.REASON_APP_CANCEL_ALL
        scope.launch {
            // Apps also clear their notifications when the user simply opens them: that is not "withdrawn".
            val byApp = appCancel && !recentlyOpened(sbn.packageName)
            val v = ContentValues().apply { put("state", (if (byApp) NState.Withdrawn else NState.Gone).ordinal); put("changed", System.currentTimeMillis()) }
            if (db.update("n", v, "nkey = ? AND state = ?", arrayOf(sbn.key, NState.Live.ordinal.toString())) > 0) bump()
        }
    }

    /** Was [pkg] in the foreground during the last 20 s? Needs usage access (granted for screen time). */
    private fun recentlyOpened(pkg: String): Boolean = runCatching {
        val usm = appContext.getSystemService(android.app.usage.UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 20_000, now)
        val e = android.app.usage.UsageEvents.Event()
        var opened = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == pkg && e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) opened = true
        }
        opened
    }.getOrDefault(false)

    /** WhatsApp & co. append "(3 messages)" to group titles. */
    private fun cleanConv(s: String) = s.replace(Regex("""\s*\(\d+[^)]*\)\s*$"""), "").replace(Regex("""\s*:\s*\d+\s+\S+$"""), "").trim()

    private fun storeMessages(pkg: String, key: String, conv: String, msgs: List<Triple<String, NotificationCompat.MessagingStyle.Message, String>>) {
        val now = System.currentTimeMillis()
        val seen = HashSet<Pair<String, Long>>()
        db.beginTransaction()
        try {
            for ((sender, m, text) in msgs) {
                seen += sender to m.timestamp
                val image = if (m.dataMimeType?.startsWith("image/") == true) m.dataUri?.let { uri ->
                    runCatching { appContext.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
                } else null
                val v = ContentValues().apply {
                    put("pkg", pkg); put("nkey", key); put("conv", conv); put("sender", sender); put("text", text)
                    put("time", m.timestamp); put("state", NState.Live.ordinal); put("msg", 1); put("changed", now)
                }
                val id = db.insertWithOnConflict("n", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                if (id > 0) {
                    image?.let { saveImage(id, it) }
                } else {
                    // Known message: it is still visible under the (possibly new) key.
                    db.execSQL(
                        "UPDATE n SET nkey = ?, state = CASE WHEN state = ${NState.Deleted.ordinal} THEN state ELSE ${NState.Live.ordinal} END " +
                            "WHERE pkg = ? AND conv = ? AND sender = ? AND time = ?",
                        arrayOf<Any>(key, pkg, conv, sender, m.timestamp),
                    )
                }
            }
            // Messages of this notification that are no longer part of it: either scrolled out of
            // the app's window (older than everything shown now) or removed – deleted by the sender.
            val oldest = msgs.minOf { it.second.timestamp }
            db.rawQuery("SELECT id, sender, time FROM n WHERE nkey = ? AND msg = 1 AND state = ?", arrayOf(key, NState.Live.ordinal.toString())).use { c ->
                while (c.moveToNext()) {
                    if ((c.getString(1) to c.getLong(2)) in seen) continue
                    val state = if (c.getLong(2) >= oldest) NState.Deleted else NState.Gone
                    db.update("n", ContentValues().apply { put("state", state.ordinal); put("changed", now) }, "id = ?", arrayOf(c.getLong(0).toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        afterWrite()
    }

    private fun storePlain(pkg: String, key: String, title: String, text: String, time: Long, picture: Bitmap?) {
        // Same notification updated with new text: the previous version counts as replaced, not withdrawn.
        db.update("n", ContentValues().apply { put("state", NState.Gone.ordinal) }, "nkey = ? AND msg = 0 AND state = ? AND NOT (conv = ? AND text = ?)",
            arrayOf(key, NState.Live.ordinal.toString(), title, text))
        val v = ContentValues().apply {
            put("pkg", pkg); put("nkey", key); put("conv", title); put("sender", ""); put("text", text)
            put("time", time); put("state", NState.Live.ordinal); put("msg", 0); put("changed", System.currentTimeMillis())
        }
        val id = db.insertWithOnConflict("n", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        if (id > 0) picture?.let { saveImage(id, it) }
        afterWrite()
    }

    private fun picture(n: Notification): Bitmap? = runCatching {
        @Suppress("DEPRECATION")
        (n.extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
            ?: ((n.extras.getParcelable(Notification.EXTRA_PICTURE_ICON) as? Icon)?.loadDrawable(appContext) as? BitmapDrawable)?.bitmap
    }.getOrNull()

    private fun imageDir() = File(appContext.filesDir, "notif_images").apply { mkdirs() }

    private fun saveImage(id: Long, bmp: Bitmap) {
        val f = File(imageDir(), "$id.jpg")
        runCatching {
            // Keep files small: at most 1600 px on the long side.
            val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
            val b = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            db.update("n", ContentValues().apply { put("image", f.absolutePath) }, "id = ?", arrayOf(id.toString()))
        }
    }

    private fun afterWrite() {
        bump()
        val now = System.currentTimeMillis()
        if (now - lastPrune > 3_600_000) {
            lastPrune = now
            pruneNow()
        }
    }

    private fun bump() {
        _version.value = _version.value + 1
    }

    private fun pruneNow() {
        val days = _settings.value.keepDays
        if (days > 0) deleteWhere("time < ?", arrayOf((System.currentTimeMillis() - days * 86_400_000L).toString()))
        // Hard caps also with "keep forever": at most MAX_ROWS entries and MAX_IMAGE_BYTES of pictures
        // (oldest pictures go first; the text of those entries stays).
        val rows = db.rawQuery("SELECT COUNT(*) FROM n", null).use { it.moveToFirst(); it.getLong(0) }
        if (rows > MAX_ROWS) {
            deleteWhere("id IN (SELECT id FROM n ORDER BY time ASC LIMIT ?)", arrayOf((rows - MAX_ROWS).toString()))
        }
        var bytes = imageDir().listFiles().orEmpty().sumOf { it.length() }
        if (bytes > MAX_IMAGE_BYTES) {
            db.rawQuery("SELECT id, image FROM n WHERE image IS NOT NULL ORDER BY time ASC", null).use { c ->
                while (bytes > MAX_IMAGE_BYTES * 8 / 10 && c.moveToNext()) {
                    val f = File(c.getString(1))
                    bytes -= f.length()
                    f.delete()
                    db.execSQL("UPDATE n SET image = NULL WHERE id = ?", arrayOf<Any>(c.getLong(0)))
                }
            }
            bump()
        }
    }

    private const val MAX_ROWS = 50_000L
    private const val MAX_IMAGE_BYTES = 300L * 1024 * 1024

    /** Database + pictures on disk, for the settings. */
    suspend fun storageBytes(): Long = withContext(io) {
        appContext.getDatabasePath("notif_history.db").let { it.length() + File(it.path + "-wal").length() } +
            imageDir().listFiles().orEmpty().sumOf { it.length() }
    }

    private fun deleteWhere(where: String?, args: Array<String>?) {
        db.query("n", arrayOf("image"), (where?.let { "($it) AND " } ?: "") + "image IS NOT NULL", args, null, null, null).use { c ->
            while (c.moveToNext()) File(c.getString(0)).delete()
        }
        db.delete("n", where, args)
        bump()
    }

    // ------------------------------------------------------------------ queries / maintenance

    private fun label(pkg: String) = runCatching {
        appContext.packageManager.getApplicationInfo(pkg, 0).loadLabel(appContext.packageManager).toString()
    }.getOrDefault(pkg)

    private fun entry(c: android.database.Cursor) = NEntry(
        id = c.getLong(0), pkg = c.getString(1), conv = c.getString(2), sender = c.getString(3).orEmpty(), text = c.getString(4).orEmpty(),
        time = c.getLong(5), state = NState.entries[c.getInt(6)], message = c.getInt(7) == 1, image = c.getString(8),
    )

    private const val COLS = "id, pkg, conv, sender, text, time, state, msg, image"

    /** Newest first; [query] searches chat name, sender and text. */
    suspend fun entries(query: String = "", pkg: String? = null, onlyRemoved: Boolean = false, limit: Int = 300): List<NEntry> = withContext(io) {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (query.isNotBlank()) {
            where += "(conv LIKE ? OR sender LIKE ? OR text LIKE ?)"
            repeat(3) { args += "%${query.trim()}%" }
        }
        pkg?.let { where += "pkg = ?"; args += it }
        if (onlyRemoved) where += "state IN (${NState.Withdrawn.ordinal}, ${NState.Deleted.ordinal})"
        db.rawQuery(
            "SELECT $COLS FROM n ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")} ORDER BY time DESC LIMIT $limit",
            args.toTypedArray(),
        ).use { c -> buildList { while (c.moveToNext()) add(entry(c)) } }
    }

    suspend fun apps(): List<NApp> = withContext(io) {
        db.rawQuery(
            "SELECT pkg, COUNT(*), MAX(time), SUM(state = ${NState.Deleted.ordinal} OR state = ${NState.Withdrawn.ordinal}) FROM n GROUP BY pkg ORDER BY MAX(time) DESC",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(NApp(c.getString(0), c.getInt(1), c.getLong(2), c.getInt(3))) } }
    }

    /** All chats of one app, merged across the app's notifications. */
    suspend fun conversations(pkg: String): List<NConv> = withContext(io) {
        db.rawQuery(
            "SELECT conv, COUNT(*), MAX(time), SUM(state = ${NState.Deleted.ordinal} OR state = ${NState.Withdrawn.ordinal}), " +
                "(SELECT text FROM n AS i WHERE i.pkg = o.pkg AND i.conv = o.conv ORDER BY time DESC LIMIT 1) " +
                "FROM n AS o WHERE pkg = ? GROUP BY conv ORDER BY MAX(time) DESC",
            arrayOf(pkg),
        ).use { c -> buildList { while (c.moveToNext()) add(NConv(pkg, c.getString(0), c.getInt(1), c.getLong(2), c.getString(4).orEmpty(), c.getInt(3))) } }
    }

    /** One chat, oldest first. */
    suspend fun thread(pkg: String, conv: String): List<NEntry> = withContext(io) {
        db.rawQuery("SELECT $COLS FROM n WHERE pkg = ? AND conv = ? ORDER BY time ASC", arrayOf(pkg, conv))
            .use { c -> buildList { while (c.moveToNext()) add(entry(c)) } }
    }

    suspend fun count(): Int = withContext(io) { db.rawQuery("SELECT COUNT(*) FROM n", null).use { it.moveToFirst(); it.getInt(0) } }

    suspend fun deleteAll() = withContext(io) {
        deleteWhere(null, null)
        imageDir().listFiles()?.forEach { it.delete() }
    }

    suspend fun deleteApp(pkg: String) = withContext(io) { deleteWhere("pkg = ?", arrayOf(pkg)) }

    suspend fun prune() = withContext(io) { pruneNow() }

    /** Takes the notifications that are already showing when the listener connects. */
    fun ingestActive(active: Array<StatusBarNotification>?) {
        active?.forEach { runCatching { posted(it) } }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "notif_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE n (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, nkey TEXT, conv TEXT NOT NULL, " +
                    "sender TEXT NOT NULL, text TEXT NOT NULL, time INTEGER NOT NULL, state INTEGER NOT NULL, msg INTEGER NOT NULL, " +
                    "image TEXT, changed INTEGER, UNIQUE(pkg, conv, sender, time, text))",
            )
            db.execSQL("CREATE INDEX n_time ON n(time)")
            db.execSQL("CREATE INDEX n_key ON n(nkey)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

/** Records every notification into [NotifHistory]. */
class HistoryListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        NotifHistory.ingestActive(runCatching { activeNotifications }.getOrNull())
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    companion object {
        /** For the debug receiver: removes its own test notifications again. */
        @Volatile var instance: HistoryListener? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotifHistory.posted(sbn)
        runCatching { de.emil.shizubox.hilight.HiLightNotifications.posted(this, sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        NotifHistory.removed(sbn, reason)
        runCatching { de.emil.shizubox.hilight.HiLightNotifications.removed(sbn) }
    }
}
