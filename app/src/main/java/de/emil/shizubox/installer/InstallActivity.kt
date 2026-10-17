package de.emil.shizubox.installer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.ui.glass.GlassBackground
import de.emil.shizubox.ui.system.UniversalInstallerScreen
import de.emil.shizubox.ui.theme.ShizuBoxTheme

/** Opened from file managers / browsers ("Öffnen mit ShizuBox") for APK, APKS, XAPK, APKM. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        // Reading a shared zip can take long (generic files, several hundred MB): never on the main
        // thread (Daniel's review, 0.11.1) – a spinner until it is clear what the file is.
        var isPackage by mutableStateOf<Boolean?>(null)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { isPackageFile(uri) }
            if (ok) isPackage = true else forwardToOtherApps(uri)
        }
        setContent {
            ShizuBoxTheme {
                val status by ShizukuManager.status.collectAsStateWithLifecycle()
                GlassBackground(windowBlurActive = false) {
                    if (isPackage == true) {
                        UniversalInstallerScreen(uri, status == ShizukuStatus.Ready, onClose = ::finish)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
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
