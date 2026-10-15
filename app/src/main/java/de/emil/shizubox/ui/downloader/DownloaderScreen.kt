package de.emil.shizubox.ui.downloader

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.downloader.ChannelItem
import de.emil.shizubox.downloader.Discover
import de.emil.shizubox.downloader.DownloadQueue
import de.emil.shizubox.downloader.DownloadService
import de.emil.shizubox.downloader.DownloadTask
import de.emil.shizubox.downloader.DownloaderSettings
import de.emil.shizubox.downloader.MediaEntry
import de.emil.shizubox.downloader.Subscription
import de.emil.shizubox.downloader.TaskState
import de.emil.shizubox.downloader.VideoItem
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.animatedProgress
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import de.emil.shizubox.ui.motion.slideFade
import de.emil.shizubox.ui.updates.PillNav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Page requested from outside (e.g. the progress notification opens the queue). */
object DownloaderNav {
    val page = MutableStateFlow<Int?>(null)
}

@Composable
fun DownloaderScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val requested by DownloaderNav.page.collectAsStateWithLifecycle()
    LaunchedEffect(requested) { requested?.let { page = it; DownloaderNav.page.value = null } }
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    val context = LocalContext.current
    // Downloads left over from a killed process continue as soon as the section is opened.
    LaunchedEffect(Unit) { if (DownloadQueue.active) DownloadService.start(context) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = page, transitionSpec = { slideFade(targetState > initialState) }, label = "dlPage") { p ->
            when (p) {
                0 -> StartPage(onBack, onDownload = { sheet = it })
                1 -> DownloadsPage(onBack)
                2 -> QueuePage(onBack)
                else -> MorePage(onBack)
            }
        }
        PillNav(
            listOf(
                Icons.Rounded.Home to t(R.string.dl_nav_start),
                Icons.Rounded.DownloadDone to t(R.string.dl_nav_downloads),
                Icons.AutoMirrored.Rounded.QueueMusic to t(R.string.dl_nav_queue),
                Icons.Rounded.MoreHoriz to t(R.string.dl_nav_more),
            ),
            selected = page,
            onSelect = { page = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
    }
    sheet?.let { target ->
        DownloadSheet(target, onDismiss = { sheet = null }, onQueued = { sheet = null; page = 2 })
    }
}

@Composable
fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        actions()
    }
}

private fun looksLikeUrl(s: String) = s.startsWith("http://") || s.startsWith("https://") ||
    Regex("""^[\w-]+(\.[\w-]+)+(/\S*)?$""").matches(s)

private fun VideoItem.toEntry() = MediaEntry(url, title, channel, durationSec, thumbnail)

// ------------------------------------------------------------------ Start

@Composable
private fun StartPage(onBack: () -> Unit, onDownload: (SheetTarget) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settings by DownloaderSettings.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Discover.SearchResult?>(null) }
    var feedMode by rememberSaveable { mutableIntStateOf(if (DownloaderSettings.current.subscriptions.isEmpty()) 1 else 0) }
    var feed by remember { mutableStateOf<List<VideoItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(feedMode, reload, settings.subscriptions) {
        feed = null
        error = null
        feed = runCatching { if (feedMode == 0) Discover.feed(settings.subscriptions) else Discover.trending() }
            .onFailure { error = it.message }.getOrDefault(emptyList())
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        if (looksLikeUrl(q)) {
            onDownload(SheetTarget.Url(if (q.startsWith("http")) q else "https://$q"))
            return
        }
        loading = true
        error = null
        scope.launch {
            results = runCatching { Discover.search(q) }.onFailure { error = it.message }.getOrNull()
            loading = false
        }
    }

    val list = results?.videos ?: feed
    val listStart = rememberListStart(list)
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader(t(R.string.sec_downloader), onBack) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) results = null },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = null }) { Icon(Icons.Rounded.Close, t(R.string.close)) }
                },
                placeholder = { Text(t(R.string.dl_search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (results == null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = feedMode == 0, onClick = { feedMode = 0 }, label = { Text(t(R.string.dl_subscriptions)) })
                    FilterChip(selected = feedMode == 1, onClick = { feedMode = 1 }, label = { Text(t(R.string.dl_trends)) })
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, t(R.string.refresh)) }
                }
            }
        } else if (results!!.channels.isNotEmpty()) {
            item { ChannelRow(results!!.channels) }
        }
        if (loading || (results == null && feed == null)) {
            item { Text(t(R.string.loading), modifier = Modifier.padding(8.dp)) }
        }
        error?.let { e -> item { Text("⚠ $e", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        if (results == null && feedMode == 0 && settings.subscriptions.isEmpty()) {
            item { EmptyState(Icons.Rounded.Subscriptions, t(R.string.dl_no_subs), t(R.string.dl_no_subs_hint)) }
        }
        val videos = list.orEmpty()
        if (videos.isNotEmpty()) {
            item {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { onDownload(SheetTarget.Entries(if (results != null) query else t(R.string.dl_feed), videos.map { it.toEntry() })) },
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(t(R.string.dl_download_all_n, videos.size))
                }
            }
        }
        itemsIndexed(videos, key = { i, v -> v.url + i }) { index, v ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset).enterStagger(index, listStart)) {
                VideoCard(
                    v,
                    onPlay = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    onDownload = { onDownload(SheetTarget.Url(v.url)) },
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(channels: List<ChannelItem>) {
    val settings by DownloaderSettings.state.collectAsStateWithLifecycle()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        channels.forEach { c ->
            val subscribed = settings.subscriptions.any { it.url == c.url }
            GlassCard(Modifier.width(150.dp), contentPadding = 12.dp) {
                RemoteImage(c.avatar, Modifier.size(56.dp).clip(CircleShape), maxWidth = 160)
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { DownloaderSettings.toggleSubscription(Subscription(c.url, c.name, c.avatar)) },
                ) { Text(if (subscribed) t(R.string.dl_unsubscribe) else t(R.string.dl_subscribe)) }
            }
        }
    }
}

@Composable
private fun VideoCard(v: VideoItem, onPlay: () -> Unit, onDownload: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 0.dp, onClick = onDownload) {
        Box {
            RemoteImage(v.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (v.durationSec > 0) {
                Text(
                    duration(v.durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                v.channel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, t(R.string.dl_play)) }
            IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, t(R.string.dl_download)) }
        }
    }
}

