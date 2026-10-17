package de.emil.shizubox.cleaner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TrashItem(val id: Long, val orig: String, val loc: String, val size: Long, val dir: Boolean, val time: Long, val tool: CleanTool)

/** Something a tool wants to remove; [size] of a folder is its whole content (-1 = unknown). */
data class Candidate(val path: String, val size: Long, val dir: Boolean)

data class MoveResult(val moved: Int, val bytes: Long, val failed: List<Pair<String, String>>)

enum class LogAction { Trash, Restore, Delete, Expired, Blocked, Failed }

data class LogEntry(val time: Long, val action: LogAction, val tool: CleanTool?, val path: String, val size: Long, val msg: String)

/**
 * ShizuBox' trash: nothing a cleaner tool finds is deleted right away. Items are renamed (never
 * copied, see [Fs]) into a trash folder where a rename is possible in both directions.
 * Android 17 keeps per-app quota project IDs (f2fs prjquota) on Android/data/<pkg> and on its
 * cache folder separately; a rename into a folder that inherits another project ID fails with
 * EXDEV. Tested with ShizuBox' own folders and test files:
 * - Android/data/<pkg>/cache/x → Android/data/<pkg>/files/…: EXDEV; cache → its own parent: EXDEV
 * - anything in Android/data → `Android/data/.ShizuBoxTrash/…` (no inherited ID there) and back: OK,
 *   also a whole cache folder; the same for Android/obb
 * - Android/data → `/.ShizuBoxTrash`: EXDEV (or works once and cannot come back)
 * - shared storage incl. Android/media ↔ `/.ShizuBoxTrash`: OK both ways
 * Hence three trash folders: `/.ShizuBoxTrash` (with `.nomedia`), `Android/data/.ShizuBoxTrash`,
 * `Android/obb/.ShizuBoxTrash`.
 * A rename is instant and needs no extra space – the space is only freed when the trash is
 * emptied. The index (original path, size, tool) and the cleaner log live in `cleaner.db`
 * (device-protected, not in the backup).
 */
object Trash {
    private lateinit var db: SQLiteDatabase
    private val lock = Mutex()
    private val _items = MutableStateFlow<List<TrashItem>>(emptyList())
    val items: StateFlow<List<TrashItem>> = _items.asStateFlow()
    private const val LOG_KEEP_MS = 90L * 86_400_000L
    const val INTERNAL = "internal:"

    fun init(context: Context) {
        db = Helper(context.createDeviceProtectedStorageContext()).writableDatabase
        _items.value = query()
    }

    fun zoneDir(path: String) = when {
        path.startsWith(Fs.DATA + "/") -> Guard.TRASH_DIRS[1]
        path.startsWith(Fs.OBB + "/") -> Guard.TRASH_DIRS[2]
        else -> Guard.TRASH_DIRS[0]
    }

    /** Drops entries that are inside another entry (the folder moves with its content). */
    private fun topLevel(list: List<Candidate>): List<Candidate> {
        val sorted = list.distinctBy { it.path }.sortedBy { it.path }
        val out = ArrayList<Candidate>()
        for (c in sorted) if (out.none { c.path.startsWith(it.path + "/") }) out += c
        return out
    }

