package de.emil.shizubox.ui.system

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.emil.shizubox.installer.VirusTotal
import de.emil.shizubox.installer.VtResult
import de.emil.shizubox.installer.VtStatus
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * VirusTotal check for a file. [open] streams the file for hashing, [asFile] provides a real file
 * for uploading (only called if VirusTotal does not know the file yet and the user asks for it).
 */
@Composable
fun VirusTotalCard(key: Any, open: () -> InputStream?, asFile: suspend () -> File?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember(key) { mutableStateOf<VtResult?>(null) }
    var askKey by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableIntStateOf(0) }
    var showEngines by remember { mutableStateOf(false) }

    LaunchedEffect(key, keyVersion) {
        if (VirusTotal.apiKey.isBlank()) {
            result = VtResult(VtStatus.NO_API_KEY)
            return@LaunchedEffect
        }
        result = VtResult(VtStatus.SCANNING)
        val sha = runCatching { withContext(Dispatchers.IO) { open()?.use { VirusTotal.sha256(it) } } }.getOrNull()
        result = if (sha.isNullOrBlank()) VtResult(VtStatus.ERROR, errorMessage = "Datei nicht lesbar") else VirusTotal.lookup(sha)
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Text("VirusTotal", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        val r = result
        when (r?.status) {
            null, VtStatus.SCANNING -> Busy("Prüfe Prüfsumme …")
            VtStatus.UPLOADING -> {
                Text("Lade hoch … ${r.uploadProgress} %", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { r.uploadProgress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            VtStatus.QUEUED -> Busy("In der Warteschlange …")
            VtStatus.ANALYZING -> Busy("Wird von den Scannern geprüft …")
            VtStatus.NO_API_KEY -> {
                Text(
                    "Für den Scan brauchst du einen kostenlosen VirusTotal-API-Schlüssel.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = { askKey = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Schlüssel eingeben") }
            }
            VtStatus.INVALID_API_KEY -> {
                Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "API-Schlüssel ungültig")
                TextButton(onClick = { askKey = true }) { Text("Schlüssel ändern") }
            }
            VtStatus.RATE_LIMITED -> {
                Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.tertiary, "Limit erreicht (4 Anfragen pro Minute)")
                TextButton(onClick = { keyVersion++ }) { Text("Erneut versuchen") }
            }
            VtStatus.TOO_LARGE -> Text("Datei zu groß für VirusTotal (max. 650 MB)", style = MaterialTheme.typography.bodyMedium)
            VtStatus.ERROR -> {
                Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, r.errorMessage.ifBlank { "Fehler" })
                TextButton(onClick = { keyVersion++ }) { Text("Erneut versuchen") }
            }
            VtStatus.NOT_FOUND -> {
                Text("Diese Datei kennt VirusTotal noch nicht.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        scope.launch {
                            val sha = r.sha256
                            result = VtResult(VtStatus.UPLOADING, sha256 = sha)
                            val file = runCatching { asFile() }.getOrNull()
                            result = if (file == null) {
                                VtResult(VtStatus.ERROR, errorMessage = "Datei nicht lesbar")
                            } else {
                                VirusTotal.uploadAndScan(file, sha) { result = it }
                            }
                        }
                    },
                ) { Text("Hochladen & scannen") }
            }
            VtStatus.CLEAN, VtStatus.SUSPICIOUS, VtStatus.MALICIOUS -> {
                when (r.status) {
                    VtStatus.CLEAN -> Verdict(Icons.Rounded.CheckCircle, Color(0xFF4CD964), "Sauber · 0 von ${r.total} Scannern")
                    VtStatus.SUSPICIOUS -> Verdict(Icons.Rounded.GppMaybe, MaterialTheme.colorScheme.tertiary, "Verdächtig · ${r.suspicious} von ${r.total} Scannern")
                    else -> Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, "Schädlich · ${r.malicious} von ${r.total} Scannern")
                }
                val hits = r.engineResults.filter { it.category == "malicious" || it.category == "suspicious" }
                Row {
                    if (hits.isNotEmpty()) {
                        TextButton(onClick = { showEngines = !showEngines }) { Text(if (showEngines) "Treffer ausblenden" else "Treffer anzeigen") }
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/file/${r.sha256}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Bericht öffnen") }
                }
                if (showEngines) {
                    hits.forEach {
                        Text("${it.engineName}: ${it.result ?: it.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (askKey) {
        var value by remember { mutableStateOf(VirusTotal.apiKey) }
        AlertDialog(
            onDismissRequest = { askKey = false },
            title = { Text("VirusTotal-API-Schlüssel") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Kostenlos: auf virustotal.com anmelden, dann Profil → API Key. Der Schlüssel bleibt nur auf diesem Handy.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/my-apikey"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("virustotal.com öffnen") }
                    OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, placeholder = { Text("API-Schlüssel") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    VirusTotal.apiKey = value
                    askKey = false
                    keyVersion++
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { askKey = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.padding(end = 12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Verdict(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
