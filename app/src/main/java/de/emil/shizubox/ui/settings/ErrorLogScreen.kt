@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.debug.ErrorKind
import de.emil.shizubox.debug.ErrorLog
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart

private fun kindLabel(k: ErrorKind) = when (k) {
    ErrorKind.Crash -> t(R.string.el_crash)
    ErrorKind.Anr -> t(R.string.el_anr)
    ErrorKind.Native -> t(R.string.el_native)
    ErrorKind.Killed -> t(R.string.el_killed)
    ErrorKind.Hang -> t(R.string.el_hang)
}

/** Settings → error log: ShizuBox's own crashes, ANRs and hangs, to copy or share. */
@Composable
fun ErrorLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by ErrorLog.entries.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val listStart = rememberListStart(Unit)
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.el_title), style = MaterialTheme.typography.headlineSmall)
            }
            Text(t(R.string.el_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (entries.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ShizuBox", ErrorLog.asText()))
                        message = t(R.string.el_copied)
                    }) { Text(t(R.string.el_copy)) }
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ErrorLog.asText()), null)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(t(R.string.el_share)) }
                    TextButton(onClick = { ErrorLog.clear() }) { Text(t(R.string.el_clear), color = MaterialTheme.colorScheme.error) }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        } else {
            item { EmptyState(Icons.Rounded.CheckCircle, t(R.string.el_empty), t(R.string.el_empty_hint)) }
        }
        itemsIndexed(entries, key = { _, e -> "${e.time}-${e.kind}-${e.title.hashCode()}" }) { i, e ->
            var open by remember { mutableStateOf(false) }
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                GlassCard(Modifier.fillMaxWidth().animateContentSize(Motion.smooth()), contentPadding = 12.dp, onClick = { open = !open }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            kindLabel(e.kind),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (e.kind == ErrorKind.Hang || e.kind == ErrorKind.Anr) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            DateUtils.formatDateTime(context, e.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    if (open) {
                        Text(
                            e.details,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}
