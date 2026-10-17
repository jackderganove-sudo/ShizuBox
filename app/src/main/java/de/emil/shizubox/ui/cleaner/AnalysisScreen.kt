@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.Analysis
import de.emil.shizubox.cleaner.AppStorage
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.cleaner.Fs
import de.emil.shizubox.cleaner.FsEntry
import de.emil.shizubox.cleaner.StorageAnalysis
import de.emil.shizubox.cleaner.StorageCat
import de.emil.shizubox.t
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import de.emil.shizubox.ui.theme.LocalDarkTheme

/**
 * Categorical slots of the validated reference palette (dataviz skill, fixed order = CVD-safe
 * adjacent pairs; light and dark steps chosen for their surfaces). Part-to-whole → stacked bar.
 */
private val LIGHT = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948)
private val DARK = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767)

@Composable
fun catColor(c: StorageCat): Color = Color((if (LocalDarkTheme.current) DARK else LIGHT)[c.ordinal])

fun scanAnalysis(context: android.content.Context) = CleanerJobs.start(CleanTool.Analysis) { report ->
    StorageAnalysis.scan(context) { report(it, null) }
}

@Composable
fun AnalysisScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val result by StorageAnalysis.result.collectAsStateWithLifecycle()
    val running by CleanerJobs.running.collectAsStateWithLifecycle()
    val errors by CleanerJobs.errors.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(CleanerNav.tab.also { CleanerNav.tab = 0 }) }
    var folder by rememberSaveable { mutableStateOf(Fs.ROOT) }
    var menu by remember { mutableStateOf<FsEntry?>(null) }
    val job = running[CleanTool.Analysis]
    LaunchedEffect(shizukuReady) { if (shizukuReady && result == null && job == null) scanAnalysis(context) }
    BackHandler(tab == 1 && folder != Fs.ROOT) { folder = folder.substringBeforeLast('/') }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(t(R.string.cl_analysis), onBack) {
                IconButton(enabled = shizukuReady && job == null, onClick = { scanAnalysis(context) }) { Icon(Icons.Rounded.Refresh, t(R.string.cl_rescan)) }
            }
        }
        if (job != null) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator()
                    Text(job.text, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    TextButton(onClick = { CleanerJobs.cancel(CleanTool.Analysis) }) { Text(t(R.string.cancel)) }
                }
            }
        }
        errors[CleanTool.Analysis]?.let { e -> item { Hint("⚠ $e") } }
        val a = result
        if (a == null) {
            if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
            return@LazyColumn
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(R.string.sa_tab_overview, R.string.sa_tab_folders, R.string.sa_tab_largest, R.string.sa_tab_apps).forEachIndexed { i, res ->
                    SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 4)) {
                        Text(t(res), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        when (tab) {
            0 -> overview(a, onApps = { tab = 3 }, onFolder = { folder = it; tab = 1 })
            1 -> folderItems(a, folder, shizukuReady, onOpen = { folder = it }, onMenu = { menu = it })
            2 -> {
                item { Hint(t(R.string.sa_largest_hint)) }
                itemsIndexed(a.top, key = { _, e -> e.path }) { _, e ->
                    EntryRow(e.path, e.size, false, null, time = e.mtime, onClick = { menu = e }, onLongClick = { menu = e })
                }
            }
            else -> appItems(a)
        }
    }
    menu?.let { e ->
        EntryMenu(e.path, e.dir, if (e.dir) result?.dirSizes?.get(e.path) ?: e.size else e.size, CleanTool.Analysis,
            onDismiss = { menu = null },
            onShowInFolder = { folder = it; tab = 1 },
            onMoved = { r -> if (r.moved > 0) StorageAnalysis.forget(listOf(e.path), mapOf(e.path to r.bytes)) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overview(a: Analysis, onApps: () -> Unit, onFolder: (String) -> Unit) {
    item {
        val context = LocalContext.current
        var picked by remember { mutableStateOf<StorageCat?>(null) }
        val total = a.categories.values.sum().coerceAtLeast(1)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(t(R.string.sa_used_by), style = MaterialTheme.typography.titleMedium)
            RollingText(size(context, a.numbers.used), MaterialTheme.typography.displaySmall)
            Text(
                picked?.let { "${it.label} · ${size(context, a.categories.getValue(it))} · ${percent(a.categories.getValue(it), total)}" } ?: t(R.string.sa_tap_hint),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
            StackedBar(a, picked) { picked = it }
            // Legend = table view: colour, name, size, share. Text stays in text colours.
            StorageCat.entries.forEach { c ->
                val v = a.categories.getValue(c)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).pointerInput(c) {
                        detectTapGestures {
                            when (c) {
                                StorageCat.Apps -> onApps()
                                StorageCat.Downloads -> onFolder(Fs.ROOT + "/Download")
                                else -> picked = c
                            }
                        }
                    },
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(catColor(c)))
                    Text(c.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text(percent(v, total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    Text(size(context, v), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(t(R.string.sa_system_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Text(t(R.string.sa_scanned, a.files, ago(a.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun percent(v: Long, total: Long) = "%.1f %%".format(v * 100.0 / total)

/** One horizontal stacked bar, segments in palette order with 2 dp gaps, grows in from the left; tap shows a value. */
@Composable
private fun StackedBar(a: Analysis, picked: StorageCat?, onPick: (StorageCat) -> Unit) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(a.time) { grow.snapTo(0f); grow.animateTo(1f, tween(700)) }
    val cats = StorageCat.entries
    val colors = cats.map { catColor(it) }
    val total = a.categories.values.sum().coerceAtLeast(1).toFloat()
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp).pointerInput(a.time) {
            detectTapGestures { pos ->
                var x = 0f
                for (c in cats) {
                    val w = a.categories.getValue(c) / total * size.width
                    if (pos.x in x..(x + w)) { onPick(c); break }
                    x += w
                }
            }
        },
    ) {
        val gap = 2.dp.toPx()
        var x = 0f
        cats.forEachIndexed { i, c ->
            val w = a.categories.getValue(c) / total * size.width * grow.value
            if (w > gap) {
                drawRoundRect(
                    colors[i], topLeft = Offset(x, 0f), size = Size(w - gap, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx()), alpha = if (picked == null || picked == c) 1f else 0.4f,
                )
            }
            x += w
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.folderItems(
    a: Analysis, folder: String, shizukuReady: Boolean, onOpen: (String) -> Unit, onMenu: (FsEntry) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (folder != Fs.ROOT) TextButton(onClick = { onOpen(folder.substringBeforeLast('/')) }) { Text("‹ " + t(R.string.sa_up)) }
            Text("/" + Fs.rel(folder).removePrefix("/"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(size(LocalContext.current, a.dirSizes[folder] ?: 0), style = MaterialTheme.typography.titleSmall)
        }
    }
    item(key = "list-$folder") {
        val children by produceState<List<FsEntry>?>(null, folder, a.dirSizes) {
            value = if (!shizukuReady) emptyList() else runCatching { Fs.list(folder) }.getOrDefault(emptyList())
                .map { if (it.dir) it.copy(size = a.dirSizes[it.path] ?: 0) else it }
                .sortedByDescending { it.size }
        }
        val list = children
        if (list == null) {
            LoadingIndicator(Modifier.padding(16.dp))
            return@item
        }
        if (list.isEmpty()) {
            EmptyState(Icons.Rounded.Storage, t(R.string.sa_empty_folder))
            return@item
        }
        val max = list.maxOf { it.size }.coerceAtLeast(1)
        val listStart = rememberListStart(folder)
        var shown by remember(folder) { mutableIntStateOf(100) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.take(shown).forEachIndexed { i, e ->
                Box(Modifier.enterStagger(i, listStart)) {
                    Column {
                        EntryRow(
                            e.path, e.size, e.dir, null, time = e.mtime, showParent = false,
                            onClick = { if (e.dir) onOpen(e.path) else onMenu(e) },
                            onLongClick = { onMenu(e) },
                        )
                        LinearProgressIndicator(progress = { e.size.toFloat() / max }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp), drawStopIndicator = {})
                    }
                }
            }
            if (list.size > shown) TextButton(onClick = { shown += 100 }, modifier = Modifier.fillMaxWidth()) { Text(t(R.string.cl_more, list.size - shown)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appItems(a: Analysis) {
    item { Hint(t(R.string.sa_apps_hint)) }
    itemsIndexed(a.apps.take(200), key = { _, it -> it.pkg }) { i, app ->
        val listStart = rememberListStart(Unit)
        Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) { AppRow(app) }
    }
}

@Composable
private fun AppRow(app: AppStorage) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildList {
                        add(t(R.string.sa_apk, size(context, app.apk - app.obb)))
                        add(t(R.string.sa_data, size(context, app.data - app.cache - app.extData)))
                        add(t(R.string.sa_cache, size(context, app.cache)))
                        if (app.extData > 0) add("Android/data " + size(context, app.extData))
                        if (app.obb > 0) add("obb " + size(context, app.obb))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(size(context, app.total), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
