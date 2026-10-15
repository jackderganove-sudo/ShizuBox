package de.emil.shizubox.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AppUsage(val pkg: String, val foregroundMs: Long, val launches: Int, val lastUsed: Long)

/**
 * Screen time per app from UsageStatsManager. The "usage access" app-op is granted to
 * ShizuBox itself through Shizuku, so no trip to the system settings is needed.
 */
object ScreenTime {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Grants usage access through Shizuku. Returns whether access is available afterwards. */
    suspend fun ensureAccess(context: Context): Boolean {
        if (hasAccess(context)) return true
        if (!ShizukuManager.isReady) return false
        runCatching { ShizukuManager.exec("appops set ${context.packageName} GET_USAGE_STATS allow") }
        return hasAccess(context)
    }

    /** Usage since midnight [days] - 1 days ago (1 = today). */
    suspend fun load(context: Context, days: Int): List<AppUsage> = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val start = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()

        // Foreground time from the event stream: resumed → paused/stopped per package.
        val time = mutableMapOf<String, Long>()
        val launches = mutableMapOf<String, Int>()
        val last = mutableMapOf<String, Long>()
        val resumedAt = mutableMapOf<String, Long>()
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (pkg !in resumedAt) launches[pkg] = (launches[pkg] ?: 0) + 1
                    resumedAt.putIfAbsent(pkg, e.timeStamp)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    resumedAt.remove(pkg)?.let { from ->
                        time[pkg] = (time[pkg] ?: 0) + (e.timeStamp - from)
                        last[pkg] = e.timeStamp
                    }
                }
            }
        }
        // Still in the foreground right now.
        resumedAt.forEach { (pkg, from) ->
            time[pkg] = (time[pkg] ?: 0) + (end - from)
            last[pkg] = end
        }
        // The launcher is "in use" whenever no app is open; it would top every list.
        val home = context.packageManager.resolveActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0,
        )?.activityInfo?.packageName
        time.filter { it.value >= 1000 && it.key != home }
            .map { (pkg, ms) -> AppUsage(pkg, ms, launches[pkg] ?: 0, last[pkg] ?: 0) }
            .sortedByDescending { it.foregroundMs }
    }
}
