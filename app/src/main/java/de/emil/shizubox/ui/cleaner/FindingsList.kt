@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.Finding
import de.emil.shizubox.cleaner.MoveResult
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

/** A category of findings; [preselect] = picked by default (safe categories). */
data class FindingGroup(val key: String, val title: String, val subtitle: String, val items: List<Finding>, val preselect: Boolean)

/** Selection of a findings screen, kept by the screen. */
class FindingSelection {
    var selected by mutableStateOf(emptySet<String>())
    var initialised by mutableStateOf(false)
    fun init(groups: List<FindingGroup>) {
        if (initialised || groups.isEmpty()) return
        selected = groups.filter { it.preselect }.flatMap { g -> g.items.map { it.path } }.toSet()
        initialised = true
    }
}

/**
 * Collapsible groups with a tri-state box each, items with check boxes (100 at a time),
 * long press for the entry menu (exclude, open, …).
 */
fun LazyListScope.findingGroups(
    groups: List<FindingGroup>,
    sel: FindingSelection,
    tool: CleanTool,
    onMoved: (List<String>) -> Unit,
) {
    for (g in groups) {
        item(key = "g-${g.key}") {
            val context = LocalContext.current
            var open by remember { mutableStateOf(false) }
            var shown by remember { mutableIntStateOf(100) }
            var menu by remember { mutableStateOf<Finding?>(null) }
            val paths = g.items.map { it.path }
            val chosen = paths.count { it in sel.selected }
            val state = when {
                chosen == 0 -> ToggleableState.Off
                chosen == paths.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { if (g.items.isNotEmpty()) open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(state = state, enabled = g.items.isNotEmpty(), onClick = {
                        sel.selected = if (state == ToggleableState.On) sel.selected - paths.toSet() else sel.selected + paths
                    })
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(g.title, style = MaterialTheme.typography.titleMedium)
                        Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(size(context, g.items.sumOf { it.size }), style = MaterialTheme.typography.titleMedium)
                        Text(tp(R.plurals.cl_trash_n, g.items.size, g.items.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (g.items.isNotEmpty()) Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp))
                }
                AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.items.take(shown).forEach { f ->
                            EntryRow(
                                f.path, f.size, f.dir, f.path in sel.selected, note = f.note.ifEmpty { null }, time = f.mtime,
                                onCheck = { on -> sel.selected = if (on) sel.selected + f.path else sel.selected - f.path },
                                onLongClick = { if (!f.internal) menu = f },
                            )
                        }
                        if (g.items.size > shown) TextButton(onClick = { shown += 100 }, modifier = Modifier.fillMaxWidth()) { Text(t(R.string.cl_more, g.items.size - shown)) }
                    }
                }
            }
            menu?.let { f ->
                EntryMenu(f.path, f.dir, f.size, tool, onDismiss = { menu = null }, onMoved = { r -> if (r.moved > 0) onMoved(listOf(f.path)) })
            }
        }
    }
}

/** Sticky action: "N selected · size → to the trash", with the preview dialog. */
@Composable
fun CleanBar(
    groups: List<FindingGroup>,
    sel: FindingSelection,
    enabled: Boolean,
    extraHint: String? = null,
    clean: suspend (List<Finding>, (Int, Int) -> Unit) -> MoveResult,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val chosen = groups.flatMap { it.items }.filter { it.path in sel.selected }.distinctBy { it.path }
    GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tp(R.plurals.sel_n, chosen.size, chosen.size), style = MaterialTheme.typography.titleMedium)
                Text(size(context, chosen.sumOf { it.size }), style = MaterialTheme.typography.bodyMedium)
            }
            Box {
                Button(shapes = ButtonDefaults.shapes(), enabled = enabled && chosen.isNotEmpty() && busy == null, onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.padding(end = 8.dp))
                    Text(t(R.string.cl_to_trash))
                }
            }
        }
        busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }
    if (confirm) {
        TrashConfirmDialog(chosen.size, chosen.sumOf { it.size }, extra = extraHint, onDismiss = { confirm = false }, onConfirm = {
            confirm = false
            busy = t(R.string.cl_moving)
            scope.launch {
                val r = runCatching { de.emil.shizubox.cleaner.CleanerJobs.background { clean(chosen) { i, n -> busy = t(R.string.cl_moving_n, i, n) } }.await() }
                    .getOrElse { MoveResult(0, 0, listOf("" to (it.message ?: "error"))) }
                busy = null
                message = resultText(context, r)
                sel.selected = sel.selected - chosen.map { it.path }.toSet()
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        })
    }
}
