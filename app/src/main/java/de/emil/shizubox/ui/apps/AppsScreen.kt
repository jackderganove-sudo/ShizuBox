package de.emil.shizubox.ui.apps

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
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppFilter(val label: String) { User("Nutzer"), System("System"), Frozen("Eingefroren") }

private enum class AppSort(val label: String) { Name("Name"), Size("Größe"), Installed("Installiert"), Updated("Aktualisiert") }

private val dateFormat = java.text.SimpleDateFormat("d. MMM yy", java.util.Locale.GERMAN)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) { apps = Apps.list() }

    selected?.let { pkg ->
        BackHandler { selected = null; reloadKey++ }
        AppDetailScreen(pkg, shizukuReady, onBack = { selected = null; reloadKey++ })
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.User) }
    var sort by rememberSaveable { mutableStateOf(AppSort.Name) }
    val visible = remember(apps, query, filter, sort) {
        apps.orEmpty().filter { a ->
            when (filter) {
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text("Apps", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Einfrieren, Berechtigungen, AppOps, Akku",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("Apps", "Profile").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = tab == i,
                        onClick = { tab = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
        }
        if (!shizukuReady) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text("Shizuku muss verbunden sein, um Änderungen vorzunehmen.") } }
        }
        if (tab == 1) {
            profilesSection(apps.orEmpty(), shizukuReady) { reloadKey++ }
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Suchen") },
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
                    "${visible.size} Apps · ${android.text.format.Formatter.formatShortFileSize(context, visible.sumOf { it.sizeBytes })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { sortMenu = true }) { Text("${sort.label} ↓") }
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
            item { Text("Lade Apps …", modifier = Modifier.padding(8.dp)) }
        }
        items(visible, key = { it.pkg }) { app ->
            AppRow(app) { selected = app.pkg }
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
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onClick) {
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
                        !app.installed -> Badge("Entfernt")
                        app.frozen -> Badge("Eingefroren")
                    }
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
                            append("${dateFormat.format(java.util.Date(app.firstInstall))} installiert")
                            if (app.lastUpdate - app.firstInstall > 60_000) append(" · aktualisiert ${dateFormat.format(java.util.Date(app.lastUpdate))}")
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
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        Text(
            "Gruppen von Apps, die zusammen eingefroren und aufgetaut werden. Apps fügst du in den App-Details hinzu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { creating = true }) { Text("Neues Profil") }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Neues Profil") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("z. B. Social Media") }) },
            confirmButton = {
                TextButton(
                    onClick = { Profiles.create(name); name = ""; creating = false },
                    enabled = name.isNotBlank(),
                ) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Abbrechen") } },
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
                            if (members.isEmpty()) "Noch keine Apps" else members.joinToString { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { deleting = p.id }) { Text("Löschen") }
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    enabled = shizukuReady && members.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            if (allFrozen) Apps.unfreeze(p.packages) else Apps.freeze(p.packages)
                            onChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (allFrozen) "Alle auftauen" else "Alle einfrieren") }
            }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Profil löschen?") },
            text = { Text("Die Apps bleiben, wie sie gerade sind.") },
            confirmButton = { TextButton(onClick = { Profiles.delete(id); deleting = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }
}
