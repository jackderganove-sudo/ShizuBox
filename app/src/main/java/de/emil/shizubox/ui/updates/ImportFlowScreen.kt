package de.emil.shizubox.ui.updates

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.apps.AppEntry
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.updates.BulkImport
import de.emil.shizubox.updates.ImportHit
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.launch

/** Device apps import in three steps, like ObtainX: select → search per source → review hits. */
@Composable
fun ImportFlowScreen(onDone: () -> Unit) {
    val state by BulkImport.state.collectAsStateWithLifecycle()
    when {
        state.running -> SearchProgress()
        state.finished -> Results(onDone)
        else -> SelectApps(onDone)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectApps(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { all = Apps.list().filter { it.installed } }
    var filter by remember { mutableStateOf(0) } // 0 user, 1 system, 2 all
    var skipTracked by remember { mutableStateOf(true) }
    var sources by remember { mutableStateOf(Updates.PACKAGE_SOURCES.keys.toList()) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    val tracked = remember(all) { Updates.apps.value.map { it.id }.toSet() }
    val visible = all
        .filter { filter == 2 || it.isSystem == (filter == 1) }
        .filter { !skipTracked || it.pkg !in tracked }
        .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
                    }
                    Text("Apps auswählen", style = MaterialTheme.typography.headlineMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Benutzer-Apps", "System-Apps", "Alle").forEachIndexed { i, l ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Updates.PACKAGE_SOURCES.keys.forEach { s ->
                        FilterChip(
                            selected = s in sources,
                            onClick = { sources = if (s in sources) sources - s else Updates.PACKAGE_SOURCES.keys.filter { it in sources || it == s } },
                            label = { Text(s) },
                            leadingIcon = { SourceBadge(s, 18) },
                        )
                    }
                    FilterChip(selected = skipTracked, onClick = { skipTracked = !skipTracked }, label = { Text("Schon überwachte Apps überspringen") })
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Suchen") },
                    shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Ausgewählt: ${chosen.size}/${visible.size}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { chosen = chosen + visible.map { it.pkg } }) { Text("Alle auswählen") }
                    TextButton(onClick = { chosen = emptySet() }) { Text("Auswahl aufheben") }
                }
            }
            items(visible, key = { it.pkg }) { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    AppIcon(app.pkg, 44)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(checked = app.pkg in chosen, onCheckedChange = { chosen = if (it) chosen + app.pkg else chosen - app.pkg })
                }
            }
        }
        if (chosen.isNotEmpty()) {
            BadgedBox(
                badge = { Badge { Text("${chosen.size}") } },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val list = all.filter { it.pkg in chosen }.map { it.pkg }
                        BulkImport.start(list, sources)
                    },
                    icon = { Icon(Icons.Rounded.Search, null) },
                    text = { Text("Suchen") },
                )
            }
        }
    }
}

@Composable
private fun SearchProgress() {
    val state by BulkImport.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Überprüfe …", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                state.current?.let { "Überprüfe ${it.substringBefore(':')} …" } ?: "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
            )
        }
        items(state.progress, key = { it.source }) { p ->
            ObCard(Modifier.fillMaxWidth(), padding = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(p.source, 30)
                    Spacer(Modifier.width(14.dp))
                    Text(p.source, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    when {
                        p.finished -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        p.total == 0 -> Text("Ausstehend", style = MaterialTheme.typography.labelMedium)
                        else -> Text("${p.done}/${p.total} durchsucht", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!p.finished && p.total > 0) {
                    LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
        }
        item {
            OutlinedButton(onClick = { BulkImport.cancel() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Suche abbrechen") }
        }
    }
}

@Composable
private fun Results(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by BulkImport.state.collectAsStateWithLifecycle()
    var chosen by remember(state.hits) { mutableStateOf(state.hits.map { it.pkg }.toSet()) }
    var adding by remember { mutableStateOf(false) }
    var showMissing by remember { mutableStateOf(false) }
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    // Installed app label (F-Droid's API has no names), else the source's name.
    val labels = remember(state.hits) {
        state.hits.associate { h -> h.pkg to (runCatching { pm.getApplicationInfo(h.pkg, 0).loadLabel(pm).toString() }.getOrNull() ?: h.release.name) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { BulkImport.reset(); onDone() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
                    }
                    Text("Ergebnisse importieren", style = MaterialTheme.typography.headlineMedium)
                }
                ObCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Counter(state.hits.size, "Gefunden")
                        Counter(state.notFound.size, "Nicht gefunden")
                        Counter(state.cancelled, "Abgebrochen")
                    }
                }
                Text("Gefunden (${state.hits.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            items(state.hits, key = { it.pkg }) { hit -> HitRow(hit, labels[hit.pkg] ?: hit.pkg, hit.pkg in chosen) { chosen = if (it) chosen + hit.pkg else chosen - hit.pkg } }
            if (state.notFound.isNotEmpty()) {
                item { GroupHeader("Nicht gefunden", state.notFound.size, showMissing) { showMissing = !showMissing } }
                if (showMissing) {
                    items(state.notFound, key = { "nf$it" }) { pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            AppIcon(pkg, 36)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (adding) return@ExtendedFloatingActionButton
                adding = true
                BulkImport.addHitsInBackground(state.hits.filter { it.pkg in chosen }) {
                    BulkImport.reset()
                    onDone()
                }
            },
            icon = { Icon(Icons.Rounded.CheckCircle, null) },
            text = { Text(if (adding) "Füge hinzu …" else "Füge ${chosen.size} gefundene Apps hinzu") },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
        )
    }
}

@Composable
private fun Counter(n: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.titleLarge) }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HitRow(hit: ImportHit, name: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box {
            AppIcon(hit.pkg, 44)
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) { SourceBadge(hit.source, 18) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${hit.pkg} · ${hit.release.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = checked, onCheckedChange = onCheck)
    }
}
