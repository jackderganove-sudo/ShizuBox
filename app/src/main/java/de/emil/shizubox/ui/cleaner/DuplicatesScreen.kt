@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerConfig
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.cleaner.DupFile
import de.emil.shizubox.cleaner.DupGroup
import de.emil.shizubox.cleaner.Duplicates
import de.emil.shizubox.cleaner.Fs
import de.emil.shizubox.cleaner.KeepRule
import de.emil.shizubox.cleaner.MoveResult
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val thumbs = LruCache<String, Bitmap>(120)

/** Small preview of a picture or video, made off the main thread and cached. */
@Composable
fun Thumb(path: String, sizeDp: Int = 56) {
    val bmp by produceState(thumbs.get(path), path) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                val s = Size(192, 192)
                when (kindOf(path)) {
                    FileKind.Image -> ThumbnailUtils.createImageThumbnail(f, s, null)
                    FileKind.Video -> ThumbnailUtils.createVideoThumbnail(f, s, null)
                    else -> null
                }
            }.getOrNull()?.also { thumbs.put(path, it) }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(sizeDp.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        else Icon(iconOf(path, false), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun scanDuplicates(rule: KeepRule) = CleanerJobs.start(CleanTool.Duplicates) { report -> Duplicates.scan(rule = rule) { text, p -> report(text, p) } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicatesScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by Duplicates.result.collectAsStateWithLifecycle()
    val running by CleanerJobs.running.collectAsStateWithLifecycle()
    val errors by CleanerJobs.errors.collectAsStateWithLifecycle()
    val cfg by CleanerSettings.state.collectAsStateWithLifecycle()
    val job = running[CleanTool.Duplicates]
    var rule by rememberSaveable { mutableStateOf(KeepRule.Camera) }
    var shown by remember { mutableIntStateOf(30) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf("") }
    val list = groups.orEmpty()
    val remove = Duplicates.toRemove(list)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(t(R.string.cl_dupes), onBack) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.du_folders), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (CleanerConfig.DEFAULT_DUPE_FOLDERS + cfg.dupeFolders).sorted().forEach { f ->
                        FilterChip(selected = f in cfg.dupeFolders, onClick = {
                            CleanerSettings.update { s -> s.copy(dupeFolders = if (f in s.dupeFolders) s.dupeFolders - f else s.dupeFolders + f) }
                        }, label = { Text(f.removePrefix("Android/media/").substringAfterLast("/").let { if (f.startsWith("Android/media/")) "$it (${f.removePrefix("Android/media/").substringBefore('/')})" else f } ) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newFolder, { newFolder = it }, label = { Text(t(R.string.du_add_folder)) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = newFolder.isNotBlank(), onClick = {
                        val f = newFolder.trim().trim('/').removePrefix("sdcard/").removePrefix("storage/emulated/0/")
                        CleanerSettings.update { it.copy(dupeFolders = it.dupeFolders + f) }
                        newFolder = ""
                    }) { Icon(Icons.Rounded.Add, t(R.string.cl_add)) }
                }
                Text(t(R.string.du_min_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 100, 1024, 10 * 1024).forEach { kb ->
                        FilterChip(selected = cfg.dupeMinKb == kb, onClick = { CleanerSettings.update { it.copy(dupeMinKb = kb) } }, label = { Text(size(context, kb * 1000L)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t(R.string.du_similar), style = MaterialTheme.typography.bodyMedium)
                        Text(t(R.string.du_similar_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = cfg.dupeSimilar, onCheckedChange = { v -> CleanerSettings.update { it.copy(dupeSimilar = v) } })
                }
                Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady && job == null && cfg.dupeFolders.isNotEmpty(), onClick = { shown = 30; scanDuplicates(rule) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Search, null, Modifier.padding(end = 8.dp))
                    Text(t(if (groups == null) R.string.du_scan else R.string.cl_rescan))
                }
            }
        }
        if (job != null) item { JobCard(job.text) { CleanerJobs.cancel(CleanTool.Duplicates) } }
        errors[CleanTool.Duplicates]?.let { e -> item { Hint("⚠ $e") } }
        if (groups != null && list.isEmpty() && job == null) item { EmptyState(Icons.Rounded.ContentCopy, t(R.string.du_none)) }
        if (list.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer, contentPadding = 12.dp) {
                    Text(tp(R.plurals.du_groups, list.size, list.size, size(context, list.sumOf { it.waste })), style = MaterialTheme.typography.titleMedium)
                    Text(t(R.string.du_keep_rule), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(KeepRule.Camera to R.string.du_rule_camera, KeepRule.Oldest to R.string.du_rule_oldest, KeepRule.Resolution to R.string.du_rule_res).forEach { (r, res) ->
                            FilterChip(selected = rule == r, onClick = { rule = r; Duplicates.applyRule(r) }, label = { Text(t(res)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(tp(R.plurals.du_remove_n, remove.size, remove.size, size(context, remove.sumOf { it.size })), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady && remove.isNotEmpty() && busy == null, onClick = { confirm = true }) { Text(t(R.string.cl_to_trash)) }
                    }
                    busy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(t(R.string.du_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            itemsIndexed(list.take(shown), key = { _, g -> g.id }) { _, g ->
                GroupCard(g) { updated -> Duplicates.update(list.map { if (it.id == g.id) updated else it }) }
            }
            if (list.size > shown) item { TextButton(onClick = { shown += 30 }, modifier = Modifier.fillMaxWidth()) { Text(t(R.string.cl_more, list.size - shown)) } }
        }
    }
    if (confirm) {
        TrashConfirmDialog(remove.size, remove.sumOf { it.size }, extra = t(R.string.du_confirm_extra), onDismiss = { confirm = false }, onConfirm = {
            confirm = false
            busy = t(R.string.cl_moving)
            val files = remove
            scope.launch {
                val r = runCatching { CleanerJobs.background { Duplicates.clean(files) { i, n -> busy = t(R.string.cl_moving_n, i, n) } }.await() }
                    .getOrElse { MoveResult(0, 0, listOf("" to (it.message ?: "error"))) }
                busy = null
                message = resultText(context, r)
            }
        })
    }
}

@Composable
private fun GroupCard(g: DupGroup, onChange: (DupGroup) -> Unit) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(
            (if (g.similar) t(R.string.du_similar_group) else t(R.string.du_exact_group)) + " · " + tp(R.plurals.sa_files, g.files.size, g.files.size) +
                " · " + size(context, g.files.first().size),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
        g.files.forEach { f ->
            val keep = f.path in g.keep
            var menu by remember { mutableStateOf(false) }
            if (menu) EntryMenu(f.path, false, f.size, CleanTool.Duplicates, onDismiss = { menu = false }, onMoved = { r -> if (r.moved > 0) Duplicates.forget(listOf(f.path)) })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp).pointerInput(f.path) { detectTapGestures(onLongPress = { menu = true }) },
            ) {
                Thumb(f.path)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (keep) Icon(Icons.Rounded.Star, t(R.string.du_keep), Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(f.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        listOfNotNull(Fs.rel(f.path.substringBeforeLast('/')), ago(f.mtime), f.width.takeIf { it > 0 }?.let { "${f.width}×${f.height}" }, if (g.similar) size(context, f.size) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                // Checked = goes to the trash. The last kept file of a group cannot be unchecked away.
                Checkbox(checked = !keep, onCheckedChange = { remove ->
                    val keepNew = if (remove) g.keep - f.path else g.keep + f.path
                    if (keepNew.isEmpty()) Toast.makeText(context, t(R.string.du_keep_one), Toast.LENGTH_SHORT).show()
                    else onChange(g.copy(keep = keepNew))
                })
            }
        }
    }
}
