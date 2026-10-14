package de.emil.shizubox.installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.ui.glass.GlassBackground
import de.emil.shizubox.ui.system.UniversalInstallerScreen
import de.emil.shizubox.ui.theme.ShizuBoxTheme

/** Opened from file managers / browsers ("Öffnen mit ShizuBox") for APK, APKS, XAPK, APKM. */
class InstallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> intent.data
        }
        if (uri == null) {
            finish()
            return
        }
        if (!isPackageFile(uri)) {
            forwardToOtherApps(uri)
            return
        }
        setContent {
            ShizuBoxTheme {
                val status by ShizukuManager.status.collectAsStateWithLifecycle()
                GlassBackground(windowBlurActive = false) {
                    UniversalInstallerScreen(uri, status == ShizukuStatus.Ready, onClose = ::finish)
                }
            }
        }
    }

    /**
     * Generic files (octet-stream) also reach us. Accept only APKs and bundles: known extension,
     * or a zip that contains an AndroidManifest.xml (APK) or .apk entries (bundle).
     */
    private fun isPackageFile(uri: Uri): Boolean {
        val name = ApkParser.fileName(this, uri).lowercase()
        if (name.substringAfterLast('.', "") in setOf("apk", "apks", "xapk", "apkm")) return true
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                java.util.zip.ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }.take(200).any {
                        it.name == "AndroidManifest.xml" || it.name.endsWith(".apk", ignoreCase = true)
                    }
                }
            } ?: false
        }.getOrDefault(false)
    }

    /** Not an app package: hand the file to the normal app chooser, without ShizuBox. */
    private fun forwardToOtherApps(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, intent.type ?: contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(this, InstallActivity::class.java)))
        runCatching { startActivity(chooser) }
        finish()
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }
}
