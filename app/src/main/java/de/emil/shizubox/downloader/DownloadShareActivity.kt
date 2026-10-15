package de.emil.shizubox.downloader

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.emil.shizubox.R
import de.emil.shizubox.t
import de.emil.shizubox.ui.downloader.DownloadSheet
import de.emil.shizubox.ui.downloader.SheetTarget
import de.emil.shizubox.ui.theme.ShizuBoxTheme

/**
 * "Download with ShizuBox" in the share menu: shows the download sheet over the sending app
 * (YouTube, browser, …) and puts the link into the queue.
 */
class DownloadShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: intent.dataString
        val url = text?.let { Regex("""https?://\S+""").find(it)?.value }
        if (url == null) {
            Toast.makeText(this, t(R.string.dl_no_link), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ShizuBoxTheme {
                DownloadSheet(
                    SheetTarget.Url(url),
                    onDismiss = { finish() },
                    onQueued = {
                        Toast.makeText(this, t(R.string.dl_queued), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }
}