// ------------------------------------------------------------------ Queue

@Composable
private fun QueuePage(onBack: () -> Unit) {
    val tasks by DownloadQueue.tasks.collectAsStateWithLifecycle()
    val settings by DownloaderSettings.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val running = tasks.filter { it.state == TaskState.RUNNING }
    val waiting = tasks.filter { it.state == TaskState.QUEUED || it.state == TaskState.PAUSED }
    val failed = tasks.filter { it.state == TaskState.FAILED }
    val shown = when (tab) { 0 -> running; 1 -> waiting; else -> failed }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(t(R.string.dl_nav_queue), onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(t(R.string.dl_tab_running, running.size)) })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(t(R.string.dl_tab_waiting, waiting.size)) })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text(t(R.string.dl_tab_failed, failed.size)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(t(R.string.dl_parallel), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                (1..4).forEach { n ->
                    FilterChip(
                        selected = settings.parallel == n,
                        onClick = { DownloaderSettings.update { it.copy(parallel = n) }; DownloadQueue.pump() },
                        label = { Text("$n") },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (tab == 2 && failed.isNotEmpty()) {
                Row {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { DownloadQueue.retryAllFailed() }) { Text(t(R.string.dl_retry_all)) }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { DownloadQueue.clearFailed() }) { Text(t(R.string.term_clear)) }
                }
            }
        }
        if (shown.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, t(R.string.dl_queue_empty)) }
        itemsIndexed(shown, key = { _, it -> it.id }) { _, task ->
            Box(Modifier.animateItem(placementSpec = Motion.offset)) { QueueRow(task) }
        }
    }
}

@Composable
private fun QueueRow(task: DownloadTask) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(task.thumbnail, Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), maxWidth = 320)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (task.state) {
                        TaskState.RUNNING -> task.status ?: t(R.string.dl_starting)
                        TaskState.PAUSED -> t(R.string.dl_paused)
                        TaskState.QUEUED -> t(R.string.pending)
                        TaskState.FAILED -> "⚠ " + (task.error ?: t(R.string.error))
                        TaskState.DONE -> t(R.string.done)
                    } + " · " + formatLabel(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.state == TaskState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (task.state) {
                TaskState.RUNNING -> IconButton(onClick = { DownloadQueue.pause(task.id) }) { Icon(Icons.Rounded.Pause, t(R.string.dl_pause)) }
                TaskState.PAUSED -> IconButton(onClick = { DownloadQueue.resume(task.id) }) { Icon(Icons.Rounded.PlayArrow, t(R.string.dl_resume)) }
                TaskState.FAILED -> IconButton(onClick = { DownloadQueue.retry(task.id) }) { Icon(Icons.Rounded.Refresh, t(R.string.retry)) }
                else -> {}
            }
            IconButton(onClick = { DownloadQueue.remove(task.id) }) { Icon(Icons.Rounded.Close, t(R.string.cancel)) }
        }
        if (task.state == TaskState.RUNNING || task.state == TaskState.PAUSED) {
            LinearProgressIndicator(progress = animatedProgress(task.progress), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

fun formatLabel(task: DownloadTask): String =
    if (task.isAudio) task.format.audioFormat.uppercase()
    else (if (task.format.height > 0) "${task.format.height}p " else "") + task.format.container.uppercase()
