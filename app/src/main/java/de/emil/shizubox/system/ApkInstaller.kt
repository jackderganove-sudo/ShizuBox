package de.emil.shizubox.system

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs APKs through Shizuku (`pm install`), without the system installer dialog.
 * Accepts single .apk files, several split APKs at once, and .apks/.xapk bundles (zip archives
 * containing the split APKs). Files are copied to /data/local/tmp/shizubox first, because the
 * shell user cannot read files from app storage.
 */
object ApkInstaller {
    private const val TMP = "/data/local/tmp/shizubox"

    data class Result(val ok: Boolean, val message: String)

    suspend fun install(
        context: Context,
        uris: List<Uri>,
        allowDowngrade: Boolean,
        grantAll: Boolean,
        onProgress: (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        try {
            ShizukuManager.exec("rm -rf $TMP; mkdir -p $TMP")
            for (uri in uris) {
                val name = displayName(context, uri) ?: "file.apk"
                if (name.endsWith(".apks", true) || name.endsWith(".xapk", true) || name.endsWith(".zip", true)) {
                    onProgress(t(R.string.unpacking, name))
                    paths += extractBundle(context, uri)
                } else {
                    onProgress(t(R.string.copying, name))
                    val target = "$TMP/${paths.size}.apk"
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: return@withContext Result(false, t(R.string.file_unreadable_n, name))
                    val written = pfd.use { ShizukuManager.writeFile(it, target) }
                    if (written < 0) return@withContext Result(false, t(R.string.copy_failed))
                    paths += target
                }
            }
            if (paths.isEmpty()) return@withContext Result(false, t(R.string.no_apk_found))
            onProgress(t(R.string.installing))
            val flags = buildString {
                append("-r")
                if (allowDowngrade) append(" -d")
                if (grantAll) append(" -g")
            }
            val r = ShizukuManager.exec("pm install $flags ${paths.joinToString(" ")}", timeoutMs = 10_000, limitMs = de.emil.shizubox.apps.Apps.INSTALL_LIMIT)
            val text = (r.out + "\n" + r.err).trim()
            Result(r.ok && "Success" in text, text.lines().lastOrNull { it.isNotBlank() } ?: "exit ${r.code}")
        } catch (e: Exception) {
            Result(false, e.message ?: e.toString())
        } finally {
            runCatching { ShizukuManager.exec("rm -rf $TMP") }
        }
    }

    /** Writes every .apk inside the zip to the temp dir via a pipe. */
    private suspend fun extractBundle(context: Context, uri: Uri): List<String> {
        val out = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", true)) {
                        // Buffer the entry to a cache file, then hand its fd to the shell service.
                        val tmp = File.createTempFile("split", ".apk", context.cacheDir)
                        val target = "$TMP/b${out.size}.apk"
                        try {
                            tmp.outputStream().use { zip.copyTo(it) }
                            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                                ShizukuManager.writeFile(pfd, target)
                            }
                        } finally {
                            tmp.delete()
                        }
                        out += target
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return out
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
