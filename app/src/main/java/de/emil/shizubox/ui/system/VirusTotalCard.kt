@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import de.emil.shizubox.R
import de.emil.shizubox.t
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun VirusTotalCard(key: Any, open: () -> InputStream?, asFile: suspend () -> File?, autoScan: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember(key) { mutableStateOf<VtResult?>(null) }
    var askKey by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableIntStateOf(if (autoScan) 0 else -1) }
    var showEngines by remember { mutableStateOf(false) }

    LaunchedEffect(key, keyVersion) {
        if (keyVersion < 0) return@LaunchedEffect // auto scan off: wait for the button
        if (VirusTotal.apiKey.isBlank()) {
            result = VtResult(VtStatus.NO_API_KEY)
            return@LaunchedEffect
        }
        result = VtResult(VtStatus.SCANNING)
        val sha = runCatching { withContext(Dispatchers.IO) { open()?.use { VirusTotal.sha256(it) } } }.getOrNull()
        result = if (sha.isNullOrBlank()) VtResult(VtStatus.ERROR, errorMessage = t(R.string.file_unreadable)) else VirusTotal.lookup(sha)
    }

    // A background upload for this file wins over the local state (it survives leaving the screen).
    val scans by VirusTotal.scans.collectAsStateWithLifecycle()
    GlassCard(Modifier.fillMaxWidth()) {
        Text("VirusTotal", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        val r = result?.let { scans[it.sha256].takeIf { _ -> it.sha256.isNotBlank() } ?: it }
        if (keyVersion < 0 && r == null) {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { keyVersion = 0 }) { Text(t(R.string.vt_scan)) }
            return@GlassCard
        }
        when (r?.status) {
            null, VtStatus.SCANNING -> Busy(t(R.string.vt_hashing))
            VtStatus.UPLOADING -> {
                Text(t(R.string.vt_uploading, r.uploadProgress), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { r.uploadProgress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            VtStatus.QUEUED -> Busy(t(R.string.vt_queued))
            VtStatus.ANALYZING -> Busy(t(R.string.vt_analyzing))
            VtStatus.NO_API_KEY -> {
                Text(
                    t(R.string.vt_need_key),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { askKey = true }, modifier = Modifier.padding(top = 8.dp)) { Text(t(R.string.vt_enter_key)) }
            }
            VtStatus.INVALID_API_KEY -> {
                Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, t(R.string.vt_bad_key))
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { askKey = true }) { Text(t(R.string.vt_change_key)) }
            }
            VtStatus.RATE_LIMITED -> {
                Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.tertiary, t(R.string.vt_limit))
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { keyVersion++ }) { Text(t(R.string.retry)) }
            }
            VtStatus.NOT_ANALYZED -> {
                Text(
                    t(R.string.vt_not_analyzed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { keyVersion++ }) { Text(t(R.string.recheck)) }
            }
            VtStatus.TOO_LARGE -> Text(t(R.string.vt_too_large), style = MaterialTheme.typography.bodyMedium)
            VtStatus.ERROR -> {
                Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, r.errorMessage.ifBlank { t(R.string.error) })
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { keyVersion++ }) { Text(t(R.string.retry)) }
            }
            VtStatus.NOT_FOUND -> {
                Text(t(R.string.vt_unknown_file), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = { VirusTotal.startUpload(r.sha256, asFile) },
                ) { Text(t(R.string.vt_upload)) }
            }
            VtStatus.CLEAN, VtStatus.SUSPICIOUS, VtStatus.MALICIOUS -> {
                when (r.status) {
                    VtStatus.CLEAN -> Verdict(Icons.Rounded.CheckCircle, Color(0xFF4CD964), t(R.string.vt_clean, r.total))
                    VtStatus.SUSPICIOUS -> Verdict(Icons.Rounded.GppMaybe, MaterialTheme.colorScheme.tertiary, t(R.string.vt_suspicious, r.suspicious, r.total))
                    else -> Verdict(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, t(R.string.vt_malicious, r.malicious, r.total))
                }
                val hits = r.engineResults.filter { it.category == "malicious" || it.category == "suspicious" }
                Row {
                    if (hits.isNotEmpty()) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { showEngines = !showEngines }) { Text(if (showEngines) t(R.string.vt_hide_hits) else t(R.string.vt_show_hits)) }
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/file/${r.sha256}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(t(R.string.vt_report)) }
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
            title = { Text(t(R.string.vt_key_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        t(R.string.vt_key_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/my-apikey"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(t(R.string.vt_open_site)) }
                    OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, placeholder = { Text(t(R.string.api_key_ph)) })
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    VirusTotal.apiKey = value
                    askKey = false
                    keyVersion++
                }) { Text(t(R.string.save)) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { askKey = false }) { Text(t(R.string.cancel)) } },
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
