package de.emil.shizubox.battery

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Battery use of one app (UID) in a time window. */
data class AppDrain(
    val uid: Int,
    val mAh: Double,
    val fg: Double,
    val bg: Double,
    val fgs: Double,
    val cached: Double,
    val wakelockMs: Long,
    val mobileBytes: Long,
    val wakeups: Int,
)

data class LevelSample(val time: Long, val level: Int, val charging: Boolean)

enum class DrainWindow { SinceCharge, Day, Night }

data class BatterySettings(val nightStart: Int = 23, val nightEnd: Int = 7, val morningNotify: Boolean = false)

/**
 * Which apps used the battery. Source (tested on Android 17 via Shizuku): the "Estimated power
 * use (mAh)" section of `dumpsys batterystats --charged` – per UID total plus fg/bg/fgs/cached –
 * and the per-UID blocks for "TOTAL wake" (blamed partial wake locks), "Mobile network" and
 * "Wakeup alarm" (about 1 s together). `--checkin` is avoided: it may hand over and delete
 * Android's stored history. Android only reports "since the last charge", so ShizuBox stores an
 * hourly snapshot and computes other windows (24 h, night) as differences; the counters restart
 * when "Start clock time" changes. Battery level is sampled every 15 minutes without Shizuku.
 */
object BatteryReport {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val db: SQLiteDatabase by lazy { Helper(appContext).writableDatabase }
    private val lock = Mutex()

    private val _settings = MutableStateFlow(BatterySettings())
    val settings: StateFlow<BatterySettings> = _settings.asStateFlow()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("battery_report", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        _settings.value = BatterySettings(
            nightStart = prefs.getInt("night_start", 23),
            nightEnd = prefs.getInt("night_end", 7),
            morningNotify = prefs.getBoolean("morning_notify", false),
        )
    }

    fun update(block: (BatterySettings) -> BatterySettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit().putInt("night_start", s.nightStart).putInt("night_end", s.nightEnd).putBoolean("morning_notify", s.morningNotify).apply()
        scheduleNight(appContext)
    }

    // ------------------------------------------------------------------ sampling

