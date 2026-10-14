package de.emil.shizubox.ui.updates

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.updates.Source
import de.emil.shizubox.updates.TrackedApp
import de.emil.shizubox.updates.UpdateState
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDetailScreen(app: TrackedApp, shizukuReady: Boolean, resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(app, resumeTick) { Updates.state(app) }
    val realVersion = remember(app, resumeTick) { Updates.installedVersionName(app.id) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var filter by remember(app.key) { mutableStateOf(app.apkFilterRegex) }
    var pkg by remember(app.key) { mutableStateOf(app.id) }
    var showLog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 16.dp)) {
                if (realVersion != null) {
                    AppIcon(app.id, 64)
                    Spacer(Modifier.padding(8.dp))
                }
                Column {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "von ${app.author} · ${Source.forUrl(app.url)?.label ?: "nicht unterstützte Quelle"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    when (state) {
                        UpdateState.Update -> "Update verfügbar"
                        UpdateState.UpToDate -> "Aktuell"
                        UpdateState.NotInstalled -> "Nicht installiert"
                        UpdateState.Unknown -> "Version nicht vergleichbar"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Neueste: ${app.latestVersion ?: "?"}" + if (app.releaseDate > 0) " (${SimpleDateFormat("d. MMM yyyy", Locale.GERMAN).format(Date(app.releaseDate))})" else "", style = MaterialTheme.typography.bodyMedium)
                Text("Installiert: ${realVersion ?: "–"}", style = MaterialTheme.typography.bodyMedium)
                app.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                val trackOnly = Source.forUrl(app.url)?.trackOnly == true
                if (trackOnly) {
                    Text(
                        "APKMirror erlaubt keine automatischen Downloads: Auf der Seite die passende Variante laden und mit ShizuBox öffnen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trackOnly) {
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.releaseUrl ?: app.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Auf APKMirror laden") }
                    } else Button(
                        enabled = progress == null && app.chosenApk != null,
                        onClick = {
                            progress = 0f
                            scope.launch {
                                runCatching { Updates.download(app) { progress = it } }
                                    .onSuccess { (_, uri) -> openInstaller(context, uri) }
                                    .onFailure { Updates.update(app.copy(error = it.message)) }
                                progress = null
                            }
                        },
                    ) { Text(if (state == UpdateState.NotInstalled) "Installieren" else if (state == UpdateState.Update) "Aktualisieren" else "Neu installieren") }
                    OutlinedButton(enabled = !checking, onClick = {
                        checking = true
                        scope.launch { Updates.check(app); checking = false }
                    }) { Text(if (checking) "Prüfe …" else "Prüfen") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.releaseUrl ?: app.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Quelle öffnen") }
                    if (state == UpdateState.Update || state == UpdateState.Unknown) {
                        OutlinedButton(onClick = { Updates.markInstalled(app) }) { Text("Als aktuell markieren") }
                    }
                }
            }
        }
        if (app.apks.size > 1) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("APK-Auswahl", style = MaterialTheme.typography.titleMedium)
                    app.apks.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = app.chosenApk == a, onClick = { Updates.update(app.copy(preferredApk = i)) })
                            Text(a.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        app.changeLog?.let { log ->
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = { showLog = !showLog }) {
                    Text("Änderungen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        log.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (showLog) Int.MAX_VALUE else 6,
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Einstellungen", style = MaterialTheme.typography.titleMedium)
                Toggle("Vorabversionen einbeziehen", app.includePrereleases) {
                    scope.launch { Updates.check(app.copy(includePrereleases = it)) }
                }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("APK-Filter (Regex)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Toggle("Filter umkehren", app.invertApkFilter) {
                    scope.launch { Updates.check(app.copy(invertApkFilter = it, apkFilterRegex = filter)) }
                }
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim() },
                    label = { Text("Paketname der installierten App") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { scope.launch { Updates.check(app.copy(apkFilterRegex = filter, id = pkg)) } }) { Text("Übernehmen") }
                    TextButton(onClick = { confirmRemove = true }) { Text("Entfernen") }
                }
            }
        }
        item { Text(app.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Nicht mehr verfolgen?") },
            text = { Text("Die App bleibt installiert, ShizuBox prüft nur keine Updates mehr.") },
            confirmButton = { TextButton(onClick = { Updates.remove(app); onBack() }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Abbrechen") } },
        )
    }
}
