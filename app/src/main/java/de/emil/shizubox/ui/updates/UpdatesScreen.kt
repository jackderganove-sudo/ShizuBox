@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.updates

import androidx.compose.material.icons.rounded.SystemUpdate
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.slideFade
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import de.emil.shizubox.ui.motion.rememberListStart
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.Motion
import androidx.compose.foundation.lazy.itemsIndexed
import de.emil.shizubox.R
import de.emil.shizubox.t
import de.emil.shizubox.tp
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.Sort
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
import androidx.compose.material3.ButtonDefaults
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

private val shortDate = SimpleDateFormat("MM-dd", Locale.ROOT)

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
        AnimatedContent(
            targetState = page,
            transitionSpec = { slideFade(forward = targetState > initialState) },
            label = "updPage",
        ) { p ->
            when (p) {
                0 -> AppsPage(resumeTick, onBack, onOpen = { route = Route.Detail(it.key) }, onBrowser = { route = Route.Browser(it) })
                1 -> AddPage(onRoute = { route = it }, onAdded = { page = 0 })
                2 -> ImportExportPage()
                else -> SettingsPage()
            }
        }
        // Bottom bar like ObtainX: "update all" button (with count) left of the navigation pill.
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                page == 0 && (updateCount > 0 || updatingAll?.first != null),
                enter = scaleIn(Motion.bouncy()) + fadeIn(),
                exit = scaleOut(Motion.quick()) + fadeOut(),
            ) {
                val running = updatingAll?.first != null
                androidx.compose.material3.BadgedBox(
                    badge = { if (!running) androidx.compose.material3.Badge { RollingText("$updateCount", MaterialTheme.typography.labelSmall) } },
                ) {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            if (running) return@FloatingActionButton
                            if (de.emil.shizubox.installer.ShizukuApkInstaller.useShizuku() && de.emil.shizubox.shizuku.ShizukuManager.isReady) {
                                Updates.updateAllInBackground()
                            } else {
                                // Without Shizuku: open the installer (system dialog) for each update in turn.
                                scope.launch {
                                    for (app in apps.filter { Updates.state(it) == UpdateState.Update && Source.forUrl(it.url)?.trackOnly != true }) {
                                        runCatching { Updates.download(app) {} }.onSuccess { (_, uri) -> openInstaller(context, uri) }
                                    }
                                }
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        if (running) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        else Icon(Icons.Rounded.SystemUpdateAlt, t(R.string.upd_all))
                    }
                }
            }
            PillNav(
                items = listOf(
                    Icons.Rounded.Apps to t(R.string.nav_apps),
                    Icons.Rounded.Add to t(R.string.nav_add),
                    Icons.Rounded.CloudUpload to t(R.string.nav_ie),
                    Icons.Rounded.Settings to t(R.string.settings),
                ),
                selected = page,
                onSelect = { page = it },
            )
        }
        // Result of "update all": what was held back and why.
        updatingAll?.let { (currentKey, failed) ->
            if (currentKey == null) {
                ObCard(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).fillMaxWidth()) {
                    Text(
                        if (failed.isEmpty()) t(R.string.upd_all_done) else tp(R.plurals.upd_not_auto, failed.size, failed.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    failed.forEach { (name, reason) ->
                        Text("$name: $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (failed.isNotEmpty()) {
                        Text(
                            t(R.string.upd_not_auto_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { Updates.clearUpdateAllResult() }) { Text(t(R.string.ok)) }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
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
    var sort by rememberSaveable { mutableStateOf(0) }
    val order: Comparator<TrackedApp> = when (sort) {
        1 -> compareByDescending<TrackedApp> { it.releaseDate }.thenBy { it.name.lowercase() }
        2 -> compareBy<TrackedApp> { Source.forUrl(it.url)?.label.orEmpty() }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    val pinnedFirst = compareByDescending<TrackedApp> { it.pinned }.then(order)
    val updates = apps.filter { states[it.key] == UpdateState.Update }.sortedWith(pinnedFirst)
    val others = apps.filter { states[it.key] != UpdateState.Update }.sortedWith(pinnedFirst)
    var showUpdates by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    val updatingAll by Updates.updatingAll.collectAsStateWithLifecycle()
    val busyKey = busy ?: updatingAll?.first
    var query by rememberSaveable { mutableStateOf("") }
    val updListStart = rememberListStart()
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
            PageTitle(t(R.string.nav_apps), onBack) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, t(R.string.search)) }
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, t(R.string.upd_sort)) }
                    androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(t(R.string.s_name), t(R.string.upd_sort_release), t(R.string.upd_sort_source)).forEachIndexed { i, label ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (sort == i) Icon(Icons.Rounded.Check, null) },
                                onClick = { sort = i; sortMenu = false },
                            )
                        }
                    }
                }
                if (checking) CircularProgressIndicator(Modifier.size(40.dp).padding(8.dp))
                else IconButton(onClick = { Updates.checkAllInBackground() }) { Icon(Icons.Rounded.Refresh, t(R.string.check)) }
            }
            if (searching) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(t(R.string.search_app)) }, shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
        }
        if (apps.isEmpty()) {
            item { EmptyState(Icons.Rounded.SystemUpdate, t(R.string.upd_empty), t(R.string.upd_empty_hint)) }
        }
        val shownUpdates = updates.filter(::matches)
        if (shownUpdates.isNotEmpty()) {
            item { GroupHeader(t(R.string.sec_updates), shownUpdates.size, showUpdates) { showUpdates = !showUpdates } }
            if (showUpdates) {
                itemsIndexed(shownUpdates, key = { _, app -> "u" + app.key }) { index, app ->
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(index, updListStart)) {
                    AppRow(app, states[app.key], busyKey == app.key, onOpen = { onOpen(app) }, onInstall = { install(app) })
                    }
                }
            }
        }
        itemsIndexed(others.filter(::matches), key = { _, app -> app.key }) { index, app ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(index, updListStart)) {
            AppRow(app, states[app.key], busyKey == app.key, onOpen = { onOpen(app) }, onInstall = { install(app) })
            }
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
                    app.error ?: t(R.string.by_author, app.author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (app.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VersionChip(
                version = app.latestVersion,
                date = when (state) {
                    UpdateState.NotInstalled -> t(R.string.not_installed)
                    else -> if (app.releaseDate > 0) shortDate.format(Date(app.releaseDate)) else null
                },
                highlight = state == UpdateState.Update,
            )
            if (state == UpdateState.Update || state == UpdateState.NotInstalled) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(40.dp).padding(8.dp))
                } else {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Rounded.SystemUpdateAlt, t(R.string.install), tint = MaterialTheme.colorScheme.primary)
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
        item { PageTitle(t(R.string.add_app)) }
        item {
            MenuEntry(Icons.Rounded.Link, t(R.string.add_url), t(R.string.add_url_sub)) { showUrl = !showUrl }
            if (showUrl) {
                ObCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, placeholder = { Text("https://…") }, modifier = Modifier.fillMaxWidth())
                    Button(shapes = ButtonDefaults.shapes(), 
                        onClick = {
                            busy = true
                            scope.launch {
                                message = runCatching { Updates.add(url) }.fold({ t(R.string.added_ok, it.name) }, { it.message ?: t(R.string.error) })
                                busy = false
                                if (message!!.endsWith("✓")) { url = ""; onAdded() }
                            }
                        },
                        enabled = url.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(if (busy) t(R.string.checking) else t(R.string.add)) }
                    message?.let { Text(it, color = if (it.endsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
        item { MenuEntry(Icons.Rounded.Search, t(R.string.add_search), t(R.string.add_search_sub)) { onRoute(Route.Search) } }
        item { MenuEntry(Icons.Rounded.PhoneAndroid, t(R.string.add_device), t(R.string.add_device_sub)) { onRoute(Route.ImportDevice) } }
        item { MenuEntry(Icons.AutoMirrored.Rounded.PlaylistAdd, t(R.string.add_urls)) { onRoute(Route.UrlList) } }
        item { MenuEntry(Icons.Rounded.Star, t(R.string.add_stars)) { onRoute(Route.Stars) } }
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
                t(R.string.ie_imported, added) + if (unsupported > 0) t(R.string.ie_unsupported, unsupported) else ""
            }.getOrElse { t(R.string.ie_import_failed, it.message) }
            busy = false
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(Updates.export().toByteArray()) }
            t(R.string.ie_exported)
        }.getOrElse { t(R.string.ie_export_failed, it.message) }
    }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageTitle(t(R.string.nav_ie)) }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text("ObtainX / Obtainium", style = MaterialTheme.typography.titleMedium)
                Text(
                    t(R.string.ie_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = { importFile.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                        Text(if (busy) t(R.string.importing) else t(R.string.do_import))
                    }
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { exportFile.launch("shizubox-apps.json") }, modifier = Modifier.weight(1f)) { Text(t(R.string.export)) }
                }
                message?.let { Text(it, color = if (it.startsWith("⚠")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
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
        item { PageTitle(t(R.string.settings)) }
        item { SectionLabel(t(R.string.us_updates)) }
        item {
            Button(shapes = ButtonDefaults.shapes(), onClick = { Updates.checkAllInBackground() }, modifier = Modifier.fillMaxWidth()) { Text(t(R.string.us_check_now)) }
        }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.us_interval), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf(0 to t(R.string.off), 6 to t(R.string.h6), 12 to t(R.string.h12), 24 to t(R.string.d1), 48 to t(R.string.d2), 168 to t(R.string.w1)).forEach { (h, label) ->
                        FilterChip(selected = s.intervalHours == h, onClick = { Updates.setSettings(s.copy(intervalHours = h)) }, label = { Text(label) })
                    }
                }
            }
        }
        item { SettingRow(t(R.string.un_setting), t(R.string.un_setting_sub), s.notify) { Updates.setSettings(s.copy(notify = it)) } }
        item { SettingRow(t(R.string.us_bg_install), t(R.string.us_bg_install_sub), s.autoInstall) { Updates.setSettings(s.copy(autoInstall = it)) } }
        if (s.autoInstall) {
            item {
                SettingRow(
                    t(R.string.us_vt_gate),
                    if (de.emil.shizubox.installer.VirusTotal.apiKey.isBlank()) t(R.string.us_no_key) else t(R.string.us_sig_never),
                    s.virusTotalGate,
                ) { Updates.setSettings(s.copy(virusTotalGate = it)) }
            }
        }
        item { SettingRow(t(R.string.us_wifi), null, s.wifiOnly) { Updates.setSettings(s.copy(wifiOnly = it)) } }
        item { SettingRow(t(R.string.us_charging), null, s.chargingOnly) { Updates.setSettings(s.copy(chargingOnly = it)) } }
        item { SettingRow(t(R.string.us_on_start), null, s.checkOnStart) { Updates.setSettings(s.copy(checkOnStart = it)) } }
        item { SettingRow(t(R.string.us_prerelease), null, s.prereleasesByDefault) { Updates.setSettings(s.copy(prereleasesByDefault = it)) } }
        item { SectionLabel(t(R.string.us_source_specific)) }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.us_token), style = MaterialTheme.typography.titleSmall)
                Text(
                    t(R.string.us_token_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it.trim() }, singleLine = true,
                    placeholder = { Text("github_pat_…") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { Updates.setSettings(s.copy(githubToken = token)) }) { Text(t(R.string.save)) }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(t(R.string.us_create_token)) }
                }
            }
        }
        item {
            Text(
                t(R.string.us_credit),
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
            PageTitle(t(R.string.add_search), onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(t(R.string.search)) }, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = query.isNotBlank() && !loading, onClick = {
                    loading = true
                    scope.launch { results = SourceSearch.search(query, sources); loading = false }
                }) { Icon(Icons.Rounded.Search, t(R.string.search)) }
            }
            Text(t(R.string.srch_sources), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
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
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                            scope.launch {
                                runCatching { Updates.add(r.url) }.onSuccess { added = added + r.url }.onFailure { message = "${r.name}: ${it.message}" }
                            }
                        }) { Text(t(R.string.add)) }
                    }
                }
            }
        }
        if (results?.isEmpty() == true) item { Text(t(R.string.nothing_found), modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
private fun UrlListScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        PageTitle(t(R.string.add_urls), onBack)
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text(t(R.string.one_link_per_line)) },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
        Button(shapes = ButtonDefaults.shapes(), enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), onClick = {
            busy = true
            scope.launch {
                val urls = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                var ok = 0
                val failed = mutableListOf<String>()
                urls.forEach { u -> runCatching { Updates.add(u) }.onSuccess { ok++ }.onFailure { failed += "$u – ${it.message}" } }
                result = t(R.string.n_of_m_added, ok, urls.size) + if (failed.isNotEmpty()) "\n" + failed.joinToString("\n") else ""
                busy = false
            }
        }) { Text(if (busy) t(R.string.importing) else t(R.string.do_import)) }
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
            PageTitle(t(R.string.stars_title), onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = user, onValueChange = { user = it.trim() }, singleLine = true, placeholder = { Text(t(R.string.gh_user)) }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = user.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        repos = runCatching { SourceSearch.starred(user) }.getOrElse { result = it.message; emptyList() }
                        chosen = repos.orEmpty().toSet()
                        busy = false
                    }
                }) { Text(t(R.string.load)) }
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
                Button(shapes = ButtonDefaults.shapes(), enabled = chosen.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                    busy = true
                    scope.launch {
                        var ok = 0
                        chosen.forEach { u -> if (runCatching { Updates.add(u) }.isSuccess) ok++ }
                        result = t(R.string.stars_result, ok, chosen.size)
                        busy = false
                    }
                }) { Text(if (busy) t(R.string.adding) else tp(R.plurals.add_n_repos, chosen.size, chosen.size)) }
            }
        }
    }
}
