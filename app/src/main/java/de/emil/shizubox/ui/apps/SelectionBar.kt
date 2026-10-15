@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.apps

import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.AppEntry
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

/** Batch actions for the apps selected with a long press. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionBar(
    selection: Set<String>,
    apps: List<AppEntry>,
    visible: List<AppEntry>,
    shizukuReady: Boolean,
    onSelection: (Set<String>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val profiles by Profiles.profiles.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    val chosen = apps.filter { it.pkg in selection }
    val removable = chosen.filter { !it.isSystem && it.installed }

    fun run(block: suspend () -> String?) {
        busy = true
        scope.launch {
            message = runCatching { block() }.getOrElse { "⚠ ${it.message}" }
            busy = false
            onSelection(emptySet())
            onDone()
        }
    }

    GlassCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelection(emptySet()) }) { Icon(Icons.Rounded.Close, t(R.string.select_none)) }
            // The count rolls like the digits of the Clock app.
            AnimatedContent(
                targetState = selection.size,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "selCount",
                modifier = Modifier.weight(1f),
            ) { n -> Text(tp(R.plurals.sel_n, n, n), style = MaterialTheme.typography.titleMedium) }
            IconButton(onClick = { onSelection(selection + visible.map { it.pkg }) }) { Icon(Icons.Rounded.SelectAll, t(R.string.select_all)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enabled = shizukuReady && !busy
            AssistChip(enabled = enabled, onClick = { run { Apps.freeze(selection); t(R.string.sel_done) } }, label = { Text(t(R.string.freeze)) })
            AssistChip(enabled = enabled, onClick = { run { Apps.unfreeze(selection); t(R.string.sel_done) } }, label = { Text(t(R.string.unfreeze)) })
            AssistChip(enabled = enabled, onClick = { run { Apps.forceStop(selection); t(R.string.sel_done) } }, label = { Text(t(R.string.force_stop)) })
            if (removable.isNotEmpty()) {
                AssistChip(enabled = enabled, onClick = { confirmUninstall = true }, label = { Text(t(R.string.sel_uninstall_n, removable.size)) })
            }
            if (profiles.isNotEmpty()) {
                Box {
                    AssistChip(enabled = !busy, onClick = { profileMenu = true }, label = { Text(t(R.string.sel_to_profile)) })
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                profileMenu = false
                                Profiles.addAll(p.id, selection)
                                message = t(R.string.sel_added_profile, p.name)
                                onSelection(emptySet())
                            })
                        }
                    }
                }
            }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("⚠")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(t(R.string.uninstall_q)) },
            text = { Text(t(R.string.sel_uninstall_hint, removable.joinToString { it.label })) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmUninstall = false
                    run {
                        val failed = Apps.uninstall(removable.map { it.pkg })
                        if (failed.isEmpty()) t(R.string.sel_done) else "⚠ " + t(R.string.sel_failed, failed.joinToString())
                    }
                }) { Text(t(R.string.uninstall)) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmUninstall = false }) { Text(t(R.string.cancel)) } },
        )
    }
}
