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

    override suspend fun doWork(): Result {
        Updates.checkAll()
        val installed = mutableListOf<String>()
        val held = mutableListOf<String>()
        val s = Updates.settings.value
        if (s.autoInstall && ShizukuManager.isReady) {
            for (app in Updates.apps.value.filter { Updates.state(it) == UpdateState.Update && Source.forUrl(it.url)?.trackOnly != true }) {
                runCatching {
                    val (current, uri) = Updates.download(app) {}
                    val info = ApkParser.parse(applicationContext, uri)
                    // Safety gates: same as the manual installer shows, but without a human looking.
                    if (info.signatureMismatch == true) {
                        held += "${current.name}: andere Signatur"
                        return@runCatching
                    }
                    if (s.virusTotalGate && VirusTotal.apiKey.isNotBlank()) {
                        val sha = applicationContext.contentResolver.openInputStream(uri)!!.use { VirusTotal.sha256(it) }
                        val vt = VirusTotal.lookup(sha)
                        if (vt.status != VtStatus.CLEAN) {
                            held += "${current.name}: VirusTotal " + when (vt.status) {
                                VtStatus.MALICIOUS -> "schädlich (${vt.malicious})"
                                VtStatus.SUSPICIOUS -> "verdächtig (${vt.suspicious})"
                                VtStatus.NOT_FOUND, VtStatus.NOT_ANALYZED -> "kennt die Datei noch nicht"
                                else -> "nicht prüfbar"
                            }
                            return@runCatching
                        }
                    }
                    val uris = info.splitEntries.filter { it.selected }.map { it.uri }
                    val r = ShizukuApkInstaller.install(applicationContext, current.name, uris, InstallOptions()) {}
                    if (r == InstallResult.Success) {
                        Updates.markInstalled(Updates.apps.value.first { it.key == current.key })
                        installed += current.name
                    }
                }
            }
        }
        notify(applicationContext, Updates.apps.value.filter { Updates.state(it) == UpdateState.Update }.map { it.name }, installed, held)
        return Result.success()
    }

    companion object {
        private const val WORK = "app_updates"
        private const val CHANNEL = "app_updates"

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
            if (pending.isEmpty() && installed.isEmpty()) {
                nm.cancel(2)
                return
            }
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Updates"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = when {
                pending.isNotEmpty() -> "${pending.size} Update${if (pending.size > 1) "s" else ""} verfügbar"
                else -> "${installed.size} App${if (installed.size > 1) "s" else ""} aktualisiert"
            }
            val text = (if (pending.isNotEmpty()) pending else installed).joinToString(", ") +
                if (held.isNotEmpty()) "\n\nNicht automatisch installiert:\n" + held.joinToString("\n") else ""
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
