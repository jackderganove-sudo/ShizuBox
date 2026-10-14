package de.emil.shizubox.ui.updates

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.apps.AppEntry
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.launch

/** "Geräte-Apps übernehmen": pick installed apps, find them on package-based sources. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportInstalledScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { all = Apps.list().filter { it.installed } }
    var system by remember { mutableStateOf(false) }
    var skipTracked by remember { mutableStateOf(true) }
    var sources by remember { mutableStateOf(Updates.PACKAGE_SOURCES.keys.toSet()) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var progress by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    val tracked = remember(all) { Updates.apps.value.map { it.id }.toSet() }
    val visible = all.filter { it.isSystem == system }
        .filter { !skipTracked || it.pkg !in tracked }
        .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                IconButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                Text("Apps auswählen", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !system, onClick = { system = false }, label = { Text("Benutzer-Apps") })
                    FilterChip(selected = system, onClick = { system = true }, label = { Text("System-Apps") })
                    FilterChip(selected = skipTracked, onClick = { skipTracked = !skipTracked }, label = { Text("Schon verfolgte überspringen") })
                }
                Text("Suchen in:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Updates.PACKAGE_SOURCES.keys.forEach { s ->
                        FilterChip(selected = s in sources, onClick = { sources = if (s in sources) sources - s else sources + s }, label = { Text(s) })
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Suchen") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ausgewählt: ${chosen.size}/${visible.size}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { chosen = chosen + visible.map { it.pkg } }) { Text("Alle") }
                    TextButton(onClick = { chosen = emptySet() }) { Text("Keine") }
                }
            }
            items(visible, key = { it.pkg }) { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    AppIcon(app.pkg, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(checked = app.pkg in chosen, onCheckedChange = { chosen = if (it) chosen + app.pkg else chosen - app.pkg })
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            progress?.let { (i, pkg) ->
                Text("Suche $pkg … (${i + 1}/${chosen.size})", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { (i + 1f) / chosen.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            Button(
                enabled = chosen.isNotEmpty() && sources.isNotEmpty() && progress == null,
                onClick = {
                    scope.launch {
                        val list = chosen.toList()
                        val added = Updates.importInstalled(list, sources) { i, p -> progress = i to p }
                        progress = null
                        result = "$added von ${list.size} Apps gefunden und hinzugefügt"
                        chosen = emptySet()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("${chosen.size} Apps suchen und hinzufügen") }
        }
    }
}