    suspend fun move(candidates: List<Candidate>, tool: CleanTool, onProgress: (Int, Int) -> Unit = { _, _ -> }): MoveResult = lock.withLock {
        withContext(Dispatchers.IO) {
            Guard.installedPackages(force = true)
            val list = topLevel(candidates)
            val unknown = list.filter { it.size < 0 }.map { it.path }
            val sizes = if (unknown.isEmpty()) emptyMap() else Fs.sizes(unknown)
            if (list.any { zoneDir(it.path) == Guard.TRASH_DIRS[0] }) {
                ShizukuManager.exec("mkdir -p ${Fs.q(Guard.TRASH_DIRS[0])} && touch ${Fs.q(Guard.TRASH_DIRS[0] + "/.nomedia")}", 5000, 15_000)
            }
            var moved = 0
            var bytes = 0L
            val failed = ArrayList<Pair<String, String>>()
            list.forEachIndexed { i, c ->
                onProgress(i, list.size)
                val size = if (c.size >= 0) c.size else sizes[c.path] ?: 0L
                val reason = Guard.blockReason(c.path, tool)
                if (reason != null) {
                    failed += c.path to reason
                    log(LogAction.Blocked, tool, c.path, size, reason)
                    return@forEachIndexed
                }
                val now = System.currentTimeMillis()
                val id = db.insert("trash", null, ContentValues().apply {
                    put("orig", c.path); put("loc", ""); put("size", size); put("dir", if (c.dir) 1 else 0); put("time", now); put("tool", tool.name)
                })
                val loc = "${zoneDir(c.path)}/$id"
                val err = ShizukuManager.rename(c.path, loc)
                if (err == null) {
                    db.update("trash", ContentValues().apply { put("loc", loc) }, "id = ?", arrayOf(id.toString()))
                    moved++
                    bytes += size
                    log(LogAction.Trash, tool, c.path, size, "")
                } else {
                    db.delete("trash", "id = ?", arrayOf(id.toString()))
                    failed += c.path to err
                    log(LogAction.Failed, tool, c.path, size, err)
                }
            }
            onProgress(list.size, list.size)
            _items.value = query()
            MoveResult(moved, bytes, failed)
        }
    }

    /**
     * ShizuBox' own temporary files live in its private cache, which the shell cannot rename into
     * the trash – they are copied there through the shell service ([ShizukuManager.writeFile]) and
     * then deleted. The original path is kept as "internal:<path>".
     */
    suspend fun moveInternal(files: List<java.io.File>, tool: CleanTool): MoveResult = lock.withLock {
        withContext(Dispatchers.IO) {
            var moved = 0
            var bytes = 0L
            val failed = ArrayList<Pair<String, String>>()
            ShizukuManager.exec("mkdir -p ${Fs.q(Guard.TRASH_DIRS[0])} && touch ${Fs.q(Guard.TRASH_DIRS[0] + "/.nomedia")}", 5000, 15_000)
            for (f in files.filter { it.isFile }) {
                val size = f.length()
                val id = db.insert("trash", null, ContentValues().apply {
                    put("orig", INTERNAL + f.absolutePath); put("loc", ""); put("size", size); put("dir", 0); put("time", System.currentTimeMillis()); put("tool", tool.name)
                })
                val loc = "${Guard.TRASH_DIRS[0]}/$id"
                val written = runCatching {
                    // Closed here too: the binder only sends a duplicate of the descriptor.
                    android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { ShizukuManager.writeFile(it, loc) }
                }.getOrDefault(-1L)
                if (written == size && f.delete()) {
                    db.update("trash", ContentValues().apply { put("loc", loc) }, "id = ?", arrayOf(id.toString()))
                    moved++
                    bytes += size
                    log(LogAction.Trash, tool, f.absolutePath, size, "")
                } else {
                    db.delete("trash", "id = ?", arrayOf(id.toString()))
                    if (written >= 0) Fs.remove(listOf(loc))
                    failed += f.absolutePath to "copy"
                    log(LogAction.Failed, tool, f.absolutePath, size, "copy")
                }
            }
            _items.value = query()
            MoveResult(moved, bytes, failed)
        }
    }

