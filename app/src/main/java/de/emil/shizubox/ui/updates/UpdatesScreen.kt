package de.emil.shizubox.ui.updates

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.installer.InstallActivity
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

private val shortDate = SimpleDateFormat("dd.MM.", Locale.GERMAN)

/** Hands a downloaded APK to ShizuBox's own installer screen (preview, VirusTotal, Shizuku install). */
fun openInstaller(context: Context, uri: Uri) {
    context.startActivity(
        Intent(context, InstallActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

@Composable
fun UpdatesScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        Updates.refreshInstalledStates()
        resumeTick++
        onPauseOrDispose { }
    }

    selected?.let { key ->
        BackHandler { selected = null }
        val app = Updates.apps.collectAsStateWithLifecycle().value.firstOrNull { it.key == key }
        if (app == null) {
            selected = null
        } else {
            UpdateDetailScreen(app, shizukuReady, resumeTick, onBack = { selected = null })
            return
        }
    }
    if (importing) {
        BackHandler { importing = false }
        ImportInstalledScreen(onDone = { importing = false; tab = 0 })
        return
    }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> AppsTab(resumeTick, onBack = onBack, onOpen = { selected = it.key })
            1 -> AddTab(onAdded = { tab = 0 }, onImportInstalled = { importing = true })
            else -> SettingsTab()
        }
        // Bottom navigation, reachable with the thumb (like ObtainX).
        SingleChoiceSegmentedButtonRow(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            listOf("Apps", "Hinzufügen", "Einstellungen").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AppsTab(resumeTick: Int, onBack: () -> Unit, onOpen: (TrackedApp) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by Updates.apps.collectAsStateWithLifecycle()
    val checking by Updates.checking.collectAsStateWithLifecycle()
    val states = remember(apps, resumeTick) { apps.associate { it.key to Updates.state(it) } }
    val updates = apps.filter { states[it.key] == UpdateState.Update }.sortedBy { it.name.lowercase() }
    val others = apps.filter { states[it.key] != UpdateState.Update }.sortedBy { it.name.lowercase() }
    var busy by remember { mutableStateOf<String?>(null) }

    // Notification permission for update notices (asked once, when this screen is used).
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun install(app: TrackedApp) {
        if (Source.forUrl(app.url)?.trackOnly == true) {
            // APKMirror: download by hand on the release page, then open it with ShizuBox.
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.releaseUrl ?: app.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        busy = app.key
        scope.launch {
            runCatching { Updates.download(app) {} }
                .onSuccess { (_, uri) -> openInstaller(context, uri) }
                .onFailure { Updates.update(app.copy(error = it.message)) }
            busy = null
        }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Updates", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${apps.size} Apps verfolgt",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checking) {
                    CircularProgressIndicator(Modifier.size(32.dp).padding(4.dp))
                } else {
                    FilledTonalIconButton(onClick = { scope.launch { Updates.checkAll() } }) { Icon(Icons.Rounded.Refresh, "Prüfen") }
                }
            }
        }
        if (apps.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Noch keine Apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unter „Hinzufügen“: Link einfügen (GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKMirror, APKPure), installierte Apps übernehmen oder deinen ObtainX-Export importieren.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (updates.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                    Text("Updates (${updates.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(enabled = busy == null, onClick = {
                        scope.launch {
                            // One after another: each opens the installer preview.
                            for (app in updates.filter { Source.forUrl(it.url)?.trackOnly != true }) {
                                busy = app.key
                                runCatching { Updates.download(app) {} }.onSuccess { (_, uri) -> openInstaller(context, uri) }
                            }
                            busy = null
                        }
                    }) { Text("Alle aktualisieren") }
                }
            }
            items(updates, key = { "u" + it.key }) { app -> AppRow(app, states[app.key], busy == app.key, { onOpen(app) }) { install(app) } }
            item { Text("Alle Apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 12.dp)) }
        }
        items(others, key = { it.key }) { app -> AppRow(app, states[app.key], busy == app.key, { onOpen(app) }) { install(app) } }
    }
}

