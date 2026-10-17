@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.AppCacheInfo
import de.emil.shizubox.cleaner.AppCleaner
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.cleaner.Guard
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

fun scanAppCaches(context: android.content.Context) = CleanerJobs.start(CleanTool.AppCache) { report ->
    report(t(R.string.ac_progress), null)
    AppCleaner.scan(context)
}

@Composable
fun AppCleanerScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by AppCleaner.result.collectAsStateWithLifecycle()
    val running by CleanerJobs.running.collectAsStateWithLifecycle()
    val errors by CleanerJobs.errors.collectAsStateWithLifecycle()
    val cfg by CleanerSettings.state.collectAsStateWithLifecycle()
    val job = running[CleanTool.AppCache]
    var selected by remember { mutableStateOf<Set<String>?>(null) }
    var stopRunning by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmTrim by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shizukuReady) { if (shizukuReady && list == null && job == null) scanAppCaches(context) }
    val apps = list.orEmpty()
    // Preselected: every app whose Android/data cache may go and that does not run right now.
    LaunchedEffect(apps, cfg) {
        if (selected == null && apps.isNotEmpty()) selected = apps.filter { AppCleaner.blockReason(it) == null && !it.running }.map { it.pkg }.toSet()
    }
    val sel = selected.orEmpty()
    val chosen = apps.filter { it.pkg in sel && AppCleaner.blockReason(it) == null }
    val total = apps.sumOf { it.total }
    val max = apps.maxOfOrNull { it.total } ?: 1L
    val listStart = rememberListStart(list != null)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(t(R.string.cl_appcache), onBack) {
                IconButton(enabled = shizukuReady && job == null, onClick = { selected = null; scanAppCaches(context) }) { Icon(Icons.Rounded.Refresh, t(R.string.cl_rescan)) }
            }
        }
        if (job != null) item { JobCard(job.text) { CleanerJobs.cancel(CleanTool.AppCache) } }
        errors[CleanTool.AppCache]?.let { e -> item { Hint("⚠ $e") } }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        if (list != null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.cc_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingText(size(context, total), MaterialTheme.typography.displaySmall)
                Text(t(R.string.ac_ext_part, size(context, apps.sumOf { it.ext })), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t(R.string.ac_stop_running), style = MaterialTheme.typography.bodyMedium)
                        Text(t(R.string.ac_stop_running_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = stopRunning, onCheckedChange = { stopRunning = it })
                }
                Button(
                    shapes = ButtonDefaults.shapes(), enabled = shizukuReady && chosen.isNotEmpty() && busy == null,
                    onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Rounded.Delete, null, Modifier.padding(end = 8.dp))
                    Text(t(R.string.ac_clean_selected, chosen.size, size(context, chosen.sumOf { it.ext })))
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = shizukuReady && busy == null, onClick = { confirmTrim = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CleaningServices, null, Modifier.padding(end = 8.dp))
                    Text(t(R.string.cc_clean_all))
                }
                busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
                Text(t(R.string.ac_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        itemsIndexed(apps, key = { _, a -> a.pkg }) { i, a ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                AppCacheRow(a, a.pkg in sel, max) { on -> selected = if (on) sel + a.pkg else sel - a.pkg }
            }
        }
    }
    if (confirm) {
        TrashConfirmDialog(chosen.size, chosen.sumOf { it.ext }, extra = t(R.string.ac_confirm_extra), onDismiss = { confirm = false }, onConfirm = {
            confirm = false
            busy = t(R.string.cl_moving)
            val stop = stopRunning
            scope.launch {
                val r = runCatching { CleanerJobs.background { AppCleaner.clean(chosen, stop) { i, n -> busy = t(R.string.cl_moving_n, i, n) } }.await() }
                    .getOrElse { de.emil.shizubox.cleaner.MoveResult(0, 0, listOf("" to (it.message ?: "error"))) }
                busy = null
                message = resultText(context, r)
                selected = sel - chosen.map { it.pkg }.toSet()
            }
        })
    }
    if (confirmTrim) {
        AlertDialog(
            onDismissRequest = { confirmTrim = false },
            title = { Text(t(R.string.cc_clean_all_q)) },
            text = { Text(t(R.string.cc_clean_all_body, size(context, total)) + "\n\n" + t(R.string.ac_trim_note)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTrim = false
                    busy = t(R.string.cc_cleaning)
                    scope.launch {
                        val freed = runCatching { CleanerJobs.background { AppCleaner.trimAll(context) }.await() }.getOrDefault(0L)
                        busy = null
                        message = t(R.string.cl_freed, size(context, freed))
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        selected = null
                        scanAppCaches(context)
                    }
                }) { Text(t(R.string.cc_clean_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmTrim = false }) { Text(t(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AppCacheRow(a: AppCacheInfo, checked: Boolean, max: Long, onCheck: (Boolean) -> Unit) {
    val context = LocalContext.current
    val block = AppCleaner.blockReason(a)
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.pkg, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(size(context, a.total), style = MaterialTheme.typography.bodyLarge)
                }
                LinearProgressIndicator(progress = { a.total.toFloat() / max }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                val notes = buildList {
                    if (a.ext > 0) add(t(R.string.ac_ext, size(context, a.ext)))
                    if (a.running) add(t(R.string.ac_running))
                    if (a.pkg == Guard.AICORE) add(t(R.string.cc_heavy_one))
                    if (!block.isNullOrEmpty() && a.pkg != Guard.AICORE) add(block)
                    if (a.pkg == Guard.SELF) add(t(R.string.ac_self))
                }
                if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            // Only the Android/data part can be picked; apps without one have nothing to select.
            if (block != "") Checkbox(checked = checked && block == null, enabled = block == null && a.pkg != Guard.SELF, onCheckedChange = onCheck)
        }
    }
}
