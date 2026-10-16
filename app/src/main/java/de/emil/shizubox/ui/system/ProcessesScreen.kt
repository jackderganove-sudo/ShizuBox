@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import de.emil.shizubox.ui.motion.rememberListStart
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.Motion
import androidx.compose.foundation.lazy.itemsIndexed
import android.text.format.Formatter
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.emil.shizubox.R
import de.emil.shizubox.system.ProcessSnapshot
import de.emil.shizubox.system.Processes
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

/** One app (all its processes summed up) or a single native process. */
private data class ProcRow(val key: String, val label: String, val pkg: String?, val userId: Int, val pssKb: Long, val group: String, val count: Int)

@Composable
fun ProcessesScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var snap by remember { mutableStateOf<ProcessSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var appsOnly by rememberSaveable { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    val labels = remember { java.util.concurrent.ConcurrentHashMap<String, String>() }
    LaunchedEffect(reload, shizukuReady) {
        if (!shizukuReady) return@LaunchedEffect
        loading = true
        val s = runCatching { Processes.snapshot() }.getOrNull()
        // App names come from the APKs (disk): look them up here, not while drawing the list.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            s?.processes?.mapNotNull { it.pkg }?.distinct()?.forEach { p ->
                if (!labels.containsKey(p)) runCatching { pm.getApplicationInfo(p, 0).loadLabel(pm).toString() }.getOrNull()?.let { labels[p] = it }
            }
        }
        snap = s
        loading = false
    }

    fun appLabel(pkg: String?): String? = pkg?.let { labels[it] }

    val rows = remember(snap, appsOnly) {
        val list = snap?.processes.orEmpty()
        val apps = list.filter { appLabel(it.pkg) != null }
            .groupBy { it.pkg!! to it.userId }
            .map { (k, procs) ->
                ProcRow("${k.first}@${k.second}", appLabel(k.first)!!, k.first, k.second, procs.sumOf { it.pssKb }, procs.maxBy { it.pssKb }.group, procs.size)
            }
        val native = if (appsOnly) emptyList() else list.filter { appLabel(it.pkg) == null }
            .map { ProcRow("pid${it.pid}", it.name, null, it.userId, it.pssKb, it.group, 1) }
        (apps + native).sortedByDescending { it.pssKb }
    }
    val maxKb = rows.maxOfOrNull { it.pssKb } ?: 1L
    val prListStart = rememberListStart(snap != null)
    fun size(kb: Long) = Formatter.formatShortFileSize(context, kb * 1024)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.pr_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { reload++ }, enabled = !loading) { Icon(Icons.Rounded.Refresh, t(R.string.refresh)) }
            }
        }
        if (!shizukuReady) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
            return@LazyColumn
        }
        snap?.summary?.let { s ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row {
                        Text("RAM", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(t(R.string.pr_used_of, size(s.usedKb), size(s.totalKb)), style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(progress = { s.usedKb.toFloat() / s.totalKb }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text(t(R.string.pr_free, size(s.freeKb)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                            scope.launch {
                                runCatching { Processes.killBackground() }
                                message = t(R.string.pr_killed)
                                reload++
                            }
                        }) { Text(t(R.string.pr_kill_bg)) }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = appsOnly, onClick = { appsOnly = true }, label = { Text(t(R.string.apps)) }) }
                item { FilterChip(selected = !appsOnly, onClick = { appsOnly = false }, label = { Text(t(R.string.pr_all_procs)) }) }
            }
        }
        if (loading && snap == null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(t(R.string.pr_reading))
                }
            }
        }
        itemsIndexed(rows, key = { _, r -> r.key }) { index, r ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(index, prListStart)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = remember(r.pkg) {
                        r.pkg?.let { runCatching { pm.getApplicationIcon(it).toBitmap(72, 72).asImageBitmap() }.getOrNull() }
                    }
                    Box(Modifier.size(36.dp)) {
                        if (icon != null) Image(icon, null, Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                size(r.pssKb),
                                de.emil.shizubox.system.groupLabel(r.group),
                                if (r.count > 1) t(R.string.pr_n_procs, r.count) else null,
                                if (r.userId != 0) t(R.string.pr_user, r.userId) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(progress = { r.pssKb.toFloat() / maxKb }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    val stoppable = r.pkg != null && r.pkg != context.packageName &&
                        runCatching { pm.getApplicationInfo(r.pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_PERSISTENT == 0 }.getOrDefault(false)
                    if (stoppable) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { Processes.forceStop(r.pkg!!, r.userId) }
                                message = t(R.string.pr_stopped, r.label)
                                reload++
                            }
                        }) { Icon(Icons.Rounded.Close, t(R.string.force_stop)) }
                    }
                }
            }
            }
        }
    }
}

