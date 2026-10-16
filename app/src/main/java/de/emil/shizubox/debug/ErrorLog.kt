package de.emil.shizubox.debug

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

enum class ErrorKind { Crash, Anr, Native, Killed, Hang }

data class ErrorEntry(val time: Long, val kind: ErrorKind, val title: String, val details: String)

/**
 * ShizuBox's own crashes, ANRs and hangs, stored locally (device-protected storage, so it also
 * works before the first unlock). Not part of the backup; entries older than 30 days are dropped.
 * Sources: our uncaught-exception handler, Android's exit reasons of earlier runs (ANR traces,
 * native crashes, kills for excessive resource use) and InstallGuard's interrupted installs.
 */
object ErrorLog {
    private const val TAG = "ShizuBoxErrors"
    private const val KEEP_MS = 30L * 86_400_000L
    private const val MAX_DETAILS = 16_000
    private lateinit var file: File
    private lateinit var prefs: SharedPreferences

    private val _entries = MutableStateFlow<List<ErrorEntry>>(emptyList())
    val entries: StateFlow<List<ErrorEntry>> = _entries.asStateFlow()

    /** First thing in Application.onCreate: from then on every crash is written down. */
    fun init(context: Context) {
        val dp = context.createDeviceProtectedStorageContext()
        file = File(dp.filesDir, "error_log.jsonl")
        prefs = dp.getSharedPreferences("error_log", Context.MODE_PRIVATE)
        load()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { add(ErrorKind.Crash, "${e.javaClass.simpleName}: ${e.message.orEmpty()}".take(200), "thread ${thread.name}\n" + Log.getStackTraceString(e)) }
            previous?.uncaughtException(thread, e)
        }
        if (prefs.getBoolean("strict", false)) enableStrictMode()
    }

    private fun load() {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val list = runCatching {
            file.readLines().mapNotNull { line ->
                runCatching {
                    JSONObject(line).let { ErrorEntry(it.getLong("t"), ErrorKind.valueOf(it.getString("k")), it.optString("title"), it.optString("d")) }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
        val kept = list.filter { it.time >= cutoff }
        _entries.value = kept.sortedByDescending { it.time }
        if (kept.size != list.size) rewrite()
    }

    private fun rewrite() = runCatching {
        file.writeText(_entries.value.sortedBy { it.time }.joinToString("") { encode(it) + "\n" })
    }

    private fun encode(e: ErrorEntry) = JSONObject().put("t", e.time).put("k", e.kind.name).put("title", e.title).put("d", e.details).toString()

    @Synchronized
    fun add(kind: ErrorKind, title: String, details: String, time: Long = System.currentTimeMillis()) {
        val e = ErrorEntry(time, kind, title, details.take(MAX_DETAILS))
        _entries.value = (listOf(e) + _entries.value).sortedByDescending { it.time }
        // Append synchronously: this may be the last thing the process does.
        runCatching { file.appendText(encode(e) + "\n") }
    }

    fun clear() {
        _entries.value = emptyList()
        runCatching { file.delete() }
    }

    /**
     * Android remembers why earlier runs ended. Takes over ANRs (with the thread dump), native
     * crashes and kills for excessive resource use that are new since the last start.
     */
    fun importExitReasons(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        val since = prefs.getLong("exit_seen", 0)
        val reasons = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 30) }.getOrDefault(emptyList())
        var newest = since
        for (r in reasons.filter { it.timestamp > since }.sortedBy { it.timestamp }) {
            newest = maxOf(newest, r.timestamp)
            val kind = when (r.reason) {
                ApplicationExitInfo.REASON_ANR -> ErrorKind.Anr
                ApplicationExitInfo.REASON_CRASH_NATIVE -> ErrorKind.Native
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_FREEZER -> ErrorKind.Killed
                else -> continue // Java crashes come from our own handler; normal exits are no errors.
            }
            val trace = runCatching { r.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            add(kind, "${r.processName}: ${r.description ?: kind.name}".take(200), "pss=${r.pss} kB, rss=${r.rss} kB, importance=${r.importance}\n\n$trace", r.timestamp)
        }
        prefs.edit().putLong("exit_seen", newest).apply()
    }

    /** Everything as plain text, for "Copy" and "Share". */
    fun asText(): String = buildString {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        append("ShizuBox ${de.emil.shizubox.BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})\n\n")
        _entries.value.forEach { e ->
            append("=== ${df.format(java.util.Date(e.time))} ${e.kind} — ${e.title}\n${e.details.trim()}\n\n")
        }
    }

    /** Developer switch (DebugReceiver `--es strict on|off`): logs disk/network on the main thread and leaks. */
    fun setStrict(on: Boolean) = prefs.edit().putBoolean("strict", on).apply()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedRegistrationObjects().detectActivityLeaks()
                .detectLeakedSqlLiteObjects().penaltyLog().build(),
        )
    }
}
