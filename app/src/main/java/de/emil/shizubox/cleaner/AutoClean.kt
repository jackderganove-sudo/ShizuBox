package de.emil.shizubox.cleaner

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class AutoResult(val moved: Int, val bytes: Long, val skipped: String? = null)

/**
 * Cleaning without questions – for the schedule, the tile/quick action and the automation action.
 * Only what the user picked in the settings; by default the safe categories (thumbnails, empty
 * folders, logs/temp, broken downloads, ShizuBox leftovers, app caches without AICore). Leftovers
 * and duplicates only when switched on explicitly (duplicates: identical files only, the rule keeps
 * the camera/oldest copy). Everything goes to the trash, never while school mode runs.
 */
object AutoClean {
    private val lock = Mutex()

    suspend fun run(context: Context, cfg: CleanerConfig = CleanerSettings.current): AutoResult = lock.withLock {
        if (de.emil.shizubox.school.SchoolMode.state.value.active) return AutoResult(0, 0, "school")
        if (!ShizukuManager.isReady && !waitForShizuku()) return AutoResult(0, 0, "shizuku")
        var moved = 0
        var bytes = 0L
        fun add(r: MoveResult) { moved += r.moved; bytes += r.bytes }
        val cats = cfg.autoKeys.mapNotNull { runCatching { SysCat.valueOf(it) }.getOrNull() }.toSet()
        if (cats.isNotEmpty()) {
            val found = SystemCleaner.scan(context, cats)
            add(SystemCleaner.clean(found.values.flatten(), CleanTool.Auto))
        }
        if (AutoKeys.APP_CACHES in cfg.autoKeys) {
            val apps = AppCleaner.scan(context).filter { AppCleaner.blockReason(it) == null && !it.running }
            if (apps.isNotEmpty()) add(AppCleaner.clean(apps, stopRunning = false, tool = CleanTool.Auto))
        }
        if (AutoKeys.CORPSES in cfg.autoKeys) {
            val found = CorpseFinder.scan().values.flatten()
            if (found.isNotEmpty()) add(Trash.move(found.map { Candidate(it.path, it.size, true) }, CleanTool.Auto))
        }
        if (AutoKeys.DUPLICATES in cfg.autoKeys && StorageAccess.ensure()) {
            val groups = Duplicates.scan(cfg.copy(dupeSimilar = false)).filter { !it.similar }
            val files = Duplicates.toRemove(groups)
            if (files.isNotEmpty()) add(Trash.move(files.map { Candidate(it.path, it.size, false) }, CleanTool.Auto))
        }
        AutoResult(moved, bytes)
    }

    private suspend fun waitForShizuku(): Boolean {
        ShizukuManager.refresh()
        repeat(20) {
            if (ShizukuManager.isReady) return true
            kotlinx.coroutines.delay(500)
        }
        return ShizukuManager.isReady
    }

    /** "X cleaned up" – opens the trash. */
    fun notify(context: Context, r: AutoResult) {
        if (r.moved == 0) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.cl_ch_done), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, 7311,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Cleaner").putExtra(MainActivity.EXTRA_CLEANER_PAGE, "trash")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val size = android.text.format.Formatter.formatShortFileSize(context, r.bytes)
        nm.notify(
            7310,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_cache)
                .setContentTitle(t(R.string.ac_done_title, size))
                .setContentText(de.emil.shizubox.tp(R.plurals.ac_done_text, r.moved, r.moved, CleanerSettings.current.trashDays))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private const val CHANNEL = "cleaner_done"
}

/**
 * Scheduled cleaning: an exact alarm at the chosen day/time (like the school-mode schedule) hands
 * over to WorkManager, which waits for the charger if wanted; the worker itself only runs with the
 * screen off and retries every 30 min otherwise (up to ~6 h). Re-armed after each run and at app start.
 */
object CleanerSchedule {
    fun next(cfg: CleanerConfig, now: Long = System.currentTimeMillis()): Long? {
        if (!cfg.schedule || cfg.scheduleDays == 0) return null
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        c.set(Calendar.HOUR_OF_DAY, cfg.scheduleMinute / 60)
        c.set(Calendar.MINUTE, cfg.scheduleMinute % 60)
        repeat(8) {
            // Calendar: Sunday = 1 … Saturday = 7; our bits: Monday = 0 … Sunday = 6.
            val bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (c.timeInMillis > now && cfg.scheduleDays and (1 shl bit) != 0) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7320, Intent(context, CleanerAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = next(CleanerSettings.current)
        if (at == null) {
            am.cancel(pending(context))
            return
        }
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
    }

    fun enqueue(context: Context) {
        val cfg = CleanerSettings.current
        WorkManager.getInstance(context).enqueueUniqueWork(
            "cleaner_auto", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoCleanWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(cfg.scheduleCharging).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build(),
        )
    }
}

class CleanerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CleanerSchedule.enqueue(context)
        CleanerSchedule.schedule(context)
    }
}

class AutoCleanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val screenOn = applicationContext.getSystemService(PowerManager::class.java).isInteractive
        if (screenOn || de.emil.shizubox.school.SchoolMode.state.value.active) {
            return if (runAttemptCount < 12) Result.retry() else Result.success()
        }
        val r = runCatching { AutoClean.run(applicationContext) }.getOrElse { return if (runAttemptCount < 12) Result.retry() else Result.failure() }
        if (r.skipped == "shizuku" && runAttemptCount < 12) return Result.retry()
        AutoClean.notify(applicationContext, r)
        return Result.success()
    }
}
