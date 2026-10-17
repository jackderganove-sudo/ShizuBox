package de.emil.shizubox.cleaner

import android.os.Environment
import android.os.StatFs
import de.emil.shizubox.shizuku.ShizukuManager

data class StorageNumbers(val total: Long, val free: Long) {
    val used get() = total - free
}

/**
 * "All files access" lets ShizuBox itself read shared storage (hashes and thumbnails for the
 * duplicate finder, opening/sharing files). It grants it to itself through Shizuku – tested on
 * Android 17: `appops set de.emil.shizubox MANAGE_EXTERNAL_STORAGE allow` → isExternalStorageManager
 * is true at once, no restart. Android/data and Android/obb stay closed to the app; those go
 * through the shell. Private app data (/data/data) is out of reach without root.
 */
object StorageAccess {
    val granted get() = Environment.isExternalStorageManager()

    suspend fun ensure(): Boolean {
        if (granted) return true
        if (!ShizukuManager.isReady) return false
        runCatching { ShizukuManager.exec("appops set ${Guard.SELF} MANAGE_EXTERNAL_STORAGE allow", 5000, 15_000) }
        return granted
    }

    fun numbers(): StorageNumbers = runCatching {
        val s = StatFs(Environment.getDataDirectory().path)
        StorageNumbers(s.totalBytes, s.availableBytes)
    }.getOrDefault(StorageNumbers(0, 0))
}
