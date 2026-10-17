@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.Candidate
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.cleaner.Fs
import de.emil.shizubox.cleaner.Guard
import de.emil.shizubox.cleaner.MoveResult
import de.emil.shizubox.cleaner.Trash
import de.emil.shizubox.t
import kotlinx.coroutines.launch
import java.io.File

/** Opens/shares a file of the shared storage (needs all-files access; Android/data is not readable for the app). */
fun openFile(context: Context, path: String, share: Boolean) {
    val ok = runCatching {
        val f = File(path)
        require(f.canRead())
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        context.startActivity(Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) Toast.makeText(context, t(R.string.cl_cannot_open), Toast.LENGTH_SHORT).show()
}

/** Exclude a path from now on (long press on any finding). */
fun excludePath(path: String) {
    val rel = Fs.rel(path)
    CleanerSettings.update { it.copy(excludedPaths = it.excludedPaths + rel) }
}

/**
 * Actions for one file or folder: open, share, show in folder, to the trash, exclude.
 * Moving asks for the preview first; locked paths say why.
 */
@Composable
fun EntryMenu(
    path: String,
    dir: Boolean,
    bytes: Long,
    tool: CleanTool,
    onDismiss: () -> Unit,
    onShowInFolder: ((String) -> Unit)? = null,
    onMoved: (MoveResult) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val blocked = remember(path) { Guard.blockReason(path, tool) }
    if (confirm) {
        TrashConfirmDialog(1, bytes, onDismiss = onDismiss, onConfirm = {
            onDismiss()
            scope.launch {
                val r = runCatching { Trash.move(listOf(Candidate(path, if (dir) -1 else bytes, dir)), tool) }
                    .getOrElse { MoveResult(0, 0, listOf(path to (it.message ?: "error"))) }
                Toast.makeText(context, resultText(context, r), Toast.LENGTH_LONG).show()
                onMoved(r)
            }
        })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(path.substringAfterLast('/')) },
        text = {
            Column {
                Text(Fs.rel(path) + " · " + size(context, bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!dir) {
                    MenuItem(Icons.Rounded.OpenInNew, t(R.string.cl_open)) { onDismiss(); openFile(context, path, share = false) }
                    MenuItem(Icons.Rounded.Share, t(R.string.cl_share)) { onDismiss(); openFile(context, path, share = true) }
                }
                if (onShowInFolder != null) MenuItem(Icons.Rounded.FolderOpen, t(R.string.cl_show_in_folder)) { onDismiss(); onShowInFolder(if (dir) path else path.substringBeforeLast('/')) }
                MenuItem(Icons.Rounded.Delete, t(R.string.cl_to_trash), enabled = blocked == null) { confirm = true }
                if (blocked != null) Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 12.dp))
                MenuItem(Icons.Rounded.Block, t(R.string.cl_exclude)) {
                    onDismiss()
                    excludePath(path)
                    Toast.makeText(context, t(R.string.cl_excluded_done), Toast.LENGTH_SHORT).show()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(R.string.close)) } },
    )
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, Modifier.padding(end = 12.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}
