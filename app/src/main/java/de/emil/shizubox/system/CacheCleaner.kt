package de.emil.shizubox.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppCache(val pkg: String, val label: String, val bytes: Long)

/**
 * App caches. Sizes come from StorageStatsManager (needs usage access, which ShizuBox grants
 * itself like for screen time). Tested on Android 17 (Pixel):
 * - `pm trim-caches <size>` returns at once; with a target only a little above the free space it
 *   frees nothing, so "clean everything" asks for an unreachable 999G (the documented way).
 * - `pm clear --cache-only <pkg>` (also `cmd package clear --user 0 …`) hangs forever and deletes
 *   nothing – single apps therefore open their storage page in the settings instead.
 */
object CacheCleaner {
    /** Large caches that are really downloaded models etc. (re-downloaded after cleaning). */
    val heavy = setOf("com.google.android.aicore")

    /** Cache per app, largest first (apps without cache are left out). */
    suspend fun list(context: Context): List<AppCache> = withContext(Dispatchers.IO) {
        ScreenTime.ensureAccess(context)
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        Apps.list().filter { it.installed }.mapNotNull { a ->
            runCatching { ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, a.pkg, user).cacheBytes }.getOrNull()
                ?.takeIf { it > 0 }?.let { AppCache(a.pkg, a.label, it) }
        }.sortedByDescending { it.bytes }
    }

    fun cacheOf(context: Context, pkg: String): Long = runCatching {
        context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle()).cacheBytes
    }.getOrDefault(-1)

    private fun free() = StatFs("/data").availableBytes

    /**
     * ShizuBox's own cache: old update APKs, VirusTotal uploads, debug screenshots. Keeps what
     * may still be in use (update files of the last hour, the Activity Launcher index, yt-dlp).
     */
    suspend fun clearOwn(context: Context): Long = withContext(Dispatchers.IO) {
        val before = free()
        val hourAgo = System.currentTimeMillis() - 3600_000L
        context.cacheDir.listFiles().orEmpty().forEach { f ->
            when (f.name) {
                "activities.json", "ytdl" -> Unit
                "updates" -> f.listFiles().orEmpty().filter { it.lastModified() < hourAgo }.forEach { it.delete() }
                else -> f.deleteRecursively()
            }
        }
        context.externalCacheDir?.listFiles()?.forEach(File::deleteRecursively)
        (free() - before).coerceAtLeast(0)
    }

    /** Everything: own cache directly, all other apps through `pm trim-caches 999G`. Returns freed bytes. */
    suspend fun clearAll(context: Context): Long = withContext(Dispatchers.IO) {
        val before = free()
        clearOwn(context)
        if (ShizukuManager.isReady) ShizukuManager.exec("pm trim-caches 999G", 120_000)
        (free() - before).coerceAtLeast(0)
    }
}