    /** Puts items back; if the original place is taken meanwhile, next to it as "name (2)". */
    suspend fun restore(ids: Collection<Long>): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val items = _items.value.filter { it.id in ids }
            var ok = 0
            for (it in items) {
                if (it.orig.startsWith(INTERNAL)) {
                    // Back into ShizuBox' private cache: the app reads the trash copy itself (all-files access).
                    val dest = java.io.File(it.orig.removePrefix(INTERNAL))
                    val done = runCatching { dest.parentFile?.mkdirs(); java.io.File(it.loc).copyTo(dest, overwrite = true) }.isSuccess
                    if (done) {
                        Fs.remove(listOf(it.loc))
                        db.delete("trash", "id = ?", arrayOf(it.id.toString()))
                        ok++
                        log(LogAction.Restore, it.tool, dest.absolutePath, it.size, "")
                    } else {
                        log(LogAction.Failed, it.tool, it.orig, it.size, "restore: no file access")
                    }
                    continue
                }
                // An emptied app cache was recreated by the app meanwhile: the old one replaces it.
                if (it.dir && Regex("""^${Regex.escape(Fs.DATA)}/[^/]+/cache$""").matches(it.orig)) {
                    val there = existing(listOf(it.orig))
                    if (there == null) {
                        log(LogAction.Failed, it.tool, it.orig, it.size, "restore: check failed")
                        continue
                    }
                    if (there.isNotEmpty()) Fs.remove(listOf(it.orig))
                }
                // Checked freshly for every item by the shell service right before the rename, which
                // never overwrites: two items with the same original place both come back (0.11.1).
                val (target, err) = restoreInto(it.orig, it.dir) { to -> ShizukuManager.rename(it.loc, to) }
                if (target != null) {
                    db.delete("trash", "id = ?", arrayOf(it.id.toString()))
                    ok++
                    log(LogAction.Restore, it.tool, target, it.size, "")
                } else {
                    log(LogAction.Failed, it.tool, it.orig, it.size, "restore: $err")
                }
            }
            _items.value = query()
            ok
        }
    }

    /** Deletes for good. Returns the freed bytes. */
    suspend fun purge(ids: Collection<Long>, expired: Boolean = false): Long = lock.withLock {
        withContext(Dispatchers.IO) {
            val items = _items.value.filter { it.id in ids }
            if (items.isEmpty()) return@withContext 0L
            // Never anything outside the trash folders, whatever the index says.
            val safe = items.filter { i -> Guard.TRASH_DIRS.any { i.loc.startsWith("$it/") } }
            Fs.remove(safe.map { it.loc })
            // Unknown whether anything is left: keep every row (fail safe), the next purge tries again.
            val left = existing(safe.map { it.loc }) ?: safe.map { it.loc }.toSet()
            var freed = 0L
            for (i in safe) {
                if (i.loc in left) {
                    log(LogAction.Failed, i.tool, i.orig, i.size, "delete")
                    continue
                }
                db.delete("trash", "id = ?", arrayOf(i.id.toString()))
                freed += i.size
                log(if (expired) LogAction.Expired else LogAction.Delete, i.tool, i.orig, i.size, "")
            }
            _items.value = query()
            freed
        }
    }

    suspend fun purgeAll() = purge(_items.value.map { it.id })

    suspend fun purgeExpired(days: Int = CleanerSettings.current.trashDays): Long {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val ids = _items.value.filter { it.time < cutoff }.map { it.id }
        return if (ids.isEmpty()) 0L else purge(ids, expired = true)
    }

    /**
     * Forgets entries whose files were removed by someone else (e.g. a file manager), and repairs
     * a move that was interrupted between the rename and the index update (process killed): such a
     * row has no location yet – if the file is in the trash it is shown again, otherwise forgotten.
     */
    suspend fun reconcile() = lock.withLock {
        withContext(Dispatchers.IO) {
            val half = db.rawQuery("SELECT id, orig FROM trash WHERE loc = ''", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
            }
            if (half.isNotEmpty()) {
                val guess = half.associate { (id, orig) -> id to (if (orig.startsWith(INTERNAL)) Guard.TRASH_DIRS[0] else zoneDir(orig)) + "/$id" }
                // Without a working check nothing is forgotten (fail safe).
                val there = existing(guess.values.toList()) ?: return@withContext
                for ((id, loc) in guess) {
                    if (loc in there) db.update("trash", ContentValues().apply { put("loc", loc) }, "id = ?", arrayOf(id.toString()))
                    else db.delete("trash", "id = ?", arrayOf(id.toString()))
                }
            }
            _items.value = query()
            val all = _items.value
            if (all.isEmpty()) return@withContext
            val present = existing(all.map { it.loc })
            staleRows(all, present).forEach { id -> db.delete("trash", "id = ?", arrayOf(id.toString())) }
            _items.value = query()
        }
    }

    /** Test hook (DebugReceiver): [existing] fails as if the shell call broke. */
    @Volatile internal var failExistingForTest = false

    /**
     * Which of [paths] exist right now (one shell call per 200); null if that could not be checked
     * (Shizuku gone, timeout, cut-off output) – callers then change nothing. Before 0.11.1 a failed
     * call looked like "nothing exists" and reconcile dropped the whole index.
     */
    private suspend fun existing(paths: List<String>): Set<String>? {
        if (failExistingForTest) return null
        val out = HashSet<String>()
        for (chunk in paths.chunked(200)) {
            val r = try {
                ShizukuManager.exec("for p in ${chunk.joinToString(" ") { Fs.q(it) }}; do [ -e \"\$p\" ] && echo \"\$p\"; done; echo $END", 5000, 60_000)
            } catch (_: ShizukuUnavailableException) {
                return null
            }
            val lines = r.out.lines()
            if (!r.ok || lines.lastOrNull() != END) return null
            out += lines.dropLast(1).filter { it.isNotEmpty() }
        }
        return out
    }

    private const val END = "--sbx-end--"

    val totalBytes get() = _items.value.sumOf { it.size }

    private fun query(): List<TrashItem> = db.rawQuery("SELECT id, orig, loc, size, dir, time, tool FROM trash WHERE loc != '' ORDER BY time DESC, id DESC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(
                TrashItem(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4) == 1, c.getLong(5),
                    runCatching { CleanTool.valueOf(c.getString(6)) }.getOrDefault(CleanTool.Analysis)),
            )
        }
    }

    // ------------------------------------------------------------------ log

    fun log(action: LogAction, tool: CleanTool?, path: String, size: Long, msg: String) {
        db.insert("log", null, ContentValues().apply {
            put("time", System.currentTimeMillis()); put("action", action.name); put("tool", tool?.name); put("path", path); put("size", size); put("msg", msg)
        })
    }

    /** The log keeps 90 days – also when the trash is empty (it used to be pruned only with expired items). */
    fun pruneLog() = db.delete("log", "time < ?", arrayOf((System.currentTimeMillis() - LOG_KEEP_MS).toString()))

    suspend fun logEntries(limit: Int = 500): List<LogEntry> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT time, action, tool, path, size, msg FROM log ORDER BY time DESC LIMIT $limit", null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    LogEntry(c.getLong(0), runCatching { LogAction.valueOf(c.getString(1)) }.getOrDefault(LogAction.Failed),
                        c.getString(2)?.let { runCatching { CleanTool.valueOf(it) }.getOrNull() }, c.getString(3), c.getLong(4), c.getString(5).orEmpty()),
                )
            }
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "cleaner.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE trash (id INTEGER PRIMARY KEY AUTOINCREMENT, orig TEXT NOT NULL, loc TEXT NOT NULL, size INTEGER NOT NULL, dir INTEGER NOT NULL, time INTEGER NOT NULL, tool TEXT NOT NULL)")
            db.execSQL("CREATE TABLE log (time INTEGER NOT NULL, action TEXT NOT NULL, tool TEXT, path TEXT NOT NULL, size INTEGER NOT NULL, msg TEXT)")
            db.execSQL("CREATE INDEX log_time ON log(time)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

/** "a.jpg", then "a (2).jpg" … "a (99).jpg" (folders without splitting off an extension). */
internal fun restoreCandidates(orig: String, dir: Boolean): Sequence<String> = sequence {
    yield(orig)
    val parent = orig.substringBeforeLast('/')
    val base = orig.substringAfterLast('/')
    val dot = base.lastIndexOf('.').takeIf { it > 0 && !dir } ?: base.length
    for (n in 2..99) yield("$parent/${base.substring(0, dot)} ($n)${base.substring(dot)}")
}

/**
 * Tries the [restoreCandidates] one after another with a [rename] that never overwrites; returns
 * the place used, or null and the last error. Any error other than "exists" stops at once.
 */
internal suspend fun restoreInto(orig: String, dir: Boolean, rename: suspend (String) -> String?): Pair<String?, String?> {
    var err: String? = null
    for (to in restoreCandidates(orig, dir)) {
        err = rename(to) ?: return to to null
        if (err != ShizukuManager.RENAME_EXISTS) break
    }
    return null to err
}

/** Index rows whose file is gone; none if [present] is unknown (failed check → fail safe). */
internal fun staleRows(all: List<TrashItem>, present: Set<String>?): List<Long> =
    if (present == null) emptyList() else all.filter { it.loc !in present }.map { it.id }
