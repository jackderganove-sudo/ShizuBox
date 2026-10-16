package de.emil.shizubox.schoolstats

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.widget.RemoteViews
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class EndReason { Power, Timeout, Emergency, Running }

data class SchoolSession(val start: Long, val end: Long?, val reason: EndReason, val scheduled: Boolean, val screenOns: Int) {
    fun durationMs(now: Long = System.currentTimeMillis()) = (end ?: now) - start
}

/**
 * Statistics of school mode – read-only. School mode itself is not touched: ShizuBox reads the
 * existing `school.log` (device-protected storage, written by SchoolLog) and keeps the sessions in
 * its own database, because the log only holds the last ~32–64 KB.
 * - start: "activated, … endAt=<ms>"; a preceding "Schedule: alarm" means the schedule started it
 * - end: "exit requested" (+ "restored"); the reason is inferred:
 *   a "screen …, N in sequence" (N ≥ 3) just before → 3× power; at the planned end time → timeout;
 *   otherwise the emergency exit (adb)
 * - screen switched on during the session: "screen on" lines in between
 */
object SchoolStats {
    private lateinit var appContext: Context
    private val db: SQLiteDatabase by lazy { Helper(appContext).writableDatabase }
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        // When school mode ends (or starts), take the sessions over right away – reading only.
        de.emil.shizubox.util.Unlock.whenUnlocked(appContext) {
            scope.launch {
                de.emil.shizubox.school.SchoolMode.state.map { it.active }.distinctUntilChanged().collect {
                    kotlinx.coroutines.delay(3000) // let the log line be written
                    runCatching { collect() }
                    SchoolStatsWidget.update(appContext)
                }
            }
        }
    }

    private fun logFile() = File(appContext.createDeviceProtectedStorageContext().filesDir, "school.log")

    /** "MM-dd HH:mm:ss.SSS" has no year: this year, or last year if that would lie in the future. */
    private fun time(s: String): Long? {
        val d = runCatching { fmt.parse(s) }.getOrNull() ?: return null
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { time = d; set(Calendar.YEAR, now.get(Calendar.YEAR)) }
        if (c.timeInMillis > now.timeInMillis + 86_400_000L) c.add(Calendar.YEAR, -1)
        return c.timeInMillis
    }

    /** Parses the log and stores/updates every session it contains. */
    suspend fun collect() = lock.withLock {
        withContext(Dispatchers.IO) {
            val lines = runCatching { logFile().readLines() }.getOrDefault(emptyList())
            data class Ev(val t: Long, val tag: String, val msg: String)
            val events = lines.mapNotNull { l ->
                if (l.length < 20) return@mapNotNull null
                val t = time(l.substring(0, 18)) ?: return@mapNotNull null
                val rest = l.substring(19)
                Ev(t, rest.substringBefore(": "), rest.substringAfter(": "))
            }
            var open: Triple<Long, Long, Boolean>? = null // start, endAt, scheduled
            var screenOns = 0
            var exitAt: Long? = null
            var reason = EndReason.Emergency
            var lastScheduleAlarm = 0L
            var lastSequence = 0L to 0
            for (e in events) {
                when {
                    e.tag == "Schedule" && e.msg == "alarm" -> lastScheduleAlarm = e.t
                    e.msg.startsWith("activated") -> {
                        val endAt = Regex("""endAt=(\d+)""").find(e.msg)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        open = Triple(e.t, endAt, e.t - lastScheduleAlarm < 120_000)
                        screenOns = 0; exitAt = null
                    }
                    e.msg.startsWith("screen ") -> {
                        if (open != null && e.msg.startsWith("screen on")) screenOns++
                        Regex("""(\d+) in sequence""").find(e.msg)?.groupValues?.get(1)?.toIntOrNull()?.let { lastSequence = e.t to it }
                    }
                    e.msg == "exit requested" && open != null -> {
                        exitAt = e.t
                        val o = open!!
                        reason = when {
                            e.t - lastSequence.first < 15_000 && lastSequence.second >= 3 -> EndReason.Power
                            o.second > 0 && kotlin.math.abs(e.t - o.second) < 120_000 -> EndReason.Timeout
                            else -> EndReason.Emergency
                        }
                    }
                    e.msg == "restored" && open != null -> {
                        val o = open!!
                        save(SchoolSession(o.first, exitAt ?: e.t, if (exitAt != null) reason else EndReason.Emergency, o.third, screenOns))
                        open = null
                    }
                }
            }
            open?.let { o -> save(SchoolSession(o.first, null, EndReason.Running, o.third, screenOns)) }
            _version.value++
        }
    }

    private fun save(s: SchoolSession) {
        db.insertWithOnConflict("s", null, ContentValues().apply {
            put("start", s.start); put("end", s.end); put("reason", s.reason.name); put("scheduled", if (s.scheduled) 1 else 0); put("screen_ons", s.screenOns)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun sessions(): List<SchoolSession> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT start, end, reason, scheduled, screen_ons FROM s ORDER BY start DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) add(SchoolSession(c.getLong(0), if (c.isNull(1)) null else c.getLong(1), runCatching { EndReason.valueOf(c.getString(2)) }.getOrDefault(EndReason.Emergency), c.getInt(3) == 1, c.getInt(4)))
            }
        }
    }

    /** Milliseconds of school mode inside [from, to) – sessions are cut at the edges. */
    fun total(sessions: List<SchoolSession>, from: Long, to: Long = System.currentTimeMillis()): Long =
        sessions.sumOf { s -> (minOf(s.end ?: System.currentTimeMillis(), to) - maxOf(s.start, from)).coerceAtLeast(0) }

    fun startOfDay(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    fun startOfWeek(): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        if (timeInMillis > System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, -1)
    }.timeInMillis

    fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun format(ms: Long): String {
        val min = ms / 60_000
        return if (min < 60) t(R.string.dur_min, min.toInt()) else t(R.string.dur_h_min, (min / 60).toInt(), (min % 60).toInt())
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "school_stats.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE s (start INTEGER PRIMARY KEY, end INTEGER, reason TEXT NOT NULL, scheduled INTEGER NOT NULL, screen_ons INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

/** Home screen widget: school mode today and this week. */
class SchoolStatsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { SchoolStats.collect() }
                update(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SchoolStatsWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val sessions = runCatching { SchoolStats.sessions() }.getOrDefault(emptyList())
                val views = RemoteViews(context.packageName, R.layout.widget_school_stats).apply {
                    setTextViewText(R.id.ws_title, t(R.string.sec_school))
                    setTextViewText(R.id.ws_today, SchoolStats.format(SchoolStats.total(sessions, SchoolStats.startOfDay())))
                    setTextViewText(R.id.ws_today_label, t(R.string.ss_today))
                    setTextViewText(R.id.ws_week, SchoolStats.format(SchoolStats.total(sessions, SchoolStats.startOfWeek())))
                    setTextViewText(R.id.ws_week_label, t(R.string.ss_week))
                    setOnClickPendingIntent(
                        android.R.id.background,
                        PendingIntent.getActivity(
                            context, 71, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "School"),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                }
                manager.updateAppWidget(ids, views)
            }
        }
    }
}
