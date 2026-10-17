package de.emil.shizubox.cleaner

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** One thing a tool found. [internal] = ShizuBox' private file (moved by copy, see [Trash.moveInternal]). */
data class Finding(
    val path: String,
    val size: Long,
    val mtime: Long,
    val dir: Boolean,
    val note: String = "",
    val files: Int = 1,
    val internal: Boolean = false,
)

/**
 * Junk in the shared storage, by category. One streamed `find` over the internal storage without
 * `Android/` (app folders belong to the app cleaner and the leftover finder; e.g. WhatsApp keeps
 * live `.tmp` files in Android/media/…/.Shared) and without the trash – tested on Android 17:
 * `find R/ -mindepth 1 \( -path R/Android -o -path R/.ShizuBoxTrash \) -prune -o -printf …`.
 * Folders are only remembered as candidates by name; their content size is summed while streaming.
 */
object SystemCleaner {
    private val _result = MutableStateFlow<Map<SysCat, List<Finding>>?>(null)
    val result: StateFlow<Map<SysCat, List<Finding>>?> = _result.asStateFlow()

    private val LOG_EXT = setOf("log", "tmp", "temp", "dmp", "mdmp")
    private val PART_EXT = setOf("part", "crdownload", "partial", "opdownload")
    private val APK_EXT = setOf("apk", "apks", "xapk", "apkm")
    private val TRASH_DIR = Regex("""^\.trash(-\d+)?$""", RegexOption.IGNORE_CASE)

    fun clear(paths: Collection<String>) {
        val set = paths.toSet()
        _result.value = _result.value?.mapValues { (_, l) -> l.filterNot { it.path in set } }
    }

