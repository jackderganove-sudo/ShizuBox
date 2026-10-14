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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import de.emil.shizubox.updates.SearchResult
import de.emil.shizubox.updates.Source
import de.emil.shizubox.updates.SourceSearch
import de.emil.shizubox.updates.TrackedApp
import de.emil.shizubox.updates.UpdateState
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shortDate = SimpleDateFormat("MM-dd", Locale.GERMAN)

/** Hands a downloaded APK to ShizuBox's own installer screen (preview, VirusTotal, Shizuku install). */
fun openInstaller(context: Context, uri: Uri) {
    context.startActivity(
        Intent(context, InstallActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

/** Sub pages of the updates area. */
private sealed interface Route {
    data class Detail(val key: String) : Route
    data object ImportDevice : Route
    data object Search : Route
    data object UrlList : Route
    data object Stars : Route
    data class Browser(val url: String) : Route
}

@Composable
fun UpdatesScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        Updates.refreshInstalledStates()
        resumeTick++
        onPauseOrDispose { }
    }

    when (val r = route) {
        is Route.Detail -> {
            BackHandler { route = null }
            val app = Updates.apps.collectAsStateWithLifecycle().value.firstOrNull { it.key == r.key }
            if (app == null) route = null else UpdateDetailScreen(app, resumeTick, onBack = { route = null }, onOpenBrowser = { route = Route.Browser(it) })
            return
        }
        Route.ImportDevice -> { BackHandler { route = null }; ImportFlowScreen(onDone = { route = null; page = 0 }); return }
        Route.Search -> { BackHandler { route = null }; SearchScreen(onBack = { route = null }); return }
        Route.UrlList -> { BackHandler { route = null }; UrlListScreen(onBack = { route = null }); return }
        Route.Stars -> { BackHandler { route = null }; StarsScreen(onBack = { route = null }); return }
        is Route.Browser -> { BrowserScreen(r.url, onClose = { route = null }); return }
        null -> {}
    }

    val apps by Updates.apps.collectAsStateWithLifecycle()
    val updateCount = remember(apps, resumeTick) { Updates.updateCount() }
    val updatingAll by Updates.updatingAll.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (page) {
            0 -> AppsPage(resumeTick, onBack, onOpen = { route = Route.Detail(it.key) }, onBrowser = { route = Route.Browser(it) })
            1 -> AddPage(onRoute = { route = it }, onAdded = { page = 0 })
            2 -> ImportExportPage()
            else -> SettingsPage()
        }
        PillNav(
            items = listOf(
                Icons.Rounded.Apps to "Apps",
                Icons.Rounded.Add to "Hinzufügen",
                Icons.Rounded.CloudUpload to "Import/Export",
                Icons.Rounded.Settings to "Einstellungen",
            ),
            selected = page,
            onSelect = { page = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
        // "Update all" like ObtainX: round button bottom left with the number of updates.
        if (page == 0 && (updateCount > 0 || updatingAll?.first != null)) {
            val running = updatingAll?.first != null
            androidx.compose.material3.BadgedBox(
                badge = { if (!running) androidx.compose.material3.Badge { Text("$updateCount") } },
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = 84.dp),
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { if (!running) Updates.updateAllInBackground() },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    if (running) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    else Icon(Icons.Rounded.SystemUpdateAlt, "Alle aktualisieren")
                }
            }
        }
        // Result of "update all": what was held back and why.
        updatingAll?.let { (currentKey, failed) ->
            if (currentKey == null) {
                ObCard(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).fillMaxWidth()) {
                    Text(
                        if (failed.isEmpty()) "Alle Updates installiert ✓" else "${failed.size} nicht automatisch installiert",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    failed.forEach { (name, reason) ->
                        Text("$name: $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (failed.isNotEmpty()) {
                        Text(
                            "Diese Apps einzeln über ihren Knopf aktualisieren – der Installer zeigt dann Details.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    TextButton(onClick = { Updates.clearUpdateAllResult() }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 0.dp),
        )
        actions()
    }
}

// ------------------------------------------------------------------ Apps

@Composable
private fun AppsPage(resumeTick: Int, onBack: () -> Unit, onOpen: (TrackedApp) -> Unit, onBrowser: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by Updates.apps.collectAsStateWithLifecycle()
    val checking by Updates.checking.collectAsStateWithLifecycle()
    val states = remember(apps, resumeTick) { apps.associate { it.key to Updates.state(it) } }
    val updates = apps.filter { states[it.key] == UpdateState.Update }.sortedBy { it.name.lowercase() }
    val others = apps.filter { states[it.key] != UpdateState.Update }.sortedBy { it.name.lowercase() }
    var showUpdates by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    val updatingAll by Updates.updatingAll.collectAsStateWithLifecycle()
    val busyKey = busy ?: updatingAll?.first
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun install(app: TrackedApp) {
        if (Source.forUrl(app.url)?.trackOnly == true) {
            onBrowser(app.releaseUrl ?: app.url)
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

    fun matches(a: TrackedApp) = query.isBlank() || a.name.contains(query, true) || a.author.contains(query, true)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageTitle("Apps", onBack) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, "Suchen") }
                if (checking) CircularProgressIndicator(Modifier.size(40.dp).padding(8.dp))
                else IconButton(onClick = { Updates.checkAllInBackground() }) { Icon(Icons.Rounded.Refresh, "Prüfen") }
            }
            if (searching) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("App suchen") }, shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
        }
        if (apps.isEmpty()) {
            item {
                ObCard(Modifier.fillMaxWidth()) {
                    Text("Noch keine Apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Über „Hinzufügen“ einen Link einfügen, installierte Apps übernehmen – oder unter Import/Export deinen ObtainX-Export laden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val shownUpdates = updates.filter(::matches)
        if (shownUpdates.isNotEmpty()) {
            item { GroupHeader("Updates", shownUpdates.size, showUpdates) { showUpdates = !showUpdates } }
            if (showUpdates) {
                items(shownUpdates, key = { "u" + it.key }) { app ->
                    AppRow(app, states[app.key], busyKey == app.key, onOpen = { onOpen(app) }, onInstall = { install(app) })
                }
            }
        }
        items(others.filter(::matches), key = { it.key }) { app ->
            AppRow(app, states[app.key], busyKey == app.key, onOpen = { onOpen(app) }, onInstall = { install(app) })
        }
    }
}

@Composable
private fun AppRow(app: TrackedApp, state: UpdateState?, busy: Boolean, onOpen: () -> Unit, onInstall: () -> Unit) {
    val source = Source.forUrl(app.url)?.label
    ObCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                if (state != UpdateState.NotInstalled && app.id.isNotBlank()) {
                    AppIcon(app.id, 52)
                } else {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge) }
                }
            }
            Spacer(Modifier.width(10.dp))
            SourceBadge(source, 24)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.error ?: "Von ${app.author}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (app.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VersionChip(
                version = app.latestVersion,
                date = when (state) {
                    UpdateState.NotInstalled -> "nicht installiert"
                    else -> if (app.releaseDate > 0) shortDate.format(Date(app.releaseDate)) else null
                },
                highlight = state == UpdateState.Update,
            )
            if (state == UpdateState.Update || state == UpdateState.NotInstalled) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(40.dp).padding(8.dp))
                } else {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Rounded.SystemUpdateAlt, "Installieren", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Add

@Composable
private fun AddPage(onRoute: (Route) -> Unit, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showUrl by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { PageTitle("App hinzufügen") }
        item {
            MenuEntry(Icons.Rounded.Link, "Quell-URL der App", "GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKPure, APKMirror") { showUrl = !showUrl }
            if (showUrl) {
                ObCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, placeholder = { Text("https://…") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                message = runCatching { Updates.add(url) }.fold({ "${it.name} hinzugefügt ✓" }, { it.message ?: "Fehler" })
                                busy = false
                                if (message!!.endsWith("✓")) { url = ""; onAdded() }
                            }
                        },
                        enabled = url.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(if (busy) "Prüfe …" else "Hinzufügen") }
                    message?.let { Text(it, color = if (it.endsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
        item { MenuEntry(Icons.Rounded.Search, "Quellen durchsuchen", "GitHub, Codeberg, GitLab") { onRoute(Route.Search) } }
        item { MenuEntry(Icons.Rounded.PhoneAndroid, "Geräte-Apps gesammelt importieren", "Auf F-Droid, IzzyOnDroid und APKPure suchen") { onRoute(Route.ImportDevice) } }
        item { MenuEntry(Icons.AutoMirrored.Rounded.PlaylistAdd, "Aus URL-Liste importieren") { onRoute(Route.UrlList) } }
        item { MenuEntry(Icons.Rounded.Star, "Mit Stern markierte GitHub-Repos importieren") { onRoute(Route.Stars) } }
    }
}

// ------------------------------------------------------------------ Import / Export

@Composable
private fun ImportExportPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
                val (added, unsupported) = Updates.import(text)
                Updates.checkAllInBackground()
                "$added Apps importiert" + if (unsupported > 0) " ($unsupported mit noch nicht unterstützter Quelle)" else ""
            }.getOrElse { "Import fehlgeschlagen: ${it.message}" }
            busy = false
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageTitle("Import/Export") }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text("ObtainX / Obtainium", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Liest den JSON-Export von ObtainX und Obtainium. Der Export von ShizuBox lässt sich dort wieder importieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { importFile.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                        Text(if (busy) "Importiere …" else "Import")
                    }
                    OutlinedButton(onClick = { exportFile.launch("shizubox-apps.json") }, modifier = Modifier.weight(1f)) { Text("Export") }
                }
                message?.let { Text(it, color = if (it.contains("fehlgeschlagen")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

// ------------------------------------------------------------------ Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage() {
    val s by Updates.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var token by rememberSaveable { mutableStateOf(s.githubToken) }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageTitle("Einstellungen") }
        item { SectionLabel("Aktualisierungen") }
        item {
            Button(onClick = { Updates.checkAllInBackground() }, modifier = Modifier.fillMaxWidth()) { Text("Jetzt auf Updates prüfen") }
        }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text("Prüfintervall im Hintergrund", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf(0 to "Aus", 6 to "6 Std", 12 to "12 Std", 24 to "1 Tag", 48 to "2 Tage", 168 to "1 Woche").forEach { (h, label) ->
                        FilterChip(selected = s.intervalHours == h, onClick = { Updates.setSettings(s.copy(intervalHours = h)) }, label = { Text(label) })
                    }
                }
            }
        }
        item { SettingRow("Installation von Updates im Hintergrund", "Still über Shizuku", s.autoInstall) { Updates.setSettings(s.copy(autoInstall = it)) } }
        if (s.autoInstall) {
            item {
                SettingRow(
                    "Nur nach sauberem VirusTotal-Ergebnis",
                    if (de.emil.shizubox.installer.VirusTotal.apiKey.isBlank()) "Kein Schlüssel hinterlegt – im Installer eintragen" else "Andere Signatur wird nie automatisch installiert",
                    s.virusTotalGate,
                ) { Updates.setSettings(s.copy(virusTotalGate = it)) }
            }
        }
        item { SettingRow("Nur im WLAN", null, s.wifiOnly) { Updates.setSettings(s.copy(wifiOnly = it)) } }
        item { SettingRow("Nur während das Gerät lädt", null, s.chargingOnly) { Updates.setSettings(s.copy(chargingOnly = it)) } }
        item { SettingRow("Beim Start auf Updates prüfen", null, s.checkOnStart) { Updates.setSettings(s.copy(checkOnStart = it)) } }
        item { SettingRow("Vorabversionen standardmäßig einbeziehen", null, s.prereleasesByDefault) { Updates.setSettings(s.copy(prereleasesByDefault = it)) } }
        item { SectionLabel("Quellenspezifisch") }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text("Persönlicher GitHub-Zugriffstoken", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ohne Token erlaubt GitHub nur 60 Abfragen pro Stunde. Ein Token ohne Berechtigungen reicht; er bleibt auf diesem Handy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = token, onValueChange = { token = it.trim() }, singleLine = true, placeholder = { Text("github_pat_…") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = { Updates.setSettings(s.copy(githubToken = token)) }) { Text("Speichern") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Token erstellen") }
                }
            }
        }
        item {
            Text(
                "Funktionsweise nach Obtainium und ObtainX (GPL-3.0).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
}

@Composable
fun SettingRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ObCard(Modifier.fillMaxWidth(), onClick = { onChange(!checked) }, padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Switch(checked = checked, onCheckedChange = onChange)
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

// ------------------------------------------------------------------ Search / URL list / Stars

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var sources by remember { mutableStateOf(SourceSearch.SOURCES.toSet()) }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(setOf<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageTitle("Quellen durchsuchen", onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Suchen") }, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(enabled = query.isNotBlank() && !loading, onClick = {
                    loading = true
                    scope.launch { results = SourceSearch.search(query, sources); loading = false }
                }) { Icon(Icons.Rounded.Search, "Suchen") }
            }
            Text("Durchsuchbare Quellen", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceSearch.SOURCES.forEach { s ->
                    FilterChip(selected = s in sources, onClick = { sources = if (s in sources) sources - s else sources + s }, label = { Text(s) }, leadingIcon = { SourceBadge(s, 18) })
                }
            }
            if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(results.orEmpty(), key = { it.url }) { r ->
            ObCard(Modifier.fillMaxWidth(), padding = 12) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(r.source, 28)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Text("★ ${r.stars}" + if (r.description.isNotBlank()) " · ${r.description}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (r.url in added) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { Updates.add(r.url) }.onSuccess { added = added + r.url }.onFailure { message = "${r.name}: ${it.message}" }
                            }
                        }) { Text("Hinzufügen") }
                    }
                }
            }
        }
        if (results?.isEmpty() == true) item { Text("Nichts gefunden", modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
private fun UrlListScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        PageTitle("Aus URL-Liste importieren", onBack)
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Ein Link pro Zeile") },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
        Button(enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), onClick = {
            busy = true
            scope.launch {
                val urls = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                var ok = 0
                val failed = mutableListOf<String>()
                urls.forEach { u -> runCatching { Updates.add(u) }.onSuccess { ok++ }.onFailure { failed += "$u – ${it.message}" } }
                result = "$ok von ${urls.size} hinzugefügt" + if (failed.isNotEmpty()) "\n" + failed.joinToString("\n") else ""
                busy = false
            }
        }) { Text(if (busy) "Importiere …" else "Importieren") }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun StarsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var user by rememberSaveable { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<String>?>(null) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            PageTitle("GitHub-Sterne importieren", onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = user, onValueChange = { user = it.trim() }, singleLine = true, placeholder = { Text("GitHub-Benutzername") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(enabled = user.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        repos = runCatching { SourceSearch.starred(user) }.getOrElse { result = it.message; emptyList() }
                        chosen = repos.orEmpty().toSet()
                        busy = false
                    }
                }) { Text("Laden") }
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
        items(repos.orEmpty(), key = { it }) { url ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = url in chosen, onCheckedChange = { chosen = if (it) chosen + url else chosen - url })
                Text(url.removePrefix("https://github.com/"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!repos.isNullOrEmpty()) {
            item {
                Button(enabled = chosen.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                    busy = true
                    scope.launch {
                        var ok = 0
                        chosen.forEach { u -> if (runCatching { Updates.add(u) }.isSuccess) ok++ }
                        result = "$ok von ${chosen.size} hinzugefügt (Repos ohne APK-Release werden übersprungen)"
                        busy = false
                    }
                }) { Text(if (busy) "Füge hinzu …" else "${chosen.size} Repos hinzufügen") }
            }
        }
    }
}
