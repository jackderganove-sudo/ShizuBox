package de.emil.shizubox.ui.updates

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app browser for APKMirror: the user browses and taps "Download" as usual (a real browser
 * engine, no automation). The finished file is caught and handed to ShizuBox's installer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(startUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var download by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Schließen") }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        download?.let {
            Text("Lade herunter …", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            title = view.title.orEmpty()
                        }
                    }
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        download = 0f
                        error = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    // Same session as the page the user is on (cookies + user agent of this WebView).
                                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                                        setRequestProperty("User-Agent", userAgent)
                                        CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
                                    }
                                    val total = conn.contentLengthLong
                                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                                    val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                                    conn.inputStream.use { input ->
                                        file.outputStream().use { out ->
                                            val buf = ByteArray(64 * 1024)
                                            var done = 0L
                                            while (true) {
                                                val n = input.read(buf)
                                                if (n < 0) break
                                                out.write(buf, 0, n)
                                                done += n
                                                if (total > 0) download = done.toFloat() / total
                                            }
                                        }
                                    }
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                }
                            }.onSuccess { uri ->
                                download = null
                                openInstaller(context, uri)
                                onClose()
                            }.onFailure {
                                download = null
                                error = "Download fehlgeschlagen: ${it.message}"
                            }
                        }
                    }
                    loadUrl(startUrl)
                    webView = this
                }
            },
        )
    }
}