    suspend fun scan(context: Context, cats: Set<SysCat> = SysCat.entries.toSet(), report: (String) -> Unit = {}): Map<SysCat, List<Finding>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val out = HashMap<SysCat, MutableList<Finding>>()
        fun add(c: SysCat, f: Finding) { if (c in cats) out.getOrPut(c) { ArrayList() } += f }
        // Candidate folders (path → category) and their content.
        val dirCands = HashMap<String, SysCat>()
        val dirSize = HashMap<String, Long>()
        val dirFiles = HashMap<String, Int>()
        val dirTime = HashMap<String, Long>()
        val children = HashMap<String, Int>()
        val dirs = ArrayList<FsEntry>()
        var n = 0
        val r = Fs.ROOT
        Fs.find(listOf("$r/"), "-mindepth 1 \\( -path ${Fs.q("$r/Android")} -o -path ${Fs.q(Guard.TRASH_DIRS[0])} \\) -prune -o") { seq ->
            for (e in seq) {
                if (++n % 2000 == 0) report(t(R.string.sa_progress, n))
                children[e.parent] = (children[e.parent] ?: 0) + 1
                // Inside a candidate folder: only counts towards it.
                var p = e.parent
                var owner: String? = null
                while (p.length > r.length) {
                    if (p in dirCands) owner = p
                    p = p.substringBeforeLast('/')
                }
                if (owner != null) {
                    if (!e.dir) {
                        dirSize[owner] = (dirSize[owner] ?: 0L) + e.size
                        dirFiles[owner] = (dirFiles[owner] ?: 0) + 1
                    }
                    continue
                }
                val name = e.name
                val lower = name.lowercase()
                val ext = lower.substringAfterLast('.', "")
                if (e.dir) {
                    val c = when {
                        lower == ".thumbnails" -> SysCat.Thumbnails
                        lower == ".temp" || lower == ".tmp" -> SysCat.LogsTemp
                        TRASH_DIR.matches(name) -> SysCat.ForeignTrash
                        else -> null
                    }
                    if (c != null) {
                        dirCands[e.path] = c
                        dirTime[e.path] = e.mtime
                    } else {
                        dirs += e
                    }
                    continue
                }
                when {
                    lower.startsWith(".trashed-") -> add(SysCat.ForeignTrash, Finding(e.path, e.size, e.mtime, false))
                    lower.startsWith(".pending-") -> if (now - e.mtime > 86_400_000L) add(SysCat.Partial, Finding(e.path, e.size, e.mtime, false))
                    ext in PART_EXT -> if (now - e.mtime > 3_600_000L) add(SysCat.Partial, Finding(e.path, e.size, e.mtime, false))
                    ext in LOG_EXT || lower.startsWith("tombstone_") -> add(SysCat.LogsTemp, Finding(e.path, e.size, e.mtime, false))
                    ext in APK_EXT && e.path.startsWith("$r/Download/") -> add(SysCat.OldApks, Finding(e.path, e.size, e.mtime, false))
                }
            }
        }
        for ((path, c) in dirCands) {
            add(c, Finding(path, dirSize[path] ?: 0L, dirTime[path] ?: 0L, true, files = dirFiles[path] ?: 0))
        }
        for (d in dirs) {
            if ((children[d.path] ?: 0) == 0) add(SysCat.EmptyDirs, Finding(d.path, 0, d.mtime, true, files = 0))
        }
        if (SysCat.OldApks in cats) out[SysCat.OldApks]?.let { list -> out[SysCat.OldApks] = annotateApks(context, list).toMutableList() }
        if (SysCat.OwnLeftovers in cats) out[SysCat.OwnLeftovers] = ownLeftovers(context).toMutableList()
        // Everything goes past the guard once more – a locked or excluded path is never offered.
        val res = SysCat.entries.filter { it in cats }.associateWith { c ->
            out[c].orEmpty().filter { it.internal || Guard.blockReason(it.path, CleanTool.System) == null }.sortedByDescending { it.size }
        }
        _result.value = res
        res
    }

    /** "Installed (1.2)", "Newer than installed", "Not installed" – from the APK or the bundle. */
    private suspend fun annotateApks(context: Context, list: List<Finding>): List<Finding> {
        val pm = context.packageManager
        return list.take(200).map { f ->
            val info: Pair<String, String>? = runCatching {
                if (f.path.endsWith(".apk", true)) {
                    pm.getPackageArchiveInfo(f.path, 0)?.let { it.packageName to (it.versionName ?: "") }
                } else {
                    withTimeoutOrNull(10_000) {
                        de.emil.shizubox.installer.ApkParser.parse(context, Uri.fromFile(File(f.path))).let { it.packageName to it.versionName }
                    }
                }
            }.getOrNull()
            if (info == null || info.first.isEmpty()) return@map f
            val installed = runCatching { pm.getPackageInfo(info.first, PackageManager.MATCH_UNINSTALLED_PACKAGES).versionName }.getOrNull()
            val note = when {
                installed == null -> t(R.string.sc_apk_not_installed, info.second)
                installed == info.second -> t(R.string.sc_apk_installed_same, info.second)
                else -> t(R.string.sc_apk_installed_other, info.second, installed)
            }
            f.copy(note = note)
        } + list.drop(200)
    }

    /**
     * ShizuBox' own temporary files: copies for the APK parser and installer, VirusTotal uploads,
     * shared APKs, update downloads older than an hour, the downloader's temp folder when no download
     * runs, debug screenshots, plus its external cache (Android/data/de.emil.shizubox/cache).
     */
    private suspend fun ownLeftovers(context: Context): List<Finding> {
        val hourAgo = System.currentTimeMillis() - 3_600_000L
        val prefixes = listOf("parse_", "split", "vt_upload_", "vt_probe_", "shot_")
        val files = ArrayList<File>()
        context.cacheDir.listFiles().orEmpty().forEach { f ->
            when {
                f.isFile && prefixes.any { f.name.startsWith(it) } -> files += f
                f.isDirectory && f.name in setOf("shared_apks", "apk_parse_fallback") -> files += f.walkTopDown().filter { it.isFile }
                f.isDirectory && f.name == "updates" -> files += f.listFiles().orEmpty().filter { it.isFile && it.lastModified() < hourAgo }
                // Paused or failed downloads resume from their temp files – only when every download is done.
                f.isDirectory && f.name == "ytdl" && de.emil.shizubox.downloader.DownloadQueue.tasks.value.all { it.state == de.emil.shizubox.downloader.TaskState.DONE } ->
                    files += f.walkTopDown().filter { it.isFile && it.lastModified() < hourAgo }
            }
        }
        val internal = files.map { Finding(it.absolutePath, it.length(), it.lastModified(), false, internal = true) }
        val external = runCatching { Fs.list("${Fs.DATA}/${Guard.SELF}/cache") }.getOrDefault(emptyList())
        val sizes = if (external.isEmpty()) emptyMap() else Fs.sizes(external.map { it.path })
        return internal + external.map { Finding(it.path, sizes[it.path] ?: it.size, it.mtime, it.dir) }
    }

    /** Moves the chosen findings to the trash (ShizuBox' private ones by copy). */
    suspend fun clean(findings: List<Finding>, tool: CleanTool = CleanTool.System, onProgress: (Int, Int) -> Unit = { _, _ -> }): MoveResult {
        val (internal, shared) = findings.partition { it.internal }
        val a = Trash.move(shared.map { Candidate(it.path, if (it.dir && it.size == 0L && it.files > 0) -1 else it.size, it.dir) }, tool, onProgress)
        val b = if (internal.isEmpty()) MoveResult(0, 0, emptyList()) else Trash.moveInternal(internal.map { File(it.path) }, tool)
        clear(findings.map { it.path })
        return MoveResult(a.moved + b.moved, a.bytes + b.bytes, a.failed + b.failed)
    }
}
