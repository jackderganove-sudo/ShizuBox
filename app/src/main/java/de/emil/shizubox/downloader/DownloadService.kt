package de.emil.shizubox.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service while downloads are queued or running: keeps them going with the app
 * closed and shows one progress notification for the whole queue.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, progressNotification(this, DownloadQueue.tasks.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (watcher == null) {
            watcher = scope.launch {
                DownloadQueue.tasks.collectLatest { tasks ->
                    if (tasks.none { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collectLatest
                    }
                    getSystemService(NotificationManager::class.java).notify(ID, progressNotification(this@DownloadService, tasks))
                    if (tasks.any { it.state == TaskState.QUEUED }) DownloadQueue.pump()
                }
            }
        }
        DownloadQueue.pump()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val CHANNEL_DONE = "downloads_done"
        private const val ID = 7

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }

        private fun channels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.dl_ch_progress), NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, t(R.string.dl_ch_done), NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun openApp(context: Context, page: Int) = PendingIntent.getActivity(
            context, 20 + page,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SECTION, "Downloader")
                .putExtra(EXTRA_PAGE, page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        const val EXTRA_PAGE = "dl_page"

        fun progressNotification(context: Context, tasks: List<DownloadTask>): Notification {
            channels(context)
            val running = tasks.filter { it.state == TaskState.RUNNING }
            val waiting = tasks.count { it.state == TaskState.QUEUED }
            val progress = if (running.isEmpty()) 0f else running.map { it.progress }.average().toFloat()
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(de.emil.shizubox.tp(R.plurals.dl_running, running.size.coerceAtLeast(1), running.size.coerceAtLeast(1)))
                .setContentText(
                    listOfNotNull(running.firstOrNull()?.title, if (waiting > 0) t(R.string.dl_waiting_n, waiting) else null).joinToString(" · "),
                )
                .setProgress(100, (progress * 100).toInt(), running.isEmpty() || progress <= 0f)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context, 2))
                .build()
        }

        fun notifyDone(context: Context, task: DownloadTask) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            channels(context)
            val open = PendingIntent.getActivity(
                context, task.id.hashCode(), DlStorage.openIntent(task), PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService(NotificationManager::class.java).notify(
                task.id.hashCode(),
                Notification.Builder(context, CHANNEL_DONE)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(t(R.string.dl_done_title))
                    .setContentText(task.title)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}

/**
 * After an app update (also ShizuBox updating itself) the process was killed mid-download:
 * continue the queue right away. MY_PACKAGE_REPLACED may start foreground services.
 */
class DownloadResumeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && DownloadQueue.active) {
            runCatching { DownloadService.start(context) }
        }
    }
}
