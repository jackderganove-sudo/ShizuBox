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
        val url = text?.let(::sharedUrl)
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

/**
 * First link in a shared text. Punctuation that ends a sentence or closes a bracket around the
 * link ("(see https://x.y/a)") is not part of it (0.11.1); a closing bracket the link itself
 * opened (Wikipedia "Foo_(bar)") stays.
 */
internal fun sharedUrl(text: String): String? {
    var url = Regex("""https?://\S+""").find(text)?.value ?: return null
    while (url.isNotEmpty()) {
        val last = url.last()
        val strip = when (last) {
            '.', ',', ';', ':', '!', '?', '"', '\'', '>', '»', '“', '”' -> true
            ')' -> url.count { it == '(' } < url.count { it == ')' }
            ']' -> url.count { it == '[' } < url.count { it == ']' }
            '}' -> url.count { it == '{' } < url.count { it == '}' }
            else -> false
        }
        if (!strip) break
        url = url.dropLast(1)
    }
    return url.takeIf { it.length > "https://".length }
}
