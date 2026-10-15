package de.emil.shizubox.ui.downloader

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.downloader.DownloadQueue
import de.emil.shizubox.downloader.DownloaderSettings
import de.emil.shizubox.downloader.FormatChoice
import de.emil.shizubox.downloader.MediaEntry
import de.emil.shizubox.downloader.MediaInfo
import de.emil.shizubox.downloader.MediaKind
import de.emil.shizubox.downloader.Ytdl
import de.emil.shizubox.t

/** What the sheet downloads: a URL (looked up first) or an already known list (feed, playlist). */
sealed interface SheetTarget {
    data class Url(val url: String) : SheetTarget
    data class Entries(val title: String, val entries: List<MediaEntry>) : SheetTarget
}

private val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(target: SheetTarget, onDismiss: () -> Unit, onQueued: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DownloadSheetContent(target, onQueued)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadSheetContent(target: SheetTarget, onQueued: () -> Unit) {
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var format by remember { mutableStateOf(DownloaderSettings.current.defaultFormat) }
    LaunchedEffect(target) {
        when (target) {
            is SheetTarget.Url -> runCatching { Ytdl.info(target.url) }
                .onSuccess { info = it }
                .onFailure { e ->
                    // Errors without a message (e.g. initializer errors) must still end the spinner.
                    error = e.message?.lines()?.lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
                        ?: e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                }
            is SheetTarget.Entries -> info = MediaInfo(target.title, null, target.entries.firstOrNull()?.thumbnail, 0, emptyList(), target.entries, "")
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
    ) {
        AnimatedContent(targetState = info to error, label = "sheet") { (i, err) ->
            when {
                err != null -> Text("⚠ $err", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp))
                i == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                    LoadingIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text(t(R.string.dl_reading_link))
                }
                else -> Column {
                    RemoteImage(i.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
                    Text(i.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        listOfNotNull(i.uploader, if (i.isPlaylist) t(R.string.dl_n_videos, i.entries.size) else i.durationSec.takeIf { it > 0 }?.let(::duration))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val i = info ?: return@Column
        FormatPicker(format, available = i.heights) { format = it }
        Spacer(Modifier.height(16.dp))
        Button(
            shapes = ButtonDefaults.shapes(),
            onClick = {
                if (i.isPlaylist) DownloadQueue.enqueueAll(i.entries, format)
                else DownloadQueue.enqueue(i.url, i.title, format, i.uploader, i.thumbnail, i.durationSec)
                onQueued()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (i.isPlaylist) t(R.string.dl_download_all_n, i.entries.size) else t(R.string.dl_download))
        }
    }
}

/** Video/audio, quality, container and embedding options – also used for the default format. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatPicker(format: FormatChoice, available: List<Int> = emptyList(), onChange: (FormatChoice) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(MediaKind.VIDEO to t(R.string.dl_video), MediaKind.AUDIO to t(R.string.dl_audio)).forEachIndexed { i, (k, label) ->
                SegmentedButton(
                    selected = format.kind == k,
                    onClick = { onChange(format.copy(kind = k)) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        if (format.kind == MediaKind.VIDEO) {
            Text(t(R.string.dl_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = format.height == 0, onClick = { onChange(format.copy(height = 0)) }, label = { Text(t(R.string.dl_best)) })
                // Only qualities the video really has (if known).
                HEIGHTS.filter { available.isEmpty() || available.any { a -> a >= it * 0.9 } }.forEach { h ->
                    FilterChip(selected = format.height == h, onClick = { onChange(format.copy(height = h)) }, label = { Text("${h}p") })
                }
            }
            Text(t(R.string.dl_container), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("mp4", "mkv", "webm").forEach { c ->
                    FilterChip(selected = format.container == c, onClick = { onChange(format.copy(container = c)) }, label = { Text(c.uppercase()) })
                }
            }
        } else {
            Text(t(R.string.dl_audio_format), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("mp3", "m4a", "opus").forEach { a ->
                    FilterChip(selected = format.audioFormat == a, onClick = { onChange(format.copy(audioFormat = a)) }, label = { Text(a.uppercase()) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(t(R.string.dl_embed_thumb), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = format.embedThumbnail, onCheckedChange = { onChange(format.copy(embedThumbnail = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.dl_embed_meta), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = format.embedMetadata, onCheckedChange = { onChange(format.copy(embedMetadata = it)) })
        }
    }
}

fun duration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