    /** Battery level from the sticky broadcast – no Shizuku, no wake-up. */
    fun sampleLevel() {
        val i = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level < 0) return
        val now = System.currentTimeMillis()
        db.insert("level", null, ContentValues().apply { put("t", now); put("level", level); put("charging", if (charging) 1 else 0) })
        // Also pruned here: without Shizuku no snapshot runs, and the table grew forever (0.11.1).
        db.delete("level", "t < ?", arrayOf((now - 30 * 86_400_000L).toString()))
    }

    /** Snapshot of the per-app counters (needs Shizuku). Returns false if it could not be taken. */
    suspend fun snapshot(): Boolean = lock.withLock {
        if (!ShizukuManager.isReady) return@withLock false
        withContext(Dispatchers.IO) {
            val script = "dumpsys batterystats --charged | awk '" +
                "/Start clock time:/ {print \"#START|\" \$NF} " +
                "/^  Discharge: [0-9]/ {print \"#DIS|\" \$2} /Capacity: [0-9]+, Computed drain/ {print \"#CAP|\" \$2} " +
                "/Estimated power use/ {p=1} p && /^ *\$/ {p=0} p && /UID / {print \"#P|\" \$0} " +
                "/^  (u[0-9]+a[0-9]+|[0-9]+):\$/ {u=\$1} " +
                "/TOTAL wake:|Mobile network:|Wakeup alarm/ {print \"#U|\" u \"|\" \$0}'"
            val out = ShizukuManager.exec(script, timeoutMs = 5000, limitMs = 60_000).out
            val now = System.currentTimeMillis()
            var start = ""
            var discharge = 0.0
            var capacity = 0.0
            val power = mutableMapOf<Int, DoubleArray>()
            val extra = mutableMapOf<Int, LongArray>()
            for (line in out.lines()) {
                when {
                    line.startsWith("#START|") -> start = line.substringAfter('|').trim()
                    line.startsWith("#DIS|") -> discharge = num(line.substringAfter('|'))
                    line.startsWith("#CAP|") -> capacity = num(line.substringAfter('|').trim(','))
                    line.startsWith("#P|") -> parsePower(line.substringAfter('|'))?.let { (uid, v) -> power[uid] = v }
                    line.startsWith("#U|") -> {
                        val rest = line.substringAfter('|')
                        val uid = parseUid(rest.substringBefore('|').removeSuffix(":")) ?: continue
                        val text = rest.substringAfter('|')
                        val e = extra.getOrPut(uid) { LongArray(3) }
                        when {
                            "TOTAL wake:" in text -> e[0] += Regex("""([\d hms]+?ms|[\d hms]+s) blamed partial""").find(text)?.let { durationMs(it.groupValues[1]) } ?: 0
                            "Mobile network:" in text -> e[1] += Regex("""([\d.,]+)\s*([KMG]?B) (received|sent)""").findAll(text).sumOf { bytes(it.groupValues[1], it.groupValues[2]) }
                            "Wakeup alarm" in text -> e[2] += Regex("""(\d+) times""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                        }
                    }
                }
            }
            if (power.isEmpty()) return@withContext false
            db.beginTransaction()
            try {
                for ((uid, v) in power) {
                    val e = extra[uid] ?: LongArray(3)
                    db.insert("snap", null, ContentValues().apply {
                        put("t", now); put("epoch", start); put("uid", uid)
                        put("total", v[0]); put("fg", v[1]); put("bg", v[2]); put("fgs", v[3]); put("cached", v[4])
                        put("wl", e[0]); put("mobile", e[1]); put("wakeups", e[2])
                    })
                }
                // 30 days are plenty for "since charge", 24 h and night.
                val old = (now - 30 * 86_400_000L).toString()
                db.delete("snap", "t < ?", arrayOf(old))
                db.delete("level", "t < ?", arrayOf(old))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            // "Since charge" comes from Android itself: its counting start and the drained charge.
            val startMs = runCatching { java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US).parse(start)!!.time }.getOrDefault(0L)
            prefs.edit().putLong("last_snapshot", now).putLong("charge_start", startMs)
                .putFloat("discharge", discharge.toFloat()).putFloat("capacity", capacity.toFloat()).apply()
            _version.value++
            true
        }
    }

    fun lastSnapshot() = prefs.getLong("last_snapshot", 0)

    private fun parseUid(s: String): Int? {
        Regex("""u(\d+)a(\d+)""").matchEntire(s)?.let { return it.groupValues[1].toInt() * 100_000 + 10_000 + it.groupValues[2].toInt() }
        return s.toIntOrNull()
    }

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0

    /** "UID u0a421: 213 fg: 56.7 (3h 6m …) bg: 0.317 (…) fgs: 0.316 (…) cached: 10.1 (…)" */
    private fun parsePower(line: String): Pair<Int, DoubleArray>? {
        val m = Regex("""UID ([u\da]+): ([\d.,]+)""").find(line) ?: return null
        val uid = parseUid(m.groupValues[1]) ?: return null
        fun part(name: String) = Regex("""$name: ([\d.,]+)""").find(line)?.groupValues?.get(1)?.let(::num) ?: 0.0
        return uid to doubleArrayOf(num(m.groupValues[2]), part("fg"), part(" bg"), part("fgs"), part("cached"))
    }

    /** "1h 2m 3s 45ms" → milliseconds. */
    fun durationMs(s: String): Long = Regex("""(\d+)(ms|h|m|s|d)""").findAll(s).sumOf {
        val n = it.groupValues[1].toLong()
        when (it.groupValues[2]) { "d" -> n * 86_400_000; "h" -> n * 3_600_000; "m" -> n * 60_000; "s" -> n * 1000; else -> n }
    }

    private fun bytes(v: String, unit: String): Long = (num(v) * when (unit) { "KB" -> 1024.0; "MB" -> 1048576.0; "GB" -> 1073741824.0; else -> 1.0 }).toLong()

    // ------------------------------------------------------------------ queries

    fun window(w: DrainWindow, now: Long = System.currentTimeMillis()): LongRange = when (w) {
        DrainWindow.SinceCharge -> (prefs.getLong("charge_start", 0).takeIf { it > 0 } ?: lastChargeEnd())..now
        DrainWindow.Day -> (now - 86_400_000L)..now
        DrainWindow.Night -> nightWindow(now)
    }

    /** The last night window that has ended (or today's if it's still night, up to now). */
    fun nightWindow(now: Long): LongRange {
        val s = _settings.value
        val end = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, s.nightEnd); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now) add(Calendar.DAY_OF_YEAR, -1)
        }
        val start = (end.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, s.nightStart)
            if (timeInMillis >= end.timeInMillis) add(Calendar.DAY_OF_YEAR, -1)
        }
        return start.timeInMillis..end.timeInMillis
    }

    /** When the phone was last unplugged after charging (from the level samples). */
    private fun lastChargeEnd(): Long = db.rawQuery("SELECT MAX(t) FROM level WHERE charging = 1", null).use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
    }

    /**
     * Per-app use in [range]: sums the growth of every app's counters between consecutive
     * snapshots inside the window; a new "Start clock time" means Android restarted counting.
     * "Since charge" simply takes the latest snapshot as it is.
     */
    suspend fun drain(w: DrainWindow): List<AppDrain> = withContext(Dispatchers.IO) {
        if (w == DrainWindow.SinceCharge) {
            val t = db.rawQuery("SELECT MAX(t) FROM snap", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } ?: return@withContext emptyList()
            return@withContext db.rawQuery("SELECT uid, total, fg, bg, fgs, cached, wl, mobile, wakeups FROM snap WHERE t = ?", arrayOf(t.toString())).use { c ->
                buildList { while (c.moveToNext()) add(AppDrain(c.getInt(0), c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getLong(6), c.getLong(7), c.getInt(8))) }
            }.filter { it.mAh > 0.01 }.sortedByDescending { it.mAh }
        }
        val range = window(w)
        // One snapshot before the window as the base, then everything inside it.
        val times = db.rawQuery(
            "SELECT DISTINCT t, epoch FROM snap WHERE t >= COALESCE((SELECT MAX(t) FROM snap WHERE t <= ?), 0) AND t <= ? ORDER BY t",
            arrayOf(range.first.toString(), range.last.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }
        if (times.size < 2) return@withContext emptyList()
        fun at(t: Long) = db.rawQuery("SELECT uid, total, fg, bg, fgs, cached, wl, mobile, wakeups FROM snap WHERE t = ?", arrayOf(t.toString())).use { c ->
            buildMap { while (c.moveToNext()) put(c.getInt(0), doubleArrayOf(c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getLong(6).toDouble(), c.getLong(7).toDouble(), c.getInt(8).toDouble())) }
        }
        val sum = mutableMapOf<Int, DoubleArray>()
        var prev = at(times[0].first)
        for (i in 1 until times.size) {
            val cur = at(times[i].first)
            val reset = times[i].second != times[i - 1].second
            for ((uid, v) in cur) {
                val p = if (reset) null else prev[uid]
                val acc = sum.getOrPut(uid) { DoubleArray(8) }
                for (k in 0 until 8) acc[k] += (v[k] - (p?.get(k) ?: 0.0)).coerceAtLeast(0.0)
            }
            prev = cur
        }
        sum.map { (uid, v) -> AppDrain(uid, v[0], v[1], v[2], v[3], v[4], v[5].toLong(), v[6].toLong(), v[7].toInt()) }
            .filter { it.mAh > 0.01 }.sortedByDescending { it.mAh }
    }

    suspend fun levels(from: Long): List<LevelSample> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT t, level, charging FROM level WHERE t >= ? ORDER BY t", arrayOf(from.toString())).use { c ->
            buildList { while (c.moveToNext()) add(LevelSample(c.getLong(0), c.getInt(1), c.getInt(2) == 1)) }
        }
    }

    /** Percent used since the last charge, from Android's drained mAh and the battery capacity. */
    fun sinceChargePercent(): Int? {
        val cap = prefs.getFloat("capacity", 0f)
        return if (cap > 0) (prefs.getFloat("discharge", 0f) / cap * 100).toInt() else null
    }

    /** Level drop (percent points) in [range], ignoring time spent charging. */
    suspend fun drop(range: LongRange): Int {
        val l = levels(range.first - 15 * 60_000).filter { it.time <= range.last }
        var drop = 0
        for (i in 1 until l.size) if (!l[i].charging && !l[i - 1].charging) drop += (l[i - 1].level - l[i].level).coerceAtLeast(0)
        return drop
    }

    /** App name(s) of a UID (shared UIDs: the first label; system UIDs: "Android"). */
    fun label(uid: Int): String {
        val pm = appContext.packageManager
        // Apps of another profile (work profile, private space) can't be looked up from here.
        if (uid / 100_000 != android.os.Process.myUid() / 100_000) return t(R.string.br_other_profile, uid / 100_000)
        val app = uid % 100_000
        if (app < 10_000) return when (app) { 0 -> "Kernel (root)"; 1000 -> "Android"; else -> "System ($uid)" }
        val pkg = packageOf(uid) ?: return "UID $uid"
        return runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
    }

    fun packageOf(uid: Int): String? =
        if (uid / 100_000 != android.os.Process.myUid() / 100_000) null
        else runCatching { appContext.packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()

    // ------------------------------------------------------------------ night report

    /** Alarms at the start and end of the night: a snapshot each, and the morning summary. */
    fun scheduleNight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val s = _settings.value
        for ((code, hour) in listOf(1 to s.nightStart, 2 to s.nightEnd)) {
            val pi = PendingIntent.getBroadcast(
                context, 900 + code, Intent(context, NightReceiver::class.java).putExtra("end", code == 2),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }

    suspend fun onNightAlarm(end: Boolean) {
        sampleLevel()
        snapshot()
        scheduleNight(appContext)
        if (!end || !_settings.value.morningNotify) return
        val range = nightWindow(System.currentTimeMillis() + 60_000)
        val drop = drop(range)
        val top = drain(DrainWindow.Night).firstOrNull { it.uid >= 10_000 }
        if (appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("battery_report", t(R.string.br_title), NotificationManager.IMPORTANCE_LOW))
        val text = if (top != null) t(R.string.br_night_text, drop, label(top.uid)) else t(R.string.br_night_text_plain, drop)
        val open = PendingIntent.getActivity(
            appContext, 91, Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "System")
                .putExtra(de.emil.shizubox.privacy.PrivacyLog.EXTRA_PAGE, "battery"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            13,
            android.app.Notification.Builder(appContext, "battery_report")
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .setContentTitle(t(R.string.br_night_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "battery_report.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE snap (t INTEGER NOT NULL, epoch TEXT NOT NULL, uid INTEGER NOT NULL, total REAL, fg REAL, bg REAL, fgs REAL, cached REAL, " +
                    "wl INTEGER, mobile INTEGER, wakeups INTEGER)",
            )
            db.execSQL("CREATE INDEX snap_t ON snap(t)")
            db.execSQL("CREATE TABLE level (t INTEGER NOT NULL, level INTEGER NOT NULL, charging INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX level_t ON level(t)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

/** Every 15 minutes: battery level; at most hourly: the per-app snapshot (Shizuku). */
/**
 * ShizuBox' one periodic wake-up (every 15 min). Since 0.10.1 it also carries the hourly privacy
 * collection and the daily trash cleanup, which used to wake the phone with their own jobs
 * (~25 extra wake-ups a day).
 */
class BatteryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { BatteryReport.sampleLevel() }
        runCatching { de.emil.shizubox.hilight.HiLightCharging.check(ctx) }
        // Also takes school-mode sessions over before its small log rotates them away.
        runCatching { de.emil.shizubox.schoolstats.SchoolStats.collect() }
        if (de.emil.shizubox.school.SchoolMode.state.value.active) return Result.success()
        val now = System.currentTimeMillis()
        if (now - BatteryReport.lastSnapshot() > 55 * 60_000L) runCatching { BatteryReport.snapshot() }
        val prefs = ctx.createDeviceProtectedStorageContext().getSharedPreferences("maintenance", Context.MODE_PRIVATE)
        val bm = ctx.getSystemService(android.os.BatteryManager::class.java)
        val batteryLow = !bm.isCharging && bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) in 0 until 15
        // Hourly: privacy log (the most expensive part, ~2.5 s – not on a low battery).
        if (de.emil.shizubox.privacy.PrivacyLog.settings.value.enabled && !batteryLow && now - prefs.getLong("privacy", 0) > 55 * 60_000L) {
            runCatching { de.emil.shizubox.privacy.PrivacyLog.collect() }
            prefs.edit().putLong("privacy", now).apply()
        }
        // Daily: trash items past their days, cleaner log older than 90 days.
        if (now - prefs.getLong("trash", 0) > 23 * 3_600_000L) {
            runCatching { de.emil.shizubox.cleaner.Trash.pruneLog() }
            // Shizuku may still be connecting in a freshly started process: the shell call waits for it.
            if (de.emil.shizubox.cleaner.Trash.items.value.isNotEmpty()) {
                runCatching { de.emil.shizubox.cleaner.Trash.purgeExpired() }
            }
            prefs.edit().putLong("trash", now).apply()
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "battery_report", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build(),
            )
            BatteryReport.scheduleNight(context)
        }
    }
}

class NightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { BatteryReport.onNightAlarm(intent.getBooleanExtra("end", false)) }
            } finally {
                pending.finish()
            }
        }
    }
}