@Composable
private fun AppRow(app: TrackedApp, state: UpdateState?, busy: Boolean, onOpen: () -> Unit, onInstall: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state != UpdateState.NotInstalled && app.id.isNotBlank()) {
                AppIcon(app.id, 44)
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "von ${app.author} · ${Source.forUrl(app.url)?.label ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                app.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    app.latestVersion ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                Text(
                    when (state) {
                        UpdateState.NotInstalled -> "nicht installiert"
                        UpdateState.Unknown -> "unklar"
                        else -> if (app.releaseDate > 0) shortDate.format(Date(app.releaseDate)) else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state == UpdateState.Update || state == UpdateState.NotInstalled) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(36.dp).padding(6.dp))
                } else {
                    FilledTonalIconButton(onClick = onInstall) { Icon(Icons.Rounded.Download, "Installieren") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTab(onAdded: () -> Unit, onImportInstalled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchSources by remember { mutableStateOf(de.emil.shizubox.updates.SourceSearch.SOURCES.toSet()) }
    var results by remember { mutableStateOf<List<de.emil.shizubox.updates.SearchResult>?>(null) }

    fun add(link: String) {
        busy = true
        message = null
        scope.launch {
            message = runCatching { Updates.add(link) }.fold({ "${it.name} hinzugefügt ✓" }, { it.message ?: "Fehler" })
            busy = false
            if (message?.endsWith("✓") == true) url = ""
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
                val (added, unsupported) = Updates.import(text)
                Updates.checkAll()
                "$added Apps importiert" + if (unsupported > 0) ", $unsupported mit noch nicht unterstützter Quelle" else ""
            }.getOrElse { "Import fehlgeschlagen: ${it.message}" }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(Updates.export().toByteArray()) }
            "Exportiert ✓"
        }.getOrElse { "Export fehlgeschlagen: ${it.message}" }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("App hinzufügen", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp, top = 72.dp, bottom = 8.dp)) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Quell-URL der App", style = MaterialTheme.typography.titleMedium)
                Text(
                    "GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKMirror (nur Versionsprüfung) oder APKPure",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://github.com/…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(onClick = { add(url) }, enabled = url.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (busy) "Prüfe …" else "Hinzufügen")
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.endsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Quellen durchsuchen", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Suchen") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(enabled = query.isNotBlank(), onClick = {
                        results = null
                        scope.launch { results = de.emil.shizubox.updates.SourceSearch.search(query, searchSources) }
                    }) { Text("Los") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.emil.shizubox.updates.SourceSearch.SOURCES.forEach { s ->
                        FilterChip(
                            selected = s in searchSources,
                            onClick = { searchSources = if (s in searchSources) searchSources - s else searchSources + s },
                            label = { Text(s) },
                        )
                    }
                }
                results?.let { list ->
                    if (list.isEmpty()) Text("Nichts gefunden", style = MaterialTheme.typography.bodyMedium)
                    list.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${r.source} · ★ ${r.stars}" + if (r.description.isNotBlank()) " · ${r.description}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(enabled = !busy, onClick = { add(r.url) }) { Text("Hinzufügen") }
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = onImportInstalled) {
                Text("Geräte-Apps übernehmen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Installierte Apps auswählen – ShizuBox sucht sie auf F-Droid, IzzyOnDroid und APKPure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Import / Export", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kompatibel mit ObtainX und Obtainium (JSON-Export).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Importieren") }
                    OutlinedButton(onClick = { exportFile.launch("shizubox-apps.json") }, modifier = Modifier.weight(1f)) { Text("Exportieren") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTab() {
    val s by Updates.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var token by rememberSaveable { mutableStateOf(s.githubToken) }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Einstellungen", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp, top = 72.dp, bottom = 8.dp)) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Hintergrundprüfung", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(0 to "Aus", 6 to "6 Std", 12 to "12 Std", 24 to "1 Tag", 48 to "2 Tage", 168 to "1 Woche").forEach { (h, label) ->
                        FilterChip(selected = s.intervalHours == h, onClick = { Updates.setSettings(s.copy(intervalHours = h)) }, label = { Text(label) })
                    }
                }
                Toggle("Updates im Hintergrund installieren (Shizuku)", s.autoInstall) { Updates.setSettings(s.copy(autoInstall = it)) }
                if (s.autoInstall) {
                    Toggle("Nur nach sauberem VirusTotal-Ergebnis", s.virusTotalGate) { Updates.setSettings(s.copy(virusTotalGate = it)) }
                    Text(
                        if (de.emil.shizubox.installer.VirusTotal.apiKey.isBlank()) "Kein VirusTotal-Schlüssel hinterlegt – im Installer unter „Schlüssel eingeben“ eintragen."
                        else "Signatur-Abweichungen werden nie automatisch installiert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Toggle("Nur im WLAN", s.wifiOnly) { Updates.setSettings(s.copy(wifiOnly = it)) }
                Toggle("Nur beim Laden", s.chargingOnly) { Updates.setSettings(s.copy(chargingOnly = it)) }
                Toggle("Beim Öffnen der App prüfen", s.checkOnStart) { Updates.setSettings(s.copy(checkOnStart = it)) }
                Toggle("Vorabversionen standardmäßig einbeziehen", s.prereleasesByDefault) { Updates.setSettings(s.copy(prereleasesByDefault = it)) }
                FilledTonalButton(onClick = { scope.launch { Updates.checkAll() } }, modifier = Modifier.padding(top = 8.dp)) { Text("Jetzt prüfen") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("GitHub-Token (optional)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ohne Token erlaubt GitHub nur 60 Abfragen pro Stunde. Ein Token ohne Berechtigungen reicht. Er bleibt nur auf diesem Handy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = token, onValueChange = { token = it.trim() }, singleLine = true, placeholder = { Text("github_pat_…") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = { Updates.setSettings(s.copy(githubToken = token)) }) { Text("Speichern") }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Token erstellen") }
                }
            }
        }
        item {
            Text(
                "Funktionsweise nach Obtainium und ObtainX (GPL-3.0).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
