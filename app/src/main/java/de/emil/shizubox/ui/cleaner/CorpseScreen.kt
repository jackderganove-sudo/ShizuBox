@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.cleaner.CorpseFinder
import de.emil.shizubox.cleaner.CorpseZone
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState

fun scanCorpses() = CleanerJobs.start(CleanTool.Corpses) { report -> CorpseFinder.scan { report(it, null) } }

fun CorpseZone.title() = if (this == CorpseZone.Storage) t(R.string.cf_storage) else rel

@Composable
fun CorpseScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val result by CorpseFinder.result.collectAsStateWithLifecycle()
    val running by CleanerJobs.running.collectAsStateWithLifecycle()
    val errors by CleanerJobs.errors.collectAsStateWithLifecycle()
    val job = running[CleanTool.Corpses]
    val sel = remember { FindingSelection() }
    LaunchedEffect(shizukuReady) { if (shizukuReady && result == null && job == null) scanCorpses() }
    val groups = result?.let { r ->
        CorpseZone.entries.filter { r[it].orEmpty().isNotEmpty() }.map { z ->
            FindingGroup(z.name, z.title(), t(if (z == CorpseZone.Storage) R.string.cf_storage_sub else R.string.cf_app_sub), r.getValue(z), true)
        }
    }.orEmpty()
    LaunchedEffect(groups) { sel.init(groups) }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(t(R.string.cl_corpses), onBack) {
                IconButton(enabled = shizukuReady && job == null, onClick = { sel.initialised = false; scanCorpses() }) { Icon(Icons.Rounded.Refresh, t(R.string.cl_rescan)) }
            }
        }
        item { Hint(t(R.string.cf_intro)) }
        if (job != null) item { JobCard(job.text) { CleanerJobs.cancel(CleanTool.Corpses) } }
        errors[CleanTool.Corpses]?.let { e -> item { Hint("⚠ $e") } }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        if (result != null && groups.isEmpty() && job == null) item { EmptyState(Icons.Rounded.TaskAlt, t(R.string.cf_none), t(R.string.cf_none_hint)) }
        if (groups.isNotEmpty()) {
            item { CleanBar(groups, sel, shizukuReady) { list, progress -> CorpseFinder.clean(list, progress) } }
            findingGroups(groups, sel, CleanTool.Corpses) { CorpseFinder.clear(it) }
            item { Hint(t(R.string.cf_hint), Modifier.padding(top = 4.dp)) }
        }
    }
}
