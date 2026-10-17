package de.emil.shizubox.cleaner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class DupFile(val path: String, val size: Long, val mtime: Long, val width: Int = 0, val height: Int = 0) {
    val pixels get() = width.toLong() * height
}

/** A set of identical (or, with [similar], visually alike) files; [keep] are the ones that stay. */
data class DupGroup(val id: String, val files: List<DupFile>, val similar: Boolean, val keep: Set<String>) {
    val removable get() = files.filter { it.path !in keep }
    val waste get() = removable.sumOf { it.size }
}

enum class KeepRule { Camera, Oldest, Resolution }

/**
 * Duplicate files in chosen folders. Exact: the shell lists candidates in one `find`
 * (`-type f -size +Nk`, hidden folders excluded – .thumbnails or .Shared are app caches, not your files; tested on Android 17: 6 folders in 0.9 s), equal sizes only, then a
 * quick hash of the first and last 64 KB, then SHA-256 of the whole file – read by ShizuBox itself
 * (all-files access). Similar pictures (optional): a 64-bit dHash per picture (decoded at 1/8 size
 * or less, cached in `dupe_cache.db` by path+size+date), pictures with ≤ 6 differing bits form a
 * group (series shots, the same picture in another resolution).
 * Every group keeps at least one file – enforced here and in the UI.
 */
object Duplicates {
    private lateinit var appContext: Context
    private val cache: SQLiteDatabase by lazy { CacheDb(appContext).writableDatabase }
    private val _result = MutableStateFlow<List<DupGroup>?>(null)
    val result: StateFlow<List<DupGroup>?> = _result.asStateFlow()
    private const val PART = 64 * 1024
    private const val SIMILAR_BITS = 6
    private val IMAGES = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun update(groups: List<DupGroup>) { _result.value = groups }

    fun forget(paths: Collection<String>) {
        val set = paths.toSet()
        _result.value = _result.value?.mapNotNull { g ->
            val left = g.files.filterNot { it.path in set }
            if (left.size < 2) null else g.copy(files = left, keep = g.keep.intersect(left.map { it.path }.toSet()).ifEmpty { setOf(left.first().path) })
        }
    }

    suspend fun scan(cfg: CleanerConfig = CleanerSettings.current, rule: KeepRule = KeepRule.Camera, report: (String, Float?) -> Unit = { _, _ -> }): List<DupGroup> = withContext(Dispatchers.IO) {
        if (!StorageAccess.ensure()) error(t(R.string.du_no_access))
        val roots = cfg.dupeFolders.map { "${Fs.ROOT}/${it.trim('/')}" }
        report(t(R.string.du_listing), null)
        val files = ArrayList<FsEntry>()
        Fs.find(roots, "-type f -size +${cfg.dupeMinKb}k -not -path '*/.*'") { seq -> seq.forEach { files += it } }
        val unique = files.distinctBy { it.path }.filter { Guard.blockReason(it.path, CleanTool.Duplicates, cfg) == null }
        // 1) same size 2) same first+last 64 KB 3) same SHA-256
        val bySize = unique.groupBy { it.size }.values.filter { it.size > 1 }
        val candidates = bySize.sumOf { it.size }
        var done = 0
        val exact = ArrayList<List<FsEntry>>()
        for (sameSize in bySize) {
            exact += identicalGroups(sameSize) { done++; report(t(R.string.du_hashing, done, candidates), done.toFloat() / candidates.coerceAtLeast(1)) }
        }
        val groups = ArrayList<DupGroup>()
        exact.forEachIndexed { i, l ->
            val files = l.map { DupFile(it.path, it.size, it.mtime) }
            groups += DupGroup("e$i", files, false, setOf(keeper(files, rule).path))
        }
        if (cfg.dupeSimilar) {
            val inExact = exact.flatten().map { it.path }.toSet()
            val images = unique.filter { it.name.substringAfterLast('.', "").lowercase() in IMAGES && it.path !in inExact }.take(20_000)
            groups += similar(images, rule, report)
            pruneCache()
        }
        val sorted = groups.sortedByDescending { it.waste }
        _result.value = sorted
        sorted
    }

    /** Re-applies an automatic choice to every group. */
    fun applyRule(rule: KeepRule) {
        _result.value = _result.value?.map { g -> if (g.similar) g else g.copy(keep = setOf(keeper(g.files, rule).path)) }
    }

    fun keeper(files: List<DupFile>, rule: KeepRule): DupFile {
        val camera = files.filter { it.path.startsWith("${Fs.ROOT}/DCIM/Camera/") }
        return when (rule) {
            KeepRule.Camera -> (camera.ifEmpty { files }).minBy { it.mtime }
            KeepRule.Oldest -> files.minBy { it.mtime }
            KeepRule.Resolution -> files.maxWith(compareBy<DupFile> { it.pixels }.thenBy { it.size }.thenByDescending { it.mtime })
        }
    }

