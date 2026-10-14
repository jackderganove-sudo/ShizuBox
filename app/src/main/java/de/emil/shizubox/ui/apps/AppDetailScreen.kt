package de.emil.shizubox.ui.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.apps.ApkExport
import de.emil.shizubox.apps.AppDetails
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.BatteryMode
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.apps.bucketLabel
import de.emil.shizubox.shizuku.ShellResult
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailScreen(pkg: String, shizukuReady: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var details by remember { mutableStateOf<AppDetails?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var showAllOps by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val profiles by Profiles.profiles.collectAsStateWithLifecycle()
    val extractLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { target ->
        val d = details ?: return@rememberLauncherForActivityResult
        if (target != null) {
            scope.launch {
                message = runCatching { ApkExport.extract(context, d.entry, target) }
                    .fold({ "Gespeichert ✓" }, { "Extrahieren fehlgeschlagen: ${it.message}" })
            }
        }
    }

    LaunchedEffect(pkg, reload, shizukuReady) {
        details = runCatching { Apps.details(pkg) }.getOrNull()
    }

    fun act(block: suspend () -> ShellResult) {
        scope.launch {
            val r = runCatching { block() }.getOrNull()
            if (r != null && !r.ok) message = (r.err.ifBlank { r.out }).take(300)
            reload++
        }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
            }
        }
        val d = details
        if (d == null) {
            item { Text("Lade …", modifier = Modifier.padding(8.dp)) }
            return@LazyColumn
        }
        val e = d.entry
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)) {
                AppIcon(pkg, 64)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.label, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "$pkg${e.versionName?.let { " · $it" } ?: ""}${if (e.isSystem) " · System" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    when {
                        !e.installed -> "Für diesen Nutzer entfernt"
                        e.frozen -> "Eingefroren – die App ist deaktiviert und unsichtbar"
                        else -> "Aktiv"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!e.installed) {
                        FilledTonalButton(enabled = shizukuReady, onClick = { act { Apps.reinstall(pkg) } }) { Text("Wiederherstellen") }
                    } else {
                        FilledTonalButton(
                            enabled = shizukuReady && pkg != context.packageName,
                            onClick = { act { if (e.frozen) Apps.unfreeze(listOf(pkg)) else Apps.freeze(listOf(pkg)) } },
                        ) { Text(if (e.frozen) "Auftauen" else "Einfrieren") }
                        OutlinedButton(enabled = shizukuReady, onClick = { act { Apps.forceStop(pkg) } }) { Text("Beenden") }
                        if (!e.isSystem && pkg != context.packageName) {
                            OutlinedButton(enabled = shizukuReady, onClick = { confirmUninstall = true }) { Text("Deinstallieren") }
                        }
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("App-Info") }
                }
                if (e.installed && !e.frozen) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Apps.launchIntent(pkg)?.let { launch ->
                            OutlinedButton(onClick = { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Öffnen") }
                        }
                        if (!e.isSystem) {
                            OutlinedButton(onClick = {
                                // Fall back to any store if the installing app can't show it.
                                runCatching { context.startActivity(ApkExport.storeIntent(e)) }
                                    .onFailure { runCatching { context.startActivity(ApkExport.storeIntent(e).setPackage(null)) } }
                            }) { Text(ApkExport.storeLabel(e)) }
                        }
                        OutlinedButton(onClick = { scope.launch { runCatching { ApkExport.share(context, e) }.onFailure { message = it.message } } }) {
                            Text("APK teilen")
                        }
                        OutlinedButton(onClick = { extractLauncher.launch(ApkExport.fileName(e)) }) { Text("Extrahieren") }
                        if (!e.isSystem) {
                            OutlinedButton(enabled = shizukuReady, onClick = { act { Apps.reinstallFromApks(pkg) } }) { Text("Neu installieren") }
                        }
                    }
                }
                if (e.isSystem) {
                    Text(
                        "System-Apps lassen sich unter Android 17 ohne Root nur einfrieren, nicht entfernen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                message?.let {
                    val ok = it.endsWith("✓")
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (profiles.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("In Profilen", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.forEach { p ->
                            FilterChip(selected = pkg in p.packages, onClick = { Profiles.toggle(p.id, pkg) }, label = { Text(p.name) })
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Akku", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${d.battery.hint} · Standby-Gruppe: ${bucketLabel(d.standbyBucket)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BatteryMode.entries.forEach { m ->
                        FilterChip(
                            selected = d.battery == m,
                            enabled = shizukuReady,
                            onClick = { if (d.battery != m) act { Apps.setBattery(pkg, m) } },
                            label = { Text(m.label) },
                        )
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Berechtigungen", style = MaterialTheme.typography.titleMedium)
                if (d.permissions.isEmpty()) {
                    Text("Keine änderbaren Berechtigungen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                d.permissions.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.bodyLarge)
                            Text(p.name.substringAfterLast('.'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = p.granted,
                            enabled = shizukuReady,
                            onCheckedChange = { g -> act { Apps.setPermission(pkg, p.name, g) } },
                        )
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AppOps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAllOps = !showAllOps }) { Text(if (showAllOps) "Weniger" else "Alle ${d.appOps.size}") }
                }
                Text(
                    "Feinere Rechte unterhalb der Berechtigungen. allow = erlaubt, ignore = still verweigert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val ops = if (showAllOps) d.appOps else d.appOps.take(8)
        items(ops, key = { it.op }) { op ->
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Text(op.op, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Apps.APP_OP_MODES.forEach { mode ->
                        FilterChip(
                            selected = op.mode == mode,
                            enabled = shizukuReady,
                            onClick = { if (op.mode != mode) act { Apps.setAppOp(pkg, op.op, mode) } },
                            label = { Text(mode) },
                        )
                    }
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Deinstallieren?") },
            text = { Text("${details?.entry?.label} wird mit allen Daten entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    scope.launch {
                        val r = Apps.uninstall(pkg)
                        if (r.ok) onBack() else message = r.err.ifBlank { r.out }
                    }
                }) { Text("Deinstallieren") }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("Abbrechen") } },
        )
    }
}
