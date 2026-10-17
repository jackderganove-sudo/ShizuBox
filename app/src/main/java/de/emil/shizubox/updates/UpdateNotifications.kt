package de.emil.shizubox.updates

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Toast
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.t
import de.emil.shizubox.tp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** "Updates available" with Install / Install all / Skip buttons. */
object UpdateNotifications {
    const val ID = 5
    private const val CHANNEL = "app_updates_available"
    const val EXTRA_KEYS = "keys"
    const val ACTION_SKIP = "de.emil.shizubox.UPDATE_SKIP"

    fun available(context: Context, apps: List<TrackedApp> = Updates.installableUpdates()) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (apps.isEmpty()) {
            nm.cancel(ID)
            return
        }
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.un_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val keys = apps.map { it.key }.toTypedArray()
        fun line(a: TrackedApp) = "${a.name}: ${Updates.installedVersionName(a.id) ?: a.installedVersion ?: "?"} → ${a.latestVersion}"

        val install = PendingIntent.getActivity(
            context, 51,
            Intent(context, UpdateInstallActivity::class.java).putExtra(EXTRA_KEYS, keys).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val skip = PendingIntent.getBroadcast(
            context, 52,
            Intent(context, UpdateSkipReceiver::class.java).setAction(ACTION_SKIP).putExtra(EXTRA_KEYS, keys),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context, 53,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Updates"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        if (apps.size == 1) {
            val a = apps.first()
            Apps.icon(a.id)?.let { b.setLargeIcon(Icon.createWithBitmap(it)) }
            b.setContentTitle(t(R.string.un_one_title, a.name))
                .setContentText("${Updates.installedVersionName(a.id) ?: a.installedVersion ?: "?"} → ${a.latestVersion}")
                .addAction(Notification.Action.Builder(null, t(R.string.install), install).build())
        } else {
            val style = Notification.InboxStyle()
            apps.take(7).forEach { style.addLine(line(it)) }
            if (apps.size > 7) style.setSummaryText("+${apps.size - 7}")
            b.setContentTitle(tp(R.plurals.w_pending, apps.size, apps.size))
                .setContentText(apps.joinToString(", ") { it.name })
                .setStyle(style)
                .addAction(Notification.Action.Builder(null, t(R.string.un_install_all), install).build())
        }
        b.addAction(Notification.Action.Builder(null, t(R.string.un_skip), skip).build())
        nm.notify(ID, b.build())
    }
}

/** "Skip": marks the offered versions as skipped. */
class UpdateSkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringArrayExtra(UpdateNotifications.EXTRA_KEYS)?.let { Updates.skip(it.toList()) }
        context.getSystemService(NotificationManager::class.java).cancel(UpdateNotifications.ID)
    }
}

/**
 * "Install" from the notification. Invisible: with Shizuku the updates install silently as
 * foreground work (progress in the notification); without Shizuku a single update is
 * downloaded and handed to the installer with the system dialog, several open the updater.
 */
class UpdateInstallActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(UpdateNotifications.ID)
        val keys = intent.getStringArrayExtra(UpdateNotifications.EXTRA_KEYS)?.toList().orEmpty()
        scope.launch {
            val ready = withTimeoutOrNull(3000) { ShizukuManager.status.first { it == ShizukuStatus.Ready } } != null
            if (ready) {
                UpdateWorker.updateAllNow(this@UpdateInstallActivity, keys)
                Toast.makeText(this@UpdateInstallActivity, t(R.string.un_installing), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            val app = Updates.apps.value.firstOrNull { it.key == keys.singleOrNull() }
            if (app == null) {
                startActivity(Intent(this@UpdateInstallActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Updates"))
                finish()
                return@launch
            }
            Toast.makeText(this@UpdateInstallActivity, t(R.string.downloading), Toast.LENGTH_SHORT).show()
            // Same package check as everywhere else (Updates.openInstallerChecked).
            runCatching { Updates.downloadToInstaller(this@UpdateInstallActivity, app) }
                .onFailure { Toast.makeText(this@UpdateInstallActivity, t(R.string.download_failed, it.message), Toast.LENGTH_LONG).show() }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
