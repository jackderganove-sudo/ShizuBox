package de.emil.shizubox.updates

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.PowerManager
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Keeps a silent install from freezing the phone again and again.
 *
 * A journal entry is written (synchronously) before an install starts and removed when it ends.
 * If ShizuBox starts and the entry is still there, the install never finished – the phone hung,
 * was rebooted or the process died. Then that version is held back from automatic installs, the
 * WorkManager re-run of the same request is ignored (it used to retry right after every reboot)
 * and the user gets a notification. Only an explicit new "Install" retries it.
 */
object InstallGuard {
    private const val NOTIFY_ID = 6
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val lock = Mutex()

    /** Interrupted install found at this start: (key, name, version, format, size, work id). */
    data class Interrupted(val key: String, val name: String, val version: String, val file: String, val sizeKb: Long, val workId: String, val since: Long)

    var lastInterrupted: Interrupted? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("install_guard", Context.MODE_PRIVATE)
        val running = prefs.getString("running", null) ?: run {
            lastInterrupted = prefs.getString("interrupted", null)?.let(::parse)
            return
        }
        val i = parse(running) ?: return
        // The app is installed in exactly that version: the install went through, only ShizuBox
        // didn't see the end (its own update always ends like this – the process is replaced).
        val pkg = runCatching { JSONObject(running).optString("pkg") }.getOrDefault("")
        val now = runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
        if (now != null && i.version.isNotBlank() && Versions.compare(i.version, now) == 0) {
            prefs.edit().remove("running").commit()
            return
        }
        lastInterrupted = i
        // Hold this version back from automatic installs until the user retries on purpose.
        prefs.edit()
            .remove("running")
            .putString("interrupted", running)
            .putString("blocked_${i.key}", i.version)
            .commit()
        notifyInterrupted(i)
    }

    private fun parse(s: String) = runCatching {
        JSONObject(s).let {
            Interrupted(it.getString("key"), it.optString("name"), it.optString("version"), it.optString("file"), it.optLong("size"), it.optString("work"), it.optLong("since"))
        }
    }.getOrNull()

    /** Version of [key] that hung last time and must not be installed automatically. */
    fun blockedVersion(key: String): String? = prefs.getString("blocked_$key", null)

    fun isBlocked(app: TrackedApp) = app.latestVersion != null && blockedVersion(app.key) == app.latestVersion

    /** The user asked for this install explicitly: lift the hold. */
    fun clearBlock(key: String) {
        prefs.edit().remove("blocked_$key").apply()
    }

    /** True if this WorkManager request is the re-run of a request that was interrupted. */
    fun isRerunOf(workId: String) = workId.isNotBlank() && lastInterrupted?.workId == workId

    /** Runs one install under the journal; only one install at a time. */
    suspend fun <T> guarded(app: TrackedApp, workId: String, block: suspend (journal: (file: String, sizeKb: Long) -> Unit) -> T): T = lock.withLock {
        fun write(file: String, size: Long) {
            val o = JSONObject().put("key", app.key).put("pkg", app.id).put("name", app.name).put("version", app.latestVersion.orEmpty())
                .put("file", file).put("size", size).put("work", workId).put("since", System.currentTimeMillis())
            // commit(), not apply(): it must be on disk before a possible freeze.
            prefs.edit().putString("running", o.toString()).commit()
        }
        write("", 0)
        try {
            block(::write)
        } finally {
            prefs.edit().remove("running").commit()
        }
    }

    /**
     * Quick check that the package manager answers at all (a hung install keeps it busy).
     * `cmd package path android` normally takes well under a second.
     */
    suspend fun packageManagerResponds(): Boolean = runCatching {
        ShizukuManager.exec("cmd package path android", timeoutMs = 3000, limitMs = 10_000).let { it.code == 0 && "package:" in it.out }
    }.getOrDefault(false)

    /** Background installs only while nobody is using the phone and memory is not tight. */
    fun goodTimeForBackgroundInstall(context: Context): Boolean {
        val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return !screenOn && !mem.lowMemory
    }

    private fun notifyInterrupted(i: Interrupted) {
        if (appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = appContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("app_updates", t(R.string.ch_updates), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            appContext, 61, Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Updates"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = t(R.string.ig_text, i.name, i.version)
        nm.notify(
            NOTIFY_ID,
            android.app.Notification.Builder(appContext, "app_updates")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(t(R.string.ig_title))
                .setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
