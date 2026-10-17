@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.apps

import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Widgets
import de.emil.shizubox.ui.motion.popIn
import de.emil.shizubox.ui.motion.openClose
import androidx.compose.animation.AnimatedContent
import de.emil.shizubox.ui.motion.rememberListStart
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.Motion
import androidx.compose.foundation.lazy.itemsIndexed
import de.emil.shizubox.R
import de.emil.shizubox.t
import de.emil.shizubox.tp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.apps.AppEntry
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.PermissionOverview
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pseudo package that opens the Activity Launcher instead of app details. */
const val LAUNCHER = "#launcher"
/** Pseudo package that opens the debloat suggestions. */
const val DEBLOAT = "#debloat"

private enum class AppFilter(private val labelRes: Int) {
    User(R.string.f_user), System(R.string.system), Frozen(R.string.f_frozen), NoInternet(R.string.nb_filter);
    val label get() = t(labelRes)
}

private enum class AppSort(private val labelRes: Int) {
    Name(R.string.s_name), Size(R.string.s_size), Installed(R.string.s_installed), Updated(R.string.s_updated);
    val label get() = t(labelRes)
}

private val dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)

/** Opens a page inside Apps from outside (search, favourites): a package, [DEBLOAT], [LAUNCHER] or "tab:<n>". */
object AppsNav {
    val open = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val tabState = rememberSaveable { mutableStateOf(0) }
    val selectedState = rememberSaveable { mutableStateOf<String?>(null) }
    val requested by AppsNav.open.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        val r = requested ?: return@LaunchedEffect
        if (r.startsWith("tab:")) { selectedState.value = null; tabState.value = r.removePrefix("tab:").toIntOrNull() ?: 0 } else selectedState.value = r
        AppsNav.open.value = null
    }
    val appsState = remember { mutableStateOf<List<AppEntry>?>(null) }
    val reloadState = remember { mutableStateOf(0) }
    LaunchedEffect(reloadState.value) { appsState.value = Apps.list() }

    // App details open with a spring and close back into the list.
    AnimatedContent(
        targetState = selectedState.value,
        transitionSpec = { openClose(opening = targetState != null) },
        label = "appDetail",
    ) { pkg ->
        if (pkg == DEBLOAT) {
            BackHandler { selectedState.value = null; reloadState.value++ }
            DebloatScreen(shizukuReady, onBack = { selectedState.value = null; reloadState.value++ })
        } else if (pkg == LAUNCHER) {
            BackHandler { selectedState.value = null }
            ActivityLauncherScreen(onBack = { selectedState.value = null })
        } else if (pkg != null) {
            BackHandler { selectedState.value = null; reloadState.value++ }
            AppDetailScreen(pkg, shizukuReady, onBack = { selectedState.value = null; reloadState.value++ })
        } else {
            AppsList(shizukuReady, onBack, tabState, selectedState, appsState.value, reloadState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppsList(
    shizukuReady: Boolean,
    onBack: () -> Unit,
    tabState: androidx.compose.runtime.MutableState<Int>,
    selectedState: androidx.compose.runtime.MutableState<String?>,
    apps: List<AppEntry>?,
    reloadState: androidx.compose.runtime.MutableState<Int>,
) {
    var tab by tabState
    var selected by selectedState
    var reloadKey by reloadState

    var selection by remember { mutableStateOf(setOf<String>()) }
    val appsListStart = rememberListStart(apps != null)
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    var permState by remember { mutableStateOf(PermTabState()) }
    var permReload by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(tab, permReload, shizukuReady) {
        if (tab == 2) permState = permState.copy(data = PermissionOverview.load(ctx))
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.User) }
    var sort by rememberSaveable { mutableStateOf(AppSort.Name) }
    val netBlocked by de.emil.shizubox.apps.NetBlock.blocked.collectAsStateWithLifecycle()
    val visible = remember(apps, query, filter, sort, netBlocked) {
        apps.orEmpty().filter { a ->
            when (filter) {
                AppFilter.NoInternet -> a.pkg in netBlocked
                AppFilter.User -> !a.isSystem
                AppFilter.System -> a.isSystem
                AppFilter.Frozen -> a.frozen || !a.installed
            } && (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }.let { list ->
            when (sort) {
                AppSort.Name -> list
                AppSort.Size -> list.sortedByDescending { it.sizeBytes }
                AppSort.Installed -> list.sortedByDescending { it.firstInstall }
                AppSort.Updated -> list.sortedByDescending { it.lastUpdate }
            }
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back))
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text(t(R.string.apps), style = MaterialTheme.typography.displaySmall)
                Text(
                    t(R.string.apps_sub),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf(t(R.string.apps), t(R.string.profiles), t(R.string.permissions)).forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = tab == i,
                        onClick = { tab = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(label) }
                }
            }
        }
        if (!shizukuReady) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_for_changes)) } }
        }
        if (tab == 0) {
            item(key = "launcher") {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp, onClick = { selected = LAUNCHER }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Widgets, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.al_title), style = MaterialTheme.typography.titleMedium)
                            Text(t(R.string.al_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
            item(key = "debloat") {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp, onClick = { selected = DEBLOAT }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.db_title), style = MaterialTheme.typography.titleMedium)
                            Text(t(R.string.db_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
        if (tab == 1) {
            profilesSection(apps.orEmpty(), shizukuReady) { reloadKey++ }
            return@LazyColumn
        }
        if (tab == 2) {
            permissionsSection(
                state = permState,
                shizukuReady = shizukuReady,
                onState = { permState = it },
                onRevoke = { g, h ->
                    scope.launch {
                        runCatching { PermissionOverview.revoke(g, h) }
                        permReload++
                    }
                },
                onOpenApp = { selected = it },
            )
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(t(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                AppFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    tp(R.plurals.n_apps_size, visible.size, visible.size, android.text.format.Formatter.formatShortFileSize(context, visible.sumOf { it.sizeBytes })),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { sortMenu = true }) { Text("${sort.label} ↓") }
                    androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        AppSort.entries.forEach { s ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                }
            }
        }
        if (apps == null) {
            item { Text(t(R.string.loading_apps), modifier = Modifier.padding(8.dp)) }
        } else if (selection.isEmpty() && shizukuReady) {
            item {
                Text(
                    t(R.string.sel_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (selection.isNotEmpty()) {
            stickyHeader(key = "selection") {
                SelectionBar(
                    modifier = Modifier.animateItem(placementSpec = Motion.offset).popIn(),
                    selection = selection,
                    apps = apps.orEmpty(),
                    visible = visible,
                    shizukuReady = shizukuReady,
                    onSelection = { selection = it },
                    onDone = { reloadKey++ },
                )
            }
        }
        itemsIndexed(visible, key = { _, app -> app.pkg }) { index, app ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(index, appsListStart)) {
            val isSelected = app.pkg in selection
            AppRow(
                app,
                isSelected,
                onClick = {
                    if (selection.isEmpty()) selected = app.pkg
                    else selection = if (isSelected) selection - app.pkg else selection + app.pkg
                },
                onLongClick = { selection = if (isSelected) selection - app.pkg else selection + app.pkg },
            )
            }
        }
    }
}

@Composable
fun AppIcon(pkg: String, sizeDp: Int = 40) {
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, pkg) {
        value = withContext(Dispatchers.IO) { Apps.icon(pkg) }
    }
    Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(sizeDp.dp)) }
    }
}

