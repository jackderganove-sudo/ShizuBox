@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.cleaner.Fs
import de.emil.shizubox.cleaner.MoveResult
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard

fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0))

fun ago(time: Long): String =
    if (System.currentTimeMillis() - time < 60_000) t(R.string.cl_just_now)
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Kind of a file by its extension (also used for the storage categories). */
enum class FileKind { Image, Video, Audio, Document, Apk, Other }

private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng", "avif")
private val VIDEO = setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v", "ts")
private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr", "mid", "wma")
private val DOCS = setOf("pdf", "doc", "docx", "odt", "txt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "md", "html", "json", "xml")
private val APK = setOf("apk", "apks", "xapk", "apkm")

fun kindOf(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
    in IMAGE -> FileKind.Image
    in VIDEO -> FileKind.Video
    in AUDIO -> FileKind.Audio
    in DOCS -> FileKind.Document
    in APK -> FileKind.Apk
    else -> FileKind.Other
}

fun iconOf(name: String, dir: Boolean): ImageVector = if (dir) Icons.Rounded.Folder else when (kindOf(name)) {
    FileKind.Image -> Icons.Rounded.Image
    FileKind.Video -> Icons.Rounded.Movie
    FileKind.Audio -> Icons.Rounded.AudioFile
    FileKind.Document -> Icons.Rounded.Description
    FileKind.Apk -> Icons.Rounded.Android
    FileKind.Other -> Icons.Rounded.InsertDriveFile
}

@Composable
fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(horizontal = 8.dp))
}

/** One file or folder with a check box; long press offers to exclude it. */
@Composable
fun EntryRow(
    path: String,
    bytes: Long,
    dir: Boolean,
    checked: Boolean?,
    note: String? = null,
    time: Long = 0,
    onCheck: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showParent: Boolean = true,
) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onClick ?: checked?.let { { onCheck(!it) } }, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) leading() else Icon(iconOf(path, dir), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(Fs.rel(path.substringBeforeLast('/')).takeIf { showParent }, time.takeIf { it > 0 }?.let(::ago)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!note.isNullOrEmpty()) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(size(context, bytes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
            if (checked != null) Checkbox(checked = checked, onCheckedChange = onCheck) else trailing?.invoke()
        }
    }
}

/** The preview before anything moves: how many, how much, where to. */
@Composable
fun TrashConfirmDialog(count: Int, bytes: Long, onConfirm: () -> Unit, onDismiss: () -> Unit, extra: String? = null) {
    val context = LocalContext.current
    val days = CleanerSettings.current.trashDays
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.cl_confirm_title)) },
        text = {
            Column {
                Text(tp(R.plurals.cl_confirm_body, count, count, size(context, bytes)), style = MaterialTheme.typography.bodyLarge)
                Text(tp(R.plurals.cl_confirm_days, days, days), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(t(R.string.cl_to_trash)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } },
    )
}

/** "X moved to the trash ✓" or the first problem. */
fun resultText(context: Context, r: MoveResult): String = buildString {
    if (r.moved > 0) append(tp(R.plurals.cl_moved, r.moved, r.moved, size(context, r.bytes)))
    if (r.failed.isNotEmpty()) {
        if (isNotEmpty()) append('\n')
        append("⚠ " + tp(R.plurals.cl_failed, r.failed.size, r.failed.size, r.failed.first().second))
    }
    if (isEmpty()) append(t(R.string.cl_nothing))
}
