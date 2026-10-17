package de.emil.shizubox.cleaner

import android.content.Context
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.system.CacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AppCacheInfo(
    val pkg: String,
    val label: String,
    /** All cache (StorageStats: private + Android/data/<pkg>/cache). */
    val total: Long,
    /** The part in Android/data/<pkg>/cache – this one ShizuBox can move to the trash. */
    val ext: Long,
    val running: Boolean,
) {
    val path get() = "${Fs.DATA}/$pkg/cache"
}

/**
 * Caches of apps. The private part (/data/data/<pkg>/cache) is only reachable through the system:
 * `pm trim-caches 999G` (all apps, see [CacheCleaner]) – `pm clear --cache-only` hangs on Android 17.
 * The external part, `Android/data/<pkg>/cache`, ShizuBox moves per app into the trash (the whole
 * folder, a rename into `Android/data/.ShizuBoxTrash`; the app creates a new one when it needs it).
 * Tested on Android 17: `du -sk` on the glob of all Android/data cache folders sizes them in one call (0.1 s),
 * `ps -A -o NAME=` lists running processes (0.1 s). Running apps are skipped unless stopped first,
 * so no app writes into a folder that just went away. AICore (on-device AI models) is left out
 * unless switched on in the settings.
 */
object AppCleaner {
    private val _result = MutableStateFlow<List<AppCacheInfo>?>(null)
    val result: StateFlow<List<AppCacheInfo>?> = _result.asStateFlow()

    suspend fun scan(context: Context): List<AppCacheInfo> = withContext(Dispatchers.IO) {
        val base = CacheCleaner.list(context)
        val ext = HashMap<String, Long>()
        ShizukuManager.execRecords("du -sk ${Fs.q(Fs.DATA)}/*/cache", 120_000, '\n') { lines ->
            lines.forEach { l ->
                val tab = l.indexOf('\t')
                if (tab > 0) {
                    val pkg = l.substring(tab + 1).removePrefix(Fs.DATA + "/").substringBefore('/')
                    // An empty folder still counts 4 KB in du – that is nothing to clean.
                    l.substring(0, tab).toLongOrNull()?.takeIf { it > 8 }?.let { ext[pkg] = it * 1024 }
                }
            }
        }
        val running = runningPackages()
        val labels = base.associate { it.pkg to it.label }
        val pkgs = (base.map { it.pkg } + ext.keys).distinct()
        val pm = context.packageManager
        val list = pkgs.map { p ->
            AppCacheInfo(
                p, labels[p] ?: runCatching { pm.getApplicationInfo(p, 0).loadLabel(pm).toString() }.getOrDefault(p),
                maxOf(base.firstOrNull { it.pkg == p }?.bytes ?: 0L, ext[p] ?: 0L), ext[p] ?: 0L, p in running,
            )
        }.filter { it.total > 0 }.sortedByDescending { it.total }
        _result.value = list
        list
    }

    suspend fun runningPackages(): Set<String> =
        ShizukuManager.exec("ps -A -o NAME=", 5000, 15_000).out.lineSequence().map { it.trim().substringBefore(':') }.filter { it.contains('.') }.toSet()

    /** Whether the app cleaner may move this app's cache (lock reasons come from the [Guard]). */
    fun blockReason(app: AppCacheInfo): String? = if (app.ext <= 0) "" else Guard.blockReason(app.path, CleanTool.AppCache)

    /**
     * Moves the Android/data caches of [apps] to the trash. Running apps are skipped, or stopped
     * first when [stopRunning]. Returns the trash result.
     */
    suspend fun clean(apps: List<AppCacheInfo>, stopRunning: Boolean, tool: CleanTool = CleanTool.AppCache, onProgress: (Int, Int) -> Unit = { _, _ -> }): MoveResult {
        Guard.installedPackages(force = true)
        val running = runningPackages()
        val todo = ArrayList<AppCacheInfo>()
        val skipped = ArrayList<Pair<String, String>>()
        for (a in apps) {
            if (a.pkg == Guard.SELF) continue // own external cache: system cleaner → ShizuBox leftovers
            if (a.pkg in running) {
                if (!stopRunning) { skipped += a.path to de.emil.shizubox.t(de.emil.shizubox.R.string.ac_skipped_running); continue }
                ShizukuManager.exec("am force-stop ${Fs.q(a.pkg)}", 5000, 15_000)
            }
            todo += a
        }
        val r = Trash.move(todo.map { Candidate(it.path, it.ext, true) }, tool, onProgress)
        val movedPaths = todo.map { it.path }.toSet() - r.failed.map { it.first }.toSet()
        _result.value = _result.value?.map { if (it.path in movedPaths) it.copy(total = (it.total - it.ext).coerceAtLeast(0), ext = 0) else it }
        return r.copy(failed = skipped + r.failed)
    }

    /** The existing "clean everything" (system, all apps, not restorable). */
    suspend fun trimAll(context: Context): Long = CacheCleaner.clearAll(context)
}
