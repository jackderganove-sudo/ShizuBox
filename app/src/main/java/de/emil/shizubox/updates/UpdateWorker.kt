package de.emil.shizubox.updates

import de.emil.shizubox.t
import de.emil.shizubox.tp
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.installer.ApkParser
import de.emil.shizubox.installer.InstallOptions
import de.emil.shizubox.installer.InstallResult
import de.emil.shizubox.installer.ShizukuApkInstaller
import de.emil.shizubox.installer.VirusTotal
import de.emil.shizubox.installer.VtStatus
import de.emil.shizubox.shizuku.ShizukuManager
import java.util.concurrent.TimeUnit

/** Periodic update check; optionally installs updates silently through Shizuku. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() = progressInfo(applicationContext, t(R.string.w_preparing), 0, 0, true)

    override suspend fun doWork(): Result {
        // "Update all" or "Install" from the notification: no new check needed, the list is current.
        val explicit = inputData.getBoolean(KEY_UPDATE_ALL, false)
        val only = inputData.getStringArray(KEY_ONLY)?.toSet()
        val workId = id.toString()
        // WorkManager runs an interrupted request again right after the reboot – the same install
        // that just froze the phone. InstallGuard has already told the user; don't start it again.
        if (InstallGuard.isRerunOf(workId)) return Result.success()
        // A new, explicit "Install" is the user's retry: lift the hold for what they chose.
        if (explicit) Updates.apps.value.filter { only == null || it.key in only }.forEach { InstallGuard.clearBlock(it.key) }
        if (!explicit) Updates.checkAll()
        val installed = mutableListOf<String>()
        val held = mutableListOf<String>()
        val s = Updates.settings.value
        // Automatic installs only when nobody uses the phone: a big install (dexopt) can make it stutter.
        val autoNow = s.autoInstall && InstallGuard.goodTimeForBackgroundInstall(applicationContext)
        if ((explicit || autoNow) && ShizukuManager.isReady &&
            Updates.installableUpdates().any { only == null || it.key in only }
        ) {
            // Foreground: keeps the process alive while installing, and shows the progress.
            runCatching { setForeground(progressInfo(applicationContext, t(R.string.w_preparing), 0, 0, true)) }
            // At most two progress updates per second: every 64-KB block used to post one
            // (≈ 2000 for a 120 MB XAPK), flooding system_server and System UI.
            var lastPost = 0L
            var lastStep = -1
            val (ok, failed) = Updates.runUpdateAll(only, workId) { i, total, name, p ->
                val now = android.os.SystemClock.uptimeMillis()
                if (i == lastStep && now - lastPost < 500) return@runUpdateAll
                lastPost = now
                lastStep = i
                val info = progressInfo(applicationContext, "$name (${i + 1}/$total)", ((i + p) * 100).toInt(), total * 100, p == 0f)
                runCatching { setForegroundAsync(info) }
            }
            installed += ok
            held += failed.map { (n, r) -> "$n: $r" }
        } else if (explicit && !ShizukuManager.isReady) {
            held += t(R.string.shizuku_not_conn)
        }
        if (explicit || installed.isNotEmpty() || held.isNotEmpty()) {
            notify(applicationContext, emptyList(), installed, held)
        } else if (s.notify) {
            // Plain check: offer the found updates with "Install" buttons.
            UpdateNotifications.available(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "app_updates"
        private const val WORK_ALL = "app_update_all"
        private const val CHANNEL = "app_updates"
        private const val PROGRESS_CHANNEL = "app_update_progress"
        private const val PROGRESS_ID = 3
        private const val KEY_UPDATE_ALL = "update_all"
        private const val KEY_ONLY = "only"

        /** "Update all" pressed in the app: run now as expedited foreground work. */
        fun updateAllNow(context: Context, only: Collection<String>? = null) {
            val data = if (only == null) androidx.work.workDataOf(KEY_UPDATE_ALL to true)
            else androidx.work.workDataOf(KEY_UPDATE_ALL to true, KEY_ONLY to only.toTypedArray())
            val request = androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInputData(data)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            // Several "Install" taps queue up one after another instead of being dropped.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_ALL, androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun progressInfo(context: Context, text: String, progress: Int, max: Int, indeterminate: Boolean): androidx.work.ForegroundInfo {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, t(R.string.ch_progress), NotificationManager.IMPORTANCE_LOW))
            val n = android.app.Notification.Builder(context, PROGRESS_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(t(R.string.w_updating))
                .setContentText(text)
                .setProgress(max, progress, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            return androidx.work.ForegroundInfo(PROGRESS_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        fun schedule(context: Context) {
            val s = Updates.settings.value
            val wm = WorkManager.getInstance(context)
            if (s.intervalHours <= 0) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(s.chargingOnly)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(s.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun notify(context: Context, pending: List<String>, installed: List<String>, held: List<String> = emptyList()) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.ch_updates), NotificationManager.IMPORTANCE_DEFAULT))
            if (pending.isEmpty() && installed.isEmpty() && held.isEmpty()) {
                nm.cancel(2)
                return
            }
            nm.cancel(UpdateNotifications.ID)
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Updates"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = when {
                installed.isNotEmpty() -> tp(R.plurals.w_updated, installed.size, installed.size) +
                    if (held.isNotEmpty()) t(R.string.w_not_sfx, held.size) else ""
                held.isNotEmpty() -> tp(R.plurals.w_held, held.size, held.size)
                else -> tp(R.plurals.w_pending, pending.size, pending.size)
            }
            val text = buildString {
                if (installed.isNotEmpty()) append(installed.joinToString(", "))
                else if (held.isEmpty()) append(pending.joinToString(", "))
                if (held.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append(t(R.string.w_not_installed) + "\n" + held.joinToString("\n"))
                }
            }
            nm.notify(
                2,
                android.app.Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
