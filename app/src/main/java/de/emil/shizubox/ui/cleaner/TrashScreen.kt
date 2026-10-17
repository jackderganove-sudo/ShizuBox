@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.cleaner.Fs
import de.emil.shizubox.cleaner.LogAction
import de.emil.shizubox.cleaner.LogEntry
import de.emil.shizubox.cleaner.Trash
import de.emil.shizubox.cleaner.TrashItem
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

private const val PAGE = 100

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrashScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by Trash.items.collectAsStateWithLifecycle()
    val cfg by CleanerSettings.state.collectAsStateWithLifecycle()
    var showLog by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmPurge by remember { mutableStateOf<Collection<Long>?>(null) }
    var shown by remember { mutableIntStateOf(PAGE) }
    LaunchedEffect(shizukuReady) { if (shizukuReady) runCatching { Trash.reconcile() } }
    val total = items.sumOf { it.size }
    // Tight on space: the trash still occupies it until it is emptied.
    val free = remember(items.size) { runCatching { StatFs(Fs.ROOT).availableBytes }.getOrDefault(Long.MAX_VALUE) }
    val low = free < 5L * 1024 * 1024 * 1024 && total > 0
    val listStart = rememberListStart(showLog)

    fun run(block: suspend () -> String) {
        busy = true
        scope.launch {
            message = runCatching { block() }.getOrElse { "⚠ ${it.message}" }
            busy = false
            selection = emptySet()
        }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(t(R.string.cl_trash), onBack) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !showLog, onClick = { showLog = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(t(R.string.cl_trash)) }
                SegmentedButton(selected = showLog, onClick = { showLog = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(t(R.string.cl_log)) }
            }
        }
        if (showLog) {
            logItems(this)
            return@LazyColumn
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                RollingText(size(context, total), MaterialTheme.typography.displaySmall)
                Text(tp(R.plurals.cl_trash_n, items.size, items.size) + " · " + tp(R.plurals.cl_trash_keep, cfg.trashDays, cfg.trashDays),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (low) Text(t(R.string.cl_trash_low), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                if (busy) {
                    LoadingIndicator(Modifier.padding(top = 8.dp))
                } else if (items.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = { confirmPurge = items.map { it.id } }) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.padding(end = 8.dp))
                            Text(t(R.string.cl_trash_empty))
                        }
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = {
                            run { val n = Trash.restore(items.map { it.id }); tp(R.plurals.cl_restored, n, n) }
                        }) { Text(t(R.string.cl_restore_all)) }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                Text(t(R.string.cl_trash_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (selection.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Rounded.Close, t(R.string.select_none)) }
                        Text(tp(R.plurals.sel_n, selection.size, selection.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selection = items.map { it.id }.toSet() }) { Icon(Icons.Rounded.SelectAll, t(R.string.select_all)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(enabled = shizukuReady && !busy, onClick = {
                            val ids = selection
                            run { val n = Trash.restore(ids); tp(R.plurals.cl_restored, n, n) }
                        }, label = { Text(t(R.string.cl_restore)) })
                        AssistChip(enabled = shizukuReady && !busy, onClick = { confirmPurge = selection }, label = { Text(t(R.string.cl_delete_forever)) })
                    }
                }
            }
        }
        if (items.isEmpty()) item { EmptyState(Icons.Rounded.DeleteSweep, t(R.string.cl_trash_empty_title), t(R.string.cl_trash_empty_hint)) }
        itemsIndexed(items.take(shown), key = { _, it -> it.id }) { i, it ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                TrashRow(it, cfg.trashDays, it.id in selection, selection.isNotEmpty(),
                    onToggle = { on -> selection = if (on) selection + it.id else selection - it.id },
                    onRestore = { run { val n = Trash.restore(listOf(it.id)); tp(R.plurals.cl_restored, n, n) } },
                )
            }
        }
        if (items.size > shown) item {
            TextButton(onClick = { shown += PAGE }, modifier = Modifier.fillMaxWidth()) { Text(t(R.string.cl_more, items.size - shown)) }
        }
    }
    confirmPurge?.let { ids ->
        val chosen = items.filter { it.id in ids }
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text(t(R.string.cl_purge_q)) },
            text = { Text(tp(R.plurals.cl_purge_body, chosen.size, chosen.size, size(context, chosen.sumOf { it.size }))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurge = null
                    run { val freed = Trash.purge(ids); t(R.string.cl_freed, size(context, freed)) }
                }) { Text(t(R.string.cl_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text(t(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, days: Int, selected: Boolean, selecting: Boolean, onToggle: (Boolean) -> Unit, onRestore: () -> Unit) {
    val left = kotlin.math.ceil((item.time + days * 86_400_000L - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)
    val path = item.orig.removePrefix(Trash.INTERNAL)
    EntryRow(
        path = path, bytes = item.size, dir = item.dir, checked = if (selecting) selected else null,
        note = item.tool.label + " · " + ago(item.time) + " · " + tp(R.plurals.cl_days_left, left, left),
        onCheck = onToggle,
        onClick = if (selecting) ({ onToggle(!selected) }) else null,
        onLongClick = { onToggle(!selected) },
        trailing = {
            IconButton(onClick = onRestore) { Icon(Icons.Rounded.Restore, t(R.string.cl_restore), tint = MaterialTheme.colorScheme.primary) }
        },
    )
}

private fun logItems(scope: androidx.compose.foundation.lazy.LazyListScope) = scope.item {
    val context = LocalContext.current
    val entries by produceState(emptyList<LogEntry>()) { value = Trash.logEntries() }
    if (entries.isEmpty()) {
        EmptyState(Icons.Rounded.DeleteSweep, t(R.string.cl_log_empty))
        return@item
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        entries.forEach { e ->
            val label = when (e.action) {
                LogAction.Trash -> t(R.string.cl_log_trash)
                LogAction.Restore -> t(R.string.cl_log_restore)
                LogAction.Delete -> t(R.string.cl_log_delete)
                LogAction.Expired -> t(R.string.cl_log_expired)
                LogAction.Blocked -> t(R.string.cl_log_blocked)
                LogAction.Failed -> t(R.string.cl_log_failed)
            }
            Text(
                "$label · ${size(context, e.size)} · ${ago(e.time)}" + (e.tool?.let { " · " + it.label } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = if (e.action == LogAction.Failed || e.action == LogAction.Blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(Fs.rel(e.path.removePrefix(Trash.INTERNAL)) + if (e.msg.isNotEmpty()) "\n" + e.msg else "", style = MaterialTheme.typography.bodySmall)
        }
    }
}
