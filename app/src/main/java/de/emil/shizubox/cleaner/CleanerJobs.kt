package de.emil.shizubox.cleaner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class JobState(val tool: CleanTool, val text: String = "", val progress: Float? = null)

/**
 * Scans run app-wide (not in a screen's scope, so leaving the page does not cancel them), one tool
 * at a time (a semaphore – scans read the whole storage, in parallel they only slow each other
 * down), cancellable, and inside a foreground service with a throttled progress notification
 * while they last.
 */
object CleanerJobs {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(1)
    private val jobs = HashMap<CleanTool, Job>()
    private val _running = MutableStateFlow<Map<CleanTool, JobState>>(emptyMap())
    val running: StateFlow<Map<CleanTool, JobState>> = _running.asStateFlow()
    private val _errors = MutableStateFlow<Map<CleanTool, String>>(emptyMap())
    val errors: StateFlow<Map<CleanTool, String>> = _errors.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isRunning(tool: CleanTool) = tool in _running.value

    /** Starts [block] unless the tool already runs. [report] updates the progress shown. */
    fun start(tool: CleanTool, block: suspend (report: (String, Float?) -> Unit) -> Unit): Job? {
        if (isRunning(tool)) return null
        _running.value = _running.value + (tool to JobState(tool, t(R.string.cl_waiting)))
        _errors.value = _errors.value - tool
        CleanerService.update(appContext)
        val job = scope.launch {
            try {
                gate.withPermit {
                    block { text, p -> _running.value = _running.value + (tool to JobState(tool, text, p)); CleanerService.update(appContext) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.value = _errors.value + (tool to (e.message ?: e.javaClass.simpleName))
            } finally {
                _running.value = _running.value - tool
                synchronized(jobs) { jobs.remove(tool) }
                CleanerService.update(appContext)
            }
        }
        synchronized(jobs) { jobs[tool] = job }
        return job
    }

    fun cancel(tool: CleanTool) = synchronized(jobs) { jobs[tool] }?.cancel()

    fun cancelAll() = synchronized(jobs) { jobs.values.toList() }.forEach { it.cancel() }
}

/** Keeps the process alive while cleaner scans run; the notification shows what runs and can stop it. */
class CleanerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CleanerJobs.cancelAll()
        }
        val running = CleanerJobs.running.value
        if (running.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(ID, notification(this, running.values.first()), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        CleanerJobs.cancelAll()
        stopSelf()
    }

    companion object {
        private const val ID = 7301
        private const val CHANNEL = "cleaner"
        private const val ACTION_STOP = "stop"
        private var lastNotify = 0L
        private var started = false

        fun update(context: Context) {
            val running = CleanerJobs.running.value
            if (running.isEmpty()) {
                if (started) runCatching { context.startService(Intent(context, CleanerService::class.java)) }
                started = false
                return
            }
            if (!started) {
                started = runCatching { context.startForegroundService(Intent(context, CleanerService::class.java)) }.isSuccess
                return
            }
            // Progress at most twice a second (lesson from the update notifications).
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotify < 500) return
            lastNotify = now
            context.getSystemService(NotificationManager::class.java).notify(ID, notification(context, running.values.first()))
        }

        fun channel(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.cl_ch_scan), NotificationManager.IMPORTANCE_LOW))
        }

        private fun notification(context: Context, s: JobState): Notification {
            channel(context)
            val open = PendingIntent.getActivity(
                context, 7302, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Cleaner")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stop = PendingIntent.getService(context, 7303, Intent(context, CleanerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_cache)
                .setContentTitle(s.tool.label)
                .setContentText(s.text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .apply { if (s.progress != null) setProgress(1000, (s.progress * 1000).toInt(), false) else setProgress(0, 0, true) }
                .addAction(Notification.Action.Builder(null, t(R.string.cancel), stop).build())
                .build()
        }
    }
}
