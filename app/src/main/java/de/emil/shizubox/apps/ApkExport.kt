package de.emil.shizubox.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports installed apps as installable files: a single .apk, or an .apks bundle (zip of base +
 * splits) for split apps, which the Universal Installer can install again.
 */
object ApkExport {

    fun fileName(app: AppEntry) =
        "${app.label.replace(Regex("[^A-Za-z0-9._ -]"), "_")}_${app.versionName ?: "app"}.${if (app.isSplit) "apks" else "apk"}"

    suspend fun writeTo(app: AppEntry, out: OutputStream) = withContext(Dispatchers.IO) {
        val paths = Apps.apkPaths(app.pkg)
        if (paths.size == 1) {
            File(paths[0]).inputStream().use { it.copyTo(out) }
        } else {
            ZipOutputStream(out).use { zip ->
                paths.forEach { path ->
                    val f = File(path)
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Extract into a user-chosen document (from ACTION_CREATE_DOCUMENT). */
    suspend fun extract(context: Context, app: AppEntry, target: Uri) {
        context.contentResolver.openOutputStream(target)?.use { writeTo(app, it) }
    }

    /** Share sheet with the app's APK/APKS. */
    suspend fun share(context: Context, app: AppEntry) {
        val dir = File(context.cacheDir, "shared_apks").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, fileName(app))
        file.outputStream().use { writeTo(app, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(send, app.label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Store page in the app that installed it (Play Store, F-Droid, …), else any store. */
    fun storeIntent(app: AppEntry): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.installer?.takeIf { it != "com.android.shell" && it != "de.emil.shizubox" }?.let(intent::setPackage)
        return intent
    }

    fun storeLabel(app: AppEntry) = when (app.installer) {
        "com.android.vending" -> "Im Play Store öffnen"
        "org.fdroid.fdroid", "com.looker.droidify", "org.fdroid.basic" -> "In F-Droid öffnen"
        else -> "Im Store öffnen"
    }
}
