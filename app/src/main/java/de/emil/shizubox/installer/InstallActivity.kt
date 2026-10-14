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
        setContent {
            ShizuBoxTheme {
                val status by ShizukuManager.status.collectAsStateWithLifecycle()
                GlassBackground(windowBlurActive = false) {
                    UniversalInstallerScreen(uri, status == ShizukuStatus.Ready, onClose = ::finish)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }
}
