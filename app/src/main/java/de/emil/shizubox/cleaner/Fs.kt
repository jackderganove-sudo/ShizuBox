package de.emil.shizubox.cleaner

import de.emil.shizubox.shizuku.ShizukuManager

/** One file or folder from a `find` listing. [size] of a folder is its own inode size, not its content. */
data class FsEntry(val path: String, val size: Long, val mtime: Long, val dir: Boolean) {
    val name get() = path.substringAfterLast('/')
    val parent get() = path.substringBeforeLast('/')
}

/**
 * File access for the cleaner through the Shizuku shell user: it may read and write
 * `Android/data` and `Android/obb` (groups ext_data_rw/ext_obb_rw), which normal apps may not since
 * Android 11. Tested on Android 17 (Pixel):
 * - toybox `find … -printf '%s|%T@|%M|%p\0'` works (no `%y`; `%M` gives the type as first letter);
 *   NUL-terminated records survive every file name. The whole internal storage (5300 entries)
 *   takes 0.8 s – one streamed command instead of one per file.
 * - `/sdcard` is a symlink: `find /sdcard` lists nothing, so always the real path [ROOT].
 * - `mv` across the Android/data boundary falls back to copy + setattr, and that crashed
 *   MediaProvider's FUSE daemon ("Check failed: active_nodes_…" in pf_setattr) – the whole shared
 *   storage was gone for ~4 s. Moves therefore only use [ShizukuManager.rename] (pure rename(2)).
 * - `du -sk a b c` sizes many folders in one call.
 */
object Fs {
    const val ROOT = "/storage/emulated/0"
    const val DATA = "$ROOT/Android/data"
    const val OBB = "$ROOT/Android/obb"
    const val MEDIA = "$ROOT/Android/media"

    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Relative path for display ("Download/x.apk"). */
    fun rel(path: String) = path.removePrefix(ROOT).removePrefix("/").ifEmpty { "/" }

    fun parse(rec: String): FsEntry? {
        val p = rec.split('|', limit = 4)
        if (p.size < 4 || p[3].isEmpty()) return null
        val size = p[0].toLongOrNull() ?: return null
        val mtime = ((p[1].toDoubleOrNull() ?: 0.0) * 1000).toLong()
        return FsEntry(p[3], size, mtime, p[2].startsWith('d'))
    }

    /**
     * Streams `find <roots> <expr>` as [FsEntry]s. [expr] is raw find syntax (already quoted),
     * e.g. `-maxdepth 1` or a prune clause.
     */
    suspend fun <T> find(roots: List<String>, expr: String = "", limitMs: Long = 10 * 60_000L, block: (Sequence<FsEntry>) -> T): T {
        val cmd = "find ${roots.joinToString(" ") { q(it) }} $expr -printf '%s|%T@|%M|%p\\0'"
        return ShizukuManager.execRecords(cmd, limitMs) { recs -> block(recs.mapNotNull(::parse)) }
    }

    suspend fun list(dir: String): List<FsEntry> = find(listOf(dir), "-mindepth 1 -maxdepth 1") { it.toList() }

    /** Sizes in bytes of whole folders/files (du, 1 KB blocks), in one call per 200 paths. */
    suspend fun sizes(paths: List<String>): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (chunk in paths.chunked(200)) {
            ShizukuManager.execRecords("du -sk ${chunk.joinToString(" ") { q(it) }}", 5 * 60_000L, '\n') { lines ->
                lines.forEach { l ->
                    val tab = l.indexOf('\t')
                    if (tab > 0) l.substring(0, tab).toLongOrNull()?.let { out[l.substring(tab + 1)] = it * 1024 }
                }
            }
        }
        return out
    }

    /** Deletes for good (only used by the trash and for ShizuBox's own files). */
    suspend fun remove(paths: List<String>): Boolean {
        var ok = true
        for (chunk in paths.chunked(100)) {
            val r = ShizukuManager.exec("rm -rf ${chunk.joinToString(" ") { q(it) }}", 15_000, 10 * 60_000L)
            ok = ok && r.code == 0
        }
        return ok
    }
}
