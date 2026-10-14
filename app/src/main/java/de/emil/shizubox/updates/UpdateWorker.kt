package de.emil.shizubox.updates

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

    override suspend fun getForegroundInfo() = progressInfo(applicationContext, "Bereite Updates vor …", 0, 0, true)

    override suspend fun doWork(): Result {
        // "Update all" from the app: no new check needed, the list is current.
        if (!inputData.getBoolean(KEY_UPDATE_ALL, false)) Updates.checkAll()
        val installed = mutableListOf<String>()
        val held = mutableListOf<String>()
        val s = Updates.settings.value
        val updateAll = inputData.getBoolean(KEY_UPDATE_ALL, false)
        if ((updateAll || s.autoInstall) && ShizukuManager.isReady &&
            Updates.apps.value.any { Updates.state(it) == UpdateState.Update && Source.forUrl(it.url)?.trackOnly != true }
        ) {
            // Foreground: keeps the process alive while installing, and shows the progress.
            runCatching { setForeground(progressInfo(applicationContext, "Bereite Updates vor …", 0, 0, true)) }
            val (ok, failed) = Updates.runUpdateAll { i, total, name, p ->
                val info = progressInfo(applicationContext, "$name (${i + 1}/$total)", ((i + p) * 100).toInt(), total * 100, p == 0f)
                runCatching { setForegroundAsync(info) }
            }
            installed += ok
            held += failed.map { (n, r) -> "$n: $r" }
        } else if (updateAll && !ShizukuManager.isReady) {
            held += "Shizuku nicht verbunden"
        }
        notify(applicationContext, Updates.apps.value.filter { Updates.state(it) == UpdateState.Update }.map { it.name }, installed, held)
        return Result.success()
    }

    companion object {
        private const val WORK = "app_updates"
        private const val WORK_ALL = "app_update_all"
        private const val CHANNEL = "app_updates"
        private const val PROGRESS_CHANNEL = "app_update_progress"
        private const val PROGRESS_ID = 3
        private const val KEY_UPDATE_ALL = "update_all"

        /** "Update all" pressed in the app: run now as expedited foreground work. */
        fun updateAllNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInputData(androidx.work.workDataOf(KEY_UPDATE_ALL to true))
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_ALL, androidx.work.ExistingWorkPolicy.KEEP, request)
        }

        fun progressInfo(context: Context, text: String, progress: Int, max: Int, indeterminate: Boolean): androidx.work.ForegroundInfo {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "Update-Fortschritt", NotificationManager.IMPORTANCE_LOW))
            val n = android.app.Notification.Builder(context, PROGRESS_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Apps werden aktualisiert")
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
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App-Updates", NotificationManager.IMPORTANCE_DEFAULT))
            if (pending.isEmpty() && installed.isEmpty() && held.isEmpty()) {
                nm.cancel(2)
                return
            }
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Updates"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = when {
                installed.isNotEmpty() -> "${installed.size} App${if (installed.size > 1) "s" else ""} aktualisiert" +
                    if (held.isNotEmpty()) ", ${held.size} nicht" else ""
                held.isNotEmpty() -> "${held.size} Update${if (held.size > 1) "s" else ""} nicht installiert"
                else -> "${pending.size} Update${if (pending.size > 1) "s" else ""} verfügbar"
            }
            val text = buildString {
                if (installed.isNotEmpty()) append(installed.joinToString(", "))
                else if (held.isEmpty()) append(pending.joinToString(", "))
                if (held.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Nicht installiert:\n" + held.joinToString("\n"))
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
