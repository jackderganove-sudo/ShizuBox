package de.emil.shizubox.school

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class Schedule(
    val enabled: Boolean = false,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = 0b0011111,
    val minuteOfDay: Int = 7 * 60 + 50,
    /** Starts before this time are skipped ("pause once"). */
    val skipUntil: Long = 0,
) {
    fun hasDay(d: DayOfWeek) = days and (1 shl (d.value - 1)) != 0
}

/**
 * Starts the school mode automatically at a fixed time on chosen weekdays.
 * The end is handled by the normal auto-end (and 3× power as always).
 */
object SchoolSchedule {
    private const val CHANNEL = "school_schedule"
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(Schedule())
    val state: StateFlow<Schedule> = _state.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.createDeviceProtectedStorageContext().getSharedPreferences("school_schedule", Context.MODE_PRIVATE)
        val d = Schedule()
        _state.value = Schedule(
            enabled = prefs.getBoolean("enabled", d.enabled),
            days = prefs.getInt("days", d.days),
            minuteOfDay = prefs.getInt("minute", d.minuteOfDay),
            skipUntil = prefs.getLong("skip_until", 0),
        )
        reschedule()
    }

    fun update(block: (Schedule) -> Schedule) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putInt("days", s.days)
            .putInt("minute", s.minuteOfDay)
            .putLong("skip_until", s.skipUntil)
            .apply()
        reschedule()
    }

    /** Next start in epoch millis, or null if nothing is planned. */
    fun nextStart(s: Schedule = _state.value, now: Long = System.currentTimeMillis()): Long? {
        if (!s.enabled || s.days == 0) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (i in 0..14) {
            val day = today.plusDays(i.toLong())
            if (!s.hasDay(day.dayOfWeek)) continue
            val at = LocalDateTime.of(day, java.time.LocalTime.of(s.minuteOfDay / 60, s.minuteOfDay % 60)).atZone(zone).toInstant().toEpochMilli()
            if (at > now && at > s.skipUntil) return at
        }
        return null
    }

    /** Skips only the next planned start. */
    fun skipNext() {
        val next = nextStart() ?: return
        update { it.copy(skipUntil = next + 1) }
    }

    fun reschedule() {
        val am = appContext.getSystemService(AlarmManager::class.java)
        am.cancel(alarmIntent())
        val next = nextStart() ?: return
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarmIntent())
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarmIntent())
        SchoolLog.log("Schedule", "next start at $next")
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(
        appContext, 1, Intent(appContext, ScheduleAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Called by the alarm: activate unless already on; tell the user if that is not possible. */
    suspend fun fire() {
        val s = SchoolMode.store.current()
        if (!_state.value.enabled || s.active || s.pendingRestore) return
        // A freshly started process needs a moment until the Shizuku shell service is bound.
        withTimeoutOrNull(20_000) { ShizukuManager.status.first { it == ShizukuStatus.Ready } }
        SchoolMode.activate().onFailure { notifyFailed(it.message) }
    }

    private fun notifyFailed(reason: String?) {
        if (appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.sch_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            appContext, 4,
            Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "School"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            4,
            android.app.Notification.Builder(appContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_school)
                .setContentTitle(t(R.string.sch_failed))
                .setContentText(reason ?: t(R.string.shizuku_not_connected))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** The planned start time is reached. Not exported: only our own alarm can trigger it. */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        SchoolMode.scope.launch {
            try {
                SchoolLog.log("Schedule", "alarm")
                SchoolSchedule.fire()
            } catch (e: Exception) {
                SchoolLog.log("Schedule", "start failed", e)
            } finally {
                SchoolSchedule.reschedule()
                pending.finish()
            }
        }
    }
}

/** Clock or time zone changed: plan the next start again. Never starts anything itself. */
class ScheduleTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            SchoolSchedule.reschedule()
        }
    }
}
