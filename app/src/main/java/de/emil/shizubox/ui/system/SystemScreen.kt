package de.emil.shizubox.ui.system

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.system.ApkInstaller
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.home.ShellConsole
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemScreen(
    shizukuReady: Boolean,
    onOpenLogcat: () -> Unit,
    onOpenUniversalInstaller: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text("System", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Gaming, Installieren, Logs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!shizukuReady) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text("Shizuku muss verbunden sein.") } }
        }
        item { GamingCard(shizukuReady) }
        item { InstallerCard(shizukuReady, onOpenUniversalInstaller) }
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = onOpenLogcat) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Logcat", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Systemprotokoll ansehen, nach App und Stufe filtern",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        item { ShellConsole() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamingCard(shizukuReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by Gaming.active.collectAsStateWithLifecycle()
    val o by Gaming.options.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val games = remember { Gaming.games() }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gaming-Modus", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (active) "Aktiv – wird beim Ausschalten exakt zurückgesetzt" else "Weniger Störungen, maximale Flüssigkeit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.padding(4.dp))
            } else {
                Switch(
                    checked = active,
                    enabled = shizukuReady,
                    onCheckedChange = { on ->
                        busy = true
                        scope.launch {
                            runCatching { if (on) Gaming.enable() else Gaming.disable() }
                            busy = false
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple("Benachrichtigungen blockieren (Bitte nicht stören)", o.blockNotifications) { v: Boolean -> o.copy(blockNotifications = v) },
            Triple("Keine Pop-up-Benachrichtigungen", o.noHeadsUp) { v: Boolean -> o.copy(noHeadsUp = v) },
            Triple("Immer maximale Bildwiederholrate", o.maxRefresh) { v: Boolean -> o.copy(maxRefresh = v) },
            Triple("Automatische Helligkeit aus", o.fixedBrightness) { v: Boolean -> o.copy(fixedBrightness = v) },
            Triple("Hintergrund-Apps beim Start beenden", o.killBackground) { v: Boolean -> o.copy(killBackground = v) },
        ).forEach { (label, checked, update) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = checked, enabled = !active, onCheckedChange = { Gaming.setOptions(update(it)) })
            }
        }
        if (games.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Spiele starten", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                games.forEach { g ->
                    AssistChip(
                        onClick = {
                            scope.launch {
                                if (!active && shizukuReady) runCatching { Gaming.enable() }
                                Gaming.launchIntent(g.pkg)?.let(context::startActivity)
                            }
                        },
                        label = { Text(g.label) },
                        leadingIcon = { AppIcon(g.pkg, 20) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallerCard(shizukuReady: Boolean, onOpenUniversalInstaller: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var universal by remember { mutableStateOf(true) }
    val universalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(onOpenUniversalInstaller)
    }
    var downgrade by remember { mutableStateOf(false) }
    var grantAll by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ApkInstaller.Result?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        result = null
        scope.launch {
            result = ApkInstaller.install(context, uris, downgrade, grantAll) { progress = it }
            progress = null
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("APK-Installer", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = universal, onClick = { universal = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Universal") }
            SegmentedButton(selected = !universal, onClick = { universal = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Schnell") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (universal) {
                "Universal Installer: zeigt App-Infos, Version und Signatur vorab, wählt bei APKS/XAPK/APKM nur die passenden Teile. Auch über „Öffnen mit ShizuBox“ im Dateimanager."
            } else {
                "Installiert sofort ohne Vorschau. Auch mehrere Split-APKs auf einmal."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (universal) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { universalPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Datei auswählen") }
            Text(
                "Basiert auf Universal Installer von pass-with-high-score (GPL-3.0)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downgrade erlauben", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = downgrade, onCheckedChange = { downgrade = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alle Berechtigungen gewähren", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = grantAll, onCheckedChange = { grantAll = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = shizukuReady && progress == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("APK auswählen") }
        progress?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text(it)
            }
        }
        result?.let {
            Text(
                if (it.ok) "Installiert ✓" else "Fehler: ${it.message}",
                color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