@Composable
private fun AppRow(app: AppEntry, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    GlassCard(
        Modifier.fillMaxWidth(),
        contentPadding = 12.dp,
        tint = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.isSplit) Badge("Split")
                    when {
                        !app.installed -> Badge(t(R.string.removed))
                        app.frozen -> Badge(t(R.string.f_frozen))
                    }
                    if (app.pkg in de.emil.shizubox.apps.NetBlock.blocked.collectAsStateWithLifecycle().value) Badge(t(R.string.nb_badge))
                }
                val small = MaterialTheme.typography.bodySmall
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                Text(app.pkg, style = small, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(app.versionName?.let { "v$it" }, android.text.format.Formatter.formatShortFileSize(context, app.sizeBytes))
                        .joinToString(" · "),
                    style = small,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.firstInstall > 0 && !app.isSystem) {
                    Text(
                        buildString {
                            append(t(R.string.installed_on, dateFormat.format(java.util.Date(app.firstInstall))))
                            if (app.lastUpdate - app.firstInstall > 60_000) append(t(R.string.updated_on, dateFormat.format(java.util.Date(app.lastUpdate))))
                        },
                        style = small,
                        color = dim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.profilesSection(
    apps: List<AppEntry>,
    shizukuReady: Boolean,
    onChanged: () -> Unit,
) {
    item { ProfilesHeader() }
    item { ProfilesList(apps, shizukuReady, onChanged) }
}

@Composable
private fun ProfilesHeader() {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.profiles), style = MaterialTheme.typography.titleMedium)
        Text(
            t(R.string.profiles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { creating = true }) { Text(t(R.string.new_profile)) }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(t(R.string.new_profile)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(t(R.string.profile_example)) }) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), 
                    onClick = { Profiles.create(name); name = ""; creating = false },
                    enabled = name.isNotBlank(),
                ) { Text(t(R.string.create)) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { creating = false }) { Text(t(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ProfilesList(apps: List<AppEntry>, shizukuReady: Boolean, onChanged: () -> Unit) {
    val profiles by Profiles.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val byPkg = remember(apps) { apps.associateBy { it.pkg } }
    var deleting by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profiles.forEach { p ->
            val members = p.packages.mapNotNull { byPkg[it] }
            val allFrozen = members.isNotEmpty() && members.all { it.frozen }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (members.isEmpty()) t(R.string.no_apps_yet) else members.joinToString { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { deleting = p.id }) { Text(t(R.string.delete)) }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                    enabled = shizukuReady && members.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            if (allFrozen) Apps.unfreeze(p.packages) else Apps.freeze(p.packages)
                            onChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (allFrozen) t(R.string.unfreeze_all) else t(R.string.freeze_all)) }
            }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t(R.string.delete_profile_q)) },
            text = { Text(t(R.string.delete_profile_hint)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { Profiles.delete(id); deleting = null }) { Text(t(R.string.delete)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { deleting = null }) { Text(t(R.string.cancel)) } },
        )
    }
}