    /**
     * Files of the same size → groups of byte-identical files. The quick hash only sorts out
     * obvious differences; identical always means the same SHA-256 of the whole file. Before 0.11.1
     * files up to 128 KB were taken as identical after the quick hash, which for 64–128 KB only read
     * the first 64 KB (Daniel's review).
     */
    internal suspend fun identicalGroups(sameSize: List<FsEntry>, onHashed: () -> Unit = {}): List<List<FsEntry>> {
        val byQuick = sameSize.groupBy { onHashed(); quickHash(it) }.filterKeys { it != null }.values.filter { it.size > 1 }
        return byQuick.flatMap { q ->
            coroutineContext.ensureActive()
            q.groupBy { fullHash(it) }.filterKeys { it != null }.values.filter { it.size > 1 }
        }
    }

    internal fun quickHash(e: FsEntry): String? = runCatching {
        RandomAccessFile(File(e.path), "r").use { f ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(PART)
            val n = f.read(buf)
            if (n > 0) md.update(buf, 0, n)
            if (e.size > 2 * PART) {
                f.seek(e.size - PART)
                val m = f.read(buf)
                if (m > 0) md.update(buf, 0, m)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    internal fun fullHash(e: FsEntry): String? = runCatching {
        File(e.path).inputStream().buffered(256 * 1024).use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    private suspend fun similar(images: List<FsEntry>, rule: KeepRule, report: (String, Float?) -> Unit): List<DupGroup> {
        val hashes = ArrayList<Pair<DupFile, Long>>()
        images.forEachIndexed { i, e ->
            coroutineContext.ensureActive()
            if (i % 10 == 0) report(t(R.string.du_pictures, i, images.size), i.toFloat() / images.size.coerceAtLeast(1))
            val (h, w, ht) = dHash(e) ?: return@forEachIndexed
            hashes += DupFile(e.path, e.size, e.mtime, w, ht) to h
        }
        // Union-find over all pairs with a small Hamming distance.
        val parent = IntArray(hashes.size) { it }
        fun root(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        for (i in hashes.indices) {
            for (j in i + 1 until hashes.size) {
                if (java.lang.Long.bitCount(hashes[i].second xor hashes[j].second) <= SIMILAR_BITS) parent[root(i)] = root(j)
            }
        }
        return hashes.indices.groupBy { root(it) }.values.filter { it.size > 1 }.mapIndexed { n, idx ->
            val files = idx.map { hashes[it].first }
            // Similar is not identical: nothing is picked for removal until you choose.
            DupGroup("s$n", files, true, files.map { it.path }.toSet())
        }
    }

    /** 9×8 grey picture → 64 bits (is each pixel brighter than its right neighbour?). Cached. */
    private fun dHash(e: FsEntry): Triple<Long, Int, Int>? {
        cache.rawQuery("SELECT hash, w, h FROM d WHERE path = ? AND size = ? AND mtime = ?", arrayOf(e.path, e.size.toString(), e.mtime.toString())).use { c ->
            if (c.moveToFirst()) {
                cache.execSQL("UPDATE d SET seen = ? WHERE path = ?", arrayOf<Any>(System.currentTimeMillis(), e.path))
                return Triple(c.getLong(0), c.getInt(1), c.getInt(2))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(e.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
        val bmp = BitmapFactory.decodeFile(e.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            fun grey(p: Int) = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114)
            if (grey(small.getPixel(x, y)) > grey(small.getPixel(x + 1, y))) hash = hash or (1L shl (y * 8 + x))
        }
        if (small !== bmp) small.recycle()
        bmp.recycle()
        cache.insertWithOnConflict("d", null, ContentValues().apply {
            put("path", e.path); put("size", e.size); put("mtime", e.mtime); put("hash", hash); put("w", bounds.outWidth); put("h", bounds.outHeight); put("seen", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return Triple(hash, bounds.outWidth, bounds.outHeight)
    }

    /** The chosen files to remove – never the last file of a group. */
    fun toRemove(groups: List<DupGroup>): List<DupFile> = groups.flatMap { g -> if (g.keep.none { k -> g.files.any { it.path == k } }) emptyList() else g.removable }

    suspend fun clean(files: List<DupFile>, onProgress: (Int, Int) -> Unit = { _, _ -> }): MoveResult {
        val r = Trash.move(files.map { Candidate(it.path, it.size, false) }, CleanTool.Duplicates, onProgress)
        forget(files.map { it.path } - r.failed.map { it.first }.toSet())
        return r
    }

    /** Fingerprints not used for 60 days (deleted or moved pictures) are dropped; at most 50 000 rows. */
    fun pruneCache() = runCatching {
        cache.delete("d", "seen < ?", arrayOf((System.currentTimeMillis() - 60 * 86_400_000L).toString()))
        cache.execSQL("DELETE FROM d WHERE path NOT IN (SELECT path FROM d ORDER BY seen DESC LIMIT 50000)")
    }

    /** v2: "seen" column for pruning – it is only a cache, so an upgrade simply starts empty. */
    private class CacheDb(context: Context) : SQLiteOpenHelper(context, "dupe_cache.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE d (path TEXT PRIMARY KEY, size INTEGER NOT NULL, mtime INTEGER NOT NULL, hash INTEGER NOT NULL, w INTEGER NOT NULL, h INTEGER NOT NULL, seen INTEGER NOT NULL DEFAULT 0)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS d")
            onCreate(db)
        }
    }
}
