package de.emil.shizubox.cleaner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import de.emil.shizubox.R
import de.emil.shizubox.system.ScreenTime
import de.emil.shizubox.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.PriorityQueue

/** Fixed order = fixed colour slot (part-to-whole bar). */
enum class StorageCat(val labelRes: Int) {
    Apps(R.string.sa_apps), Images(R.string.sa_images), Videos(R.string.sa_videos), Audio(R.string.sa_audio),
    Documents(R.string.sa_docs), Downloads(R.string.sa_downloads), System(R.string.sa_system), Other(R.string.sa_other),
    ;
    val label get() = t(labelRes)
}

data class AppStorage(val pkg: String, val label: String, val apk: Long, val data: Long, val cache: Long, val extData: Long, val obb: Long) {
    /** StorageStats' data already contains Android/data, its app bytes the OBBs. */
    val total get() = apk + data
}

data class Analysis(
    val time: Long,
    val numbers: StorageNumbers,
    val categories: Map<StorageCat, Long>,
    /** Recursive size of every folder (incl. Android/data and obb). */
    val dirSizes: Map<String, Long>,
    val top: List<FsEntry>,
    val apps: List<AppStorage>,
    val files: Int,
)

/**
 * Who uses the space. One streamed `find` over the whole internal storage (Android/data and obb
 * included, through the shell) adds every file to all its parent folders – memory grows with the
 * number of folders, not files – and keeps the 50 largest files in a heap. Apps come from
 * StorageStatsManager (APK + data incl. Android/data, cache), the rest of "used" is the system.
 */
object StorageAnalysis {
    private val _result = MutableStateFlow<Analysis?>(null)
    val result: StateFlow<Analysis?> = _result.asStateFlow()
    private const val TOP = 50

    suspend fun scan(context: Context, progress: (String) -> Unit = {}): Analysis = withContext(Dispatchers.IO) {
        val numbers = StorageAccess.numbers()
        val dirs = HashMap<String, Long>(4096)
        val cats = LinkedHashMap<StorageCat, Long>().apply { StorageCat.entries.forEach { put(it, 0L) } }
        val top = PriorityQueue<FsEntry>(TOP + 1, compareBy { it.size })
        var files = 0
        val android = Fs.ROOT + "/Android/"
        val download = Fs.ROOT + "/Download/"
        Fs.find(listOf(Fs.ROOT + "/"), "-mindepth 1") { seq ->
            for (e in seq) {
                if (e.dir) {
                    dirs.putIfAbsent(e.path, 0L)
                    continue
                }
                files++
                if (files % 2000 == 0) progress(t(R.string.sa_progress, files))
                var p = e.path.substringBeforeLast('/')
                while (p.length >= Fs.ROOT.length) {
                    dirs[p] = (dirs[p] ?: 0L) + e.size
                    if (p == Fs.ROOT) break
                    p = p.substringBeforeLast('/')
                }
                top.add(e)
                if (top.size > TOP) top.poll()
                // Android/… belongs to the apps (counted through StorageStats below).
                if (!e.path.startsWith(android)) {
                    val c = if (e.path.startsWith(download)) StorageCat.Downloads else when (fileKind(e.name)) {
                        0 -> StorageCat.Images
                        1 -> StorageCat.Videos
                        2 -> StorageCat.Audio
                        3 -> StorageCat.Documents
                        else -> StorageCat.Other
                    }
                    cats[c] = cats.getValue(c) + e.size
                }
            }
        }
        progress(t(R.string.sa_progress_apps))
        val apps = appSizes(context, dirs)
        cats[StorageCat.Apps] = apps.sumOf { it.total }
        val counted = cats.values.sum()
        cats[StorageCat.System] = (numbers.used - counted).coerceAtLeast(0)
        Analysis(System.currentTimeMillis(), numbers, cats, dirs, top.sortedByDescending { it.size }, apps.sortedByDescending { it.total }, files)
            .also { _result.value = it }
    }

    private suspend fun appSizes(context: Context, dirs: Map<String, Long>): List<AppStorage> {
        ScreenTime.ensureAccess(context)
        val pm = context.packageManager
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES).mapNotNull { ai ->
            val s = runCatching { ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, user) }.getOrNull() ?: return@mapNotNull null
            AppStorage(
                ai.packageName, ai.loadLabel(pm).toString(), s.appBytes, s.dataBytes, s.cacheBytes,
                dirs["${Fs.DATA}/${ai.packageName}"] ?: 0L, dirs["${Fs.OBB}/${ai.packageName}"] ?: 0L,
            )
        }.filter { it.total > 0 }
    }

    /** After moving something to the trash: take it out of the numbers without a new scan. */
    fun forget(paths: Collection<String>, sizes: Map<String, Long>) {
        val a = _result.value ?: return
        val dirs = HashMap(a.dirSizes)
        for (path in paths) {
            val size = sizes[path] ?: dirs[path] ?: 0L
            dirs.keys.removeAll { it == path || it.startsWith("$path/") }
            var p = path.substringBeforeLast('/')
            while (p.length >= Fs.ROOT.length) {
                dirs[p]?.let { dirs[p] = (it - size).coerceAtLeast(0) }
                if (p == Fs.ROOT) break
                p = p.substringBeforeLast('/')
            }
        }
        _result.value = a.copy(dirSizes = dirs, top = a.top.filterNot { e -> paths.any { e.path == it || e.path.startsWith("$it/") } })
    }

    private val IMG = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng", "avif")
    private val VID = setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v", "ts")
    private val AUD = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr", "mid", "wma")
    private val DOC = setOf("pdf", "doc", "docx", "odt", "txt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "md")

    private fun fileKind(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        in IMG -> 0
        in VID -> 1
        in AUD -> 2
        in DOC -> 3
        else -> 4
    }
}
