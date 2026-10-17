@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.cleaner.SysCat
import de.emil.shizubox.cleaner.SystemCleaner
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard

fun SysCat.title() = t(when (this) {
    SysCat.Thumbnails -> R.string.sc_thumbs
    SysCat.EmptyDirs -> R.string.sc_empty
    SysCat.LogsTemp -> R.string.sc_logs
    SysCat.Partial -> R.string.sc_partial
    SysCat.OwnLeftovers -> R.string.sc_own
    SysCat.OldApks -> R.string.sc_apks
    SysCat.ForeignTrash -> R.string.sc_trash
})

fun SysCat.subtitle() = t(when (this) {
    SysCat.Thumbnails -> R.string.sc_thumbs_sub
    SysCat.EmptyDirs -> R.string.sc_empty_sub
    SysCat.LogsTemp -> R.string.sc_logs_sub
    SysCat.Partial -> R.string.sc_partial_sub
    SysCat.OwnLeftovers -> R.string.sc_own_sub
    SysCat.OldApks -> R.string.sc_apks_sub
    SysCat.ForeignTrash -> R.string.sc_trash_sub
})

fun scanSystem(context: android.content.Context) = CleanerJobs.start(CleanTool.System) { report ->
    val cats = CleanerSettings.current.sysCats.mapNotNull { runCatching { SysCat.valueOf(it) }.getOrNull() }.toSet()
    SystemCleaner.scan(context, cats) { report(it, null) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemCleanerScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val result by SystemCleaner.result.collectAsStateWithLifecycle()
    val running by CleanerJobs.running.collectAsStateWithLifecycle()
    val errors by CleanerJobs.errors.collectAsStateWithLifecycle()
    val cfg by CleanerSettings.state.collectAsStateWithLifecycle()
    val job = running[CleanTool.System]
    val sel = remember { FindingSelection() }
    LaunchedEffect(shizukuReady) { if (shizukuReady && result == null && job == null) scanSystem(context) }
    val groups = result?.let { r ->
        SysCat.entries.filter { it in r }.map { c -> FindingGroup(c.name, c.title(), c.subtitle(), r.getValue(c), c.safe) }
    }.orEmpty()
    LaunchedEffect(groups) { sel.init(groups) }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(t(R.string.cl_system), onBack) {
                IconButton(enabled = shizukuReady && job == null, onClick = { sel.initialised = false; scanSystem(context) }) { Icon(Icons.Rounded.Refresh, t(R.string.cl_rescan)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Text(t(R.string.sc_cats), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SysCat.entries.forEach { c ->
                        FilterChip(selected = c.name in cfg.sysCats, onClick = {
                            CleanerSettings.update { s -> s.copy(sysCats = if (c.name in s.sysCats) s.sysCats - c.name else s.sysCats + c.name) }
                        }, label = { Text(c.title()) })
                    }
                }
            }
        }
        if (job != null) item { JobCard(job.text) { CleanerJobs.cancel(CleanTool.System) } }
        errors[CleanTool.System]?.let { e -> item { Hint("⚠ $e") } }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        if (groups.isNotEmpty()) {
            item { CleanBar(groups, sel, shizukuReady) { list, progress -> SystemCleaner.clean(list, CleanTool.System, progress) } }
            findingGroups(groups, sel, CleanTool.System) { SystemCleaner.clear(it) }
            item { Hint(t(R.string.sc_hint), Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
fun JobCard(text: String, onCancel: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator()
            Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(text) }
            TextButton(onClick = onCancel) { Text(t(R.string.cancel)) }
        }
    }
}
