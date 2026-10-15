package de.emil.shizubox.ui.downloader

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.downloader.DlStorage
import de.emil.shizubox.downloader.DownloadQueue
import de.emil.shizubox.downloader.DownloadTask
import de.emil.shizubox.downloader.DownloaderSettings
import de.emil.shizubox.downloader.FormatChoice
import de.emil.shizubox.downloader.MediaKind
import de.emil.shizubox.downloader.TaskState
import de.emil.shizubox.downloader.Ytdl
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

// ------------------------------------------------------------------ Downloads

@Composable
fun DownloadsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val tasks by DownloadQueue.tasks.collectAsStateWithLifecycle()
    val settings by DownloaderSettings.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 video, 2 audio
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 added, 1 name, 2 size
    var sortMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DownloadTask?>(null) }
    val done = remember(tasks, query, filter, sort) {
        tasks.filter { it.state == TaskState.DONE }
            .filter { filter == 0 || (filter == 2) == it.isAudio }
            .filter { query.isBlank() || it.title.contains(query, true) || it.uploader?.contains(query, true) == true }
            .let { l ->
                when (sort) {
                    1 -> l.sortedBy { it.title.lowercase() }
                    2 -> l.sortedByDescending { it.sizeBytes }
                    else -> l.sortedByDescending { it.finishedAt }
                }
            }
    }
    val listStart = rememberListStart(done.isNotEmpty())
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(t(R.string.dl_nav_downloads), onBack) {
                IconButton(onClick = { runCatching { context.startActivity(DlStorage.folderIntent(settings)) } }) {
                    Icon(Icons.Rounded.Folder, t(R.string.dl_show_folder))
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, t(R.string.upd_sort)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(t(R.string.dl_sort_added), t(R.string.s_name), t(R.string.s_size)).forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { sort = i; sortMenu = false })
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, t(R.string.close)) } },
                placeholder = { Text(t(R.string.search)) }, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text(t(R.string.all)) })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text(t(R.string.dl_video)) }, leadingIcon = { Icon(Icons.Rounded.Videocam, null) })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text(t(R.string.dl_audio)) }, leadingIcon = { Icon(Icons.Rounded.Audiotrack, null) })
            }
        }
        if (done.isEmpty()) item { EmptyState(Icons.Rounded.DownloadDone, t(R.string.dl_none_yet), t(R.string.dl_none_yet_hint)) }
        itemsIndexed(done, key = { _, it -> it.id }) { index, task ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset).enterStagger(index, listStart)) {
                DownloadRow(task, onDelete = { deleting = task })
            }
        }
    }
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t(R.string.dl_delete_q)) },
            text = { Text(task.title) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { DownloadQueue.deleteFile(task.id); deleting = null }) { Text(t(R.string.delete)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { deleting = null }) { Text(t(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onDelete: () -> Unit) {
    val context = LocalContext.current
    fun launch(i: Intent) = runCatching { context.startActivity(i) }.onFailure { Toast.makeText(context, t(R.string.dl_no_app), Toast.LENGTH_SHORT).show() }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { launch(DlStorage.openIntent(task)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                RemoteImage(task.thumbnail, Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), maxWidth = 320)
                Icon(
                    if (task.isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.Videocam, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(3.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        Formatter.formatShortFileSize(context, task.sizeBytes),
                        task.fileName?.substringAfterLast('.')?.uppercase().orEmpty(),
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(task.finishedAt)),
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { launch(DlStorage.openIntent(task)) }) { Icon(Icons.Rounded.PlayArrow, t(R.string.dl_play)) }
            IconButton(onClick = { launch(DlStorage.shareIntent(task)) }) { Icon(Icons.Rounded.Share, t(R.string.share_apk)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, t(R.string.delete)) }
        }
    }
}

// ------------------------------------------------------------------ More

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by DownloaderSettings.state.collectAsStateWithLifecycle()
    val tasks by DownloadQueue.tasks.collectAsStateWithLifecycle()
    var tempSize by remember { mutableLongStateOf(-1L) }
    var ytdlVersion by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasCookies by remember { mutableStateOf(Ytdl.hasCookies()) }
    var convertOpen by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        tempSize = withContext(Dispatchers.IO) { DlStorage.tempSize(context) }
        ytdlVersion = withContext(Dispatchers.IO) { runCatching { Ytdl.ensureInit(); Ytdl.version() }.getOrNull() }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfter(':').ifEmpty { uri.toString() }
        DownloaderSettings.update { it.copy(folderUri = uri.toString(), folderName = name) }
    }
    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                require("\t" in text) { t(R.string.dl_cookies_invalid) }
                Ytdl.setCookies(text)
                hasCookies = true
                t(R.string.dl_cookies_ok)
            }.getOrElse { "⚠ " + it.message }
        }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader(t(R.string.dl_nav_more), onBack) }
        message?.let { m ->
            item {
                Text(m, style = MaterialTheme.typography.bodyMedium, color = if (m.startsWith("⚠")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.dl_folder), style = MaterialTheme.typography.titleMedium)
                Text(settings.folderName ?: DlStorage.DEFAULT_FOLDER, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { folderPicker.launch(null) }) { Text(t(R.string.dl_folder_pick)) }
                    if (settings.folderUri != null) {
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { DownloaderSettings.update { it.copy(folderUri = null, folderName = null) } }) {
                            Text(t(R.string.cust_reset_default))
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.dl_default_format), style = MaterialTheme.typography.titleMedium)
                FormatPicker(settings.defaultFormat) { f -> DownloaderSettings.update { it.copy(defaultFormat = f) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.dl_cookies), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.dl_cookies_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { cookiePicker.launch(arrayOf("text/*", "*/*")) }) { Text(t(R.string.do_import)) }
                    if (hasCookies) {
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { Ytdl.setCookies(null); hasCookies = false }) { Text(t(R.string.remove)) }
                    }
                }
                if (hasCookies) Text(t(R.string.dl_cookies_active), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.dl_convert), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.dl_convert_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(), onClick = { convertOpen = true },
                    enabled = tasks.any { it.state == TaskState.DONE && !it.isAudio },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(t(R.string.dl_convert_pick)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.dl_temp), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (tempSize < 0) t(R.string.loading) else Formatter.formatShortFileSize(context, tempSize),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        // Keep the folders of downloads that are still running or paused.
                        val keep = tasks.filter { it.state != TaskState.DONE && it.state != TaskState.FAILED }.map { it.id }.toSet()
                        DlStorage.clearTemp(context, keep)
                        refresh++
                    },
                    enabled = tempSize > 0,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(t(R.string.term_clear)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("yt-dlp", style = MaterialTheme.typography.titleMedium)
                Text(ytdlVersion ?: t(R.string.loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    enabled = !updating,
                    onClick = {
                        updating = true
                        scope.launch {
                            message = runCatching { Ytdl.update() }.fold(
                                { v -> if (v == null) t(R.string.dl_ytdlp_current) else t(R.string.dl_ytdlp_updated, v) },
                                { "⚠ " + it.message },
                            )
                            updating = false
                            refresh++
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(if (updating) t(R.string.checking) else t(R.string.dl_ytdlp_update)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.dl_subscriptions), style = MaterialTheme.typography.titleMedium)
                if (settings.subscriptions.isEmpty()) {
                    Text(t(R.string.dl_no_subs_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                settings.subscriptions.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        RemoteImage(s.avatar, Modifier.size(36.dp).clip(CircleShape), maxWidth = 120)
                        Spacer(Modifier.width(12.dp))
                        Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { DownloaderSettings.toggleSubscription(s) }) { Text(t(R.string.dl_unsubscribe)) }
                    }
                }
            }
        }
        item {
            Text(
                t(R.string.dl_more_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    if (convertOpen) {
        ConvertDialog(tasks.filter { it.state == TaskState.DONE && !it.isAudio }, onDismiss = { convertOpen = false }) { msg -> message = msg }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvertDialog(videos: List<DownloadTask>, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(videos.firstOrNull()) }
    var format by remember { mutableStateOf("mp3") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(t(R.string.dl_convert)) },
        text = {
            Column {
                videos.take(30).forEach { v ->
                    FilterChip(
                        selected = chosen == v, onClick = { chosen = v },
                        label = { Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf("mp3", "m4a", "opus").forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.uppercase()) })
                    }
                }
                if (busy) Text(t(R.string.dl_converting), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                enabled = !busy && chosen != null,
                onClick = {
                    val v = chosen ?: return@TextButton
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val input = DlStorage.toTemp(context, Uri.parse(v.fileUri), v.fileName ?: "video")
                                val out = File(input.parentFile, input.nameWithoutExtension + "." + format)
                                try {
                                    Ytdl.convertToAudio(input, out, format)
                                    val saved = DlStorage.save(context, out, DownloaderSettings.current)
                                    DownloadQueue.addFinished(v.title, saved, FormatChoice(kind = MediaKind.AUDIO, audioFormat = format))
                                } finally {
                                    input.delete()
                                    out.delete()
                                }
                            }
                        }
                        busy = false
                        onResult(result.fold({ t(R.string.dl_converted) }, { "⚠ " + it.message }))
                        onDismiss()
                    }
                },
            ) { Text(t(R.string.dl_convert_start)) }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = onDismiss) { Text(t(R.string.cancel)) } },
    )
}
