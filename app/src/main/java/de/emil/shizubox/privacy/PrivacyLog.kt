package de.emil.shizubox.privacy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The sensitive things the log follows. */
enum class PrivOp(val op: String, val labelRes: Int, val permissions: List<String>, val discrete: Boolean) {
    Camera("CAMERA", R.string.pv_camera, listOf("android.permission.CAMERA"), true),
    Microphone("RECORD_AUDIO", R.string.pv_mic, listOf("android.permission.RECORD_AUDIO"), true),
    Location("FINE_LOCATION", R.string.pv_location, listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"), true),
    CoarseLocation("COARSE_LOCATION", R.string.pv_location_coarse, listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"), true),
    Contacts("READ_CONTACTS", R.string.pv_contacts, listOf("android.permission.READ_CONTACTS"), false),
    Clipboard("READ_CLIPBOARD", R.string.pv_clipboard, emptyList(), false),
    Sensors("BODY_SENSORS", R.string.pv_sensors, listOf("android.permission.BODY_SENSORS"), false),
    ;
    val label get() = t(labelRes)
    companion object { fun of(op: String) = entries.firstOrNull { it.op == op } }
}

data class PrivAccess(
    val id: Long,
    val pkg: String,
    val op: PrivOp,
    val time: Long,
    val durationMs: Long,
    val count: Int,
    /** Android's process state: top / fg / fgsvc / bg / cch / pers. */
    val state: String,
    val screenOff: Boolean,
) {
    /** In the background from the user's point of view (not on screen, not a visible service). */
    val background get() = state == "bg" || state == "cch"
    val service get() = state == "fgsvc"
}

data class PrivSettings(val enabled: Boolean = false, val keepDays: Int = 30, val notifyBackground: Boolean = false)

/**
 * Who used camera, microphone, location, contacts, clipboard and body sensors when – kept longer
 * than Android's own 24 h / 7 days. Sources (tested on Android 17 via Shizuku):
 * - `dumpsys appops --history --include-discrete 0 --op <OP>`: every single access with time,
 *   duration and process state, for camera, microphone and location (Android keeps 7 days;
 *   ~28 000 rows, 3 MB, 1.7 s for all four – so only rows newer than the last run are taken).
 * - `dumpsys appops --op <OP>` for contacts, clipboard, body sensors: only the latest access
 *   per app and state – each collection records what is new.
 * Location is very chatty (24 000 accesses a week), so it is merged into 10-minute blocks.
 * Collected hourly by [PrivacyWorker]; local database, not part of the backup.
 */
object PrivacyLog {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val db: SQLiteDatabase by lazy { Helper(appContext).writableDatabase }
    private val lock = Mutex()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _settings = MutableStateFlow(PrivSettings())
    val settings: StateFlow<PrivSettings> = _settings.asStateFlow()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("privacy", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        _settings.value = PrivSettings(
            enabled = prefs.getBoolean("enabled", false),
            keepDays = prefs.getInt("keep_days", 30),
            notifyBackground = prefs.getBoolean("notify_bg", false),
        )
    }

    fun update(block: (PrivSettings) -> PrivSettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit().putBoolean("enabled", s.enabled).putInt("keep_days", s.keepDays).putBoolean("notify_bg", s.notifyBackground).apply()
        PrivacyWorker.schedule(appContext)
    }

    // ------------------------------------------------------------------ collecting

    /** Pulls everything new since the last run. Returns the number of new entries. */
    suspend fun collect(): Int = lock.withLock {
        if (!ShizukuManager.isReady) return@withLock 0
        withContext(Dispatchers.IO) {
            val firstRun = PrivOp.entries.none { prefs.contains("since_${it.op}") }
            val oldest = PrivOp.entries.filter { it.discrete }.minOf { prefs.getString("since_${it.op}", "2000-01-01 00:00:00.000")!! }
            val screenOff = screenOffIntervals(sinceMillis(oldest))
            val inserted = mutableListOf<PrivAccess>()
            data class Key(val pkg: String, val op: String, val time: String, val state: String)
            val merged = linkedMapOf<Key, Pair<Int, Long>>()
            val newSince = mutableMapOf<String, String>()
            // One shell call per permission. awk keeps only rows newer than that permission's
            // `since` (string compare works for this format), merges the count and duration rows of
            // each access and puts location into 10-minute blocks (first 15 characters of the time);
            // sorted by time. The raw week is ~3 MB – far more than one binder reply carries – and
            // if the reply is ever cut, the next run continues after the last row that arrived.
            // Output: pkg|op|time|state|count|duration.
            for (o in PrivOp.entries.filter { it.discrete }) {
                val since = prefs.getString("since_${o.op}", "2000-01-01 00:00:00.000")!!
                val awk = "awk -F'|' -v t='$since' 'NF>12 && \$6 > t && \$1 != \"UID\" { " +
                    "k = (\$4 ~ /LOCATION/) ? \$2 \"|\" \$4 \"|\" substr(\$6,1,15) \"0:00.000|\" \$10 : \$2 \"|\" \$4 \"|\" \$6 \"|\" \$10; " +
                    "c[k] += \$7; d[k] += \$9; if (\$6 > m) m = \$6 } END { for (k in c) print k \"|\" c[k] \"|\" d[k]; print \"~NEWEST|\" m }'"
                val r = ShizukuManager.exec("dumpsys appops --history --include-discrete 0 --op ${o.op} | $awk | sort -t'|' -k3", timeoutMs = 5000, limitMs = 60_000)
                val cut = "output cut" in r.err
                var newest = since
                var lastRow = since
                for (line in r.out.lines()) {
                    val f = line.split('|')
                    if (f.size == 2 && f[0] == "~NEWEST") { if (f[1] > newest) newest = f[1]; continue }
                    if (f.size < 6) continue
                    merged[Key(f[0], f[1], f[2], f[3])] = (f[4].toIntOrNull() ?: 0) to (f[5].toLongOrNull() ?: 0)
                    if (f[2] > lastRow) lastRow = f[2]
                }
                newSince[o.op] = if (cut) lastRow else newest
            }
            db.beginTransaction()
            try {
                for ((k, v) in merged) {
                    val op = PrivOp.of(k.op) ?: continue
                    val time = runCatching { fmt.parse(k.time)!!.time }.getOrNull() ?: continue
                    // Location already arrives in 10-minute blocks (see awk above).
                    val start = time
                    val off = screenOff.any { time in it }
                    val updated = db.compileStatement(
                        "UPDATE a SET count = count + ?, duration = duration + ? WHERE pkg = ? AND op = ? AND start = ? AND state = ?",
                    ).apply {
                        bindLong(1, maxOf(1, v.first).toLong()); bindLong(2, v.second)
                        bindString(3, k.pkg); bindString(4, op.op); bindLong(5, start); bindString(6, k.state)
                    }.executeUpdateDelete()
                    if (updated == 0) {
                        val id = db.insert("a", null, ContentValues().apply {
                            put("pkg", k.pkg); put("op", op.op); put("start", start); put("duration", v.second)
                            put("count", maxOf(1, v.first)); put("state", k.state); put("screen_off", if (off) 1 else 0)
                        })
                        if (id > 0) inserted += PrivAccess(id, k.pkg, op, start, v.second, maxOf(1, v.first), k.state, off)
                    }
                }
                inserted += collectLastAccess(screenOff)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            prefs.edit().apply { newSince.forEach { (op, s) -> putString("since_$op", s) } }.apply()
            prune()
            if (inserted.isNotEmpty()) _version.value++
            if (!firstRun && _settings.value.notifyBackground) notifyBackground(inserted)
            inserted.size
        }
    }

    /** Contacts, clipboard, body sensors: Android only keeps the latest access per app and state. */
    private suspend fun collectLastAccess(screenOff: List<LongRange>): List<PrivAccess> {
        val ops = PrivOp.entries.filter { !it.discrete }
        // "~OP~" marks the sections; dumpsys itself prints lines starting with "#".
        val out = ShizukuManager.exec(ops.joinToString("; ") { "echo '~${it.op}~'; dumpsys appops --op ${it.op}" }, timeoutMs = 5000, limitMs = 30_000).out
        val access = Regex("""Access: \[(\w+)-[^\]]*] (\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})""")
        val result = mutableListOf<PrivAccess>()
        var op: PrivOp? = null
        var pkg: String? = null
        for (line in out.lines()) {
            val l = line.trim()
            when {
                l.startsWith("~") && l.endsWith("~") -> op = PrivOp.of(l.trim('~'))
                l.startsWith("Package ") -> pkg = l.removePrefix("Package ").removeSuffix(":")
                else -> access.find(l)?.let { m ->
                    val o = op ?: return@let
                    val p = pkg ?: return@let
                    val time = runCatching { fmt.parse(m.groupValues[2])!!.time }.getOrNull() ?: return@let
                    val state = m.groupValues[1]
                    val id = db.insertWithOnConflict("a", null, ContentValues().apply {
                        put("pkg", p); put("op", o.op); put("start", time); put("duration", 0); put("count", 1)
                        put("state", state); put("screen_off", if (screenOff.any { time in it }) 1 else 0)
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                    if (id > 0) result += PrivAccess(id, p, o, time, 0, 1, state, screenOff.any { time in it })
                }
            }
        }
        return result
    }

    private fun sinceMillis(s: String) = runCatching { fmt.parse(s)!!.time }.getOrDefault(0L)

    /** Screen-off periods from the usage events (SCREEN_NON_INTERACTIVE → SCREEN_INTERACTIVE). */
    private fun screenOffIntervals(from: Long): List<LongRange> = runCatching {
        val usm = appContext.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(maxOf(from, now - 8 * 86_400_000L) - 3_600_000L, now)
        val e = UsageEvents.Event()
        val list = mutableListOf<LongRange>()
        var offSince: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> offSince = e.timeStamp
                UsageEvents.Event.SCREEN_INTERACTIVE -> { offSince?.let { list += it..e.timeStamp }; offSince = null }
            }
        }
        offSince?.let { list += it..now }
        list
    }.getOrDefault(emptyList())

    private fun prune() {
        val days = _settings.value.keepDays
        if (days > 0) db.delete("a", "start < ?", arrayOf((System.currentTimeMillis() - days * 86_400_000L).toString()))
    }

    private fun notifyBackground(new: List<PrivAccess>) {
        val hits = new.filter { (it.op == PrivOp.Camera || it.op == PrivOp.Microphone) && (it.background || it.screenOff) && it.pkg != appContext.packageName }
        if (hits.isEmpty() || appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("privacy", t(R.string.pv_title), NotificationManager.IMPORTANCE_DEFAULT))
        val pm = appContext.packageManager
        val names = hits.map { a -> runCatching { pm.getApplicationInfo(a.pkg, 0).loadLabel(pm).toString() }.getOrDefault(a.pkg) + " (" + a.op.label + ")" }.distinct()
        val open = PendingIntent.getActivity(
            appContext, 81, Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "System").putExtra(EXTRA_PAGE, "privacy"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            12,
            android.app.Notification.Builder(appContext, "privacy")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(t(R.string.pv_notif_title))
                .setContentText(names.joinToString(", "))
                .setStyle(android.app.Notification.BigTextStyle().bigText(names.joinToString(", ")))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    const val EXTRA_PAGE = "system_page"

    // ------------------------------------------------------------------ queries

    private fun row(c: android.database.Cursor) = PrivAccess(
        c.getLong(0), c.getString(1), PrivOp.of(c.getString(2)) ?: PrivOp.Camera, c.getLong(3), c.getLong(4), c.getInt(5), c.getString(6), c.getInt(7) == 1,
    )

    /** Newest first; [ops] filters, [pkg] limits to one app, [onlyBackground] to background/screen-off accesses. */
    suspend fun entries(ops: Set<PrivOp>, pkg: String? = null, onlyBackground: Boolean = false, limit: Int = 500): List<PrivAccess> = withContext(Dispatchers.IO) {
        val where = mutableListOf("op IN (${ops.joinToString { "'${it.op}'" }})")
        val args = mutableListOf<String>()
        pkg?.let { where += "pkg = ?"; args += it }
        if (onlyBackground) where += "(state IN ('bg','cch') OR screen_off = 1)"
        db.rawQuery("SELECT id, pkg, op, start, duration, count, state, screen_off FROM a WHERE ${where.joinToString(" AND ")} ORDER BY start DESC LIMIT $limit", args.toTypedArray())
            .use { c -> buildList { while (c.moveToNext()) add(row(c)) } }
    }

    data class AppSummary(val pkg: String, val counts: Map<PrivOp, Int>, val background: Int, val last: Long)

    suspend fun perApp(ops: Set<PrivOp>): List<AppSummary> = withContext(Dispatchers.IO) {
        val map = linkedMapOf<String, MutableMap<PrivOp, Int>>()
        val bg = mutableMapOf<String, Int>()
        val last = mutableMapOf<String, Long>()
        db.rawQuery(
            "SELECT pkg, op, SUM(count), SUM(CASE WHEN state IN ('bg','cch') OR screen_off = 1 THEN count ELSE 0 END), MAX(start) FROM a " +
                "WHERE op IN (${ops.joinToString { "'${it.op}'" }}) GROUP BY pkg, op",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val p = c.getString(0)
                val o = PrivOp.of(c.getString(1)) ?: continue
                map.getOrPut(p) { mutableMapOf() }[o] = c.getInt(2)
                bg[p] = (bg[p] ?: 0) + c.getInt(3)
                last[p] = maxOf(last[p] ?: 0, c.getLong(4))
            }
        }
        map.map { (p, m) -> AppSummary(p, m, bg[p] ?: 0, last[p] ?: 0) }.sortedByDescending { it.last }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { db.rawQuery("SELECT COUNT(*) FROM a", null).use { it.moveToFirst(); it.getInt(0) } }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.delete("a", null, null)
        prefs.edit().apply { PrivOp.entries.forEach { remove("since_${it.op}") } }.apply()
        _version.value++
    }

    /** Takes the permission away (runtime permissions via `pm revoke`, clipboard via its app op). */
    suspend fun revoke(pkg: String, op: PrivOp) = ShizukuManager.exec(
        if (op == PrivOp.Clipboard) "appops set $pkg READ_CLIPBOARD ignore"
        else op.permissions.joinToString("; ") { "pm revoke $pkg $it 2>/dev/null" } + "; appops set $pkg ${op.op} ignore",
        timeoutMs = 5000,
    )

    private class Helper(context: Context) : SQLiteOpenHelper(context, "privacy.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE a (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, op TEXT NOT NULL, start INTEGER NOT NULL, " +
                    "duration INTEGER NOT NULL, count INTEGER NOT NULL, state TEXT NOT NULL, screen_off INTEGER NOT NULL, UNIQUE(pkg, op, start, state))",
            )
            db.execSQL("CREATE INDEX a_start ON a(start)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

/** Hourly collection while the log is on (only reads, needs Shizuku). */
class PrivacyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // School mode: nothing to do, and not even a shell call.
        if (de.emil.shizubox.school.SchoolMode.state.value.active) return Result.success()
        if (PrivacyLog.settings.value.enabled) runCatching { PrivacyLog.collect() }
        return Result.success()
    }

    companion object {
        private const val WORK = "privacy_log"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!PrivacyLog.settings.value.enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<PrivacyWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
