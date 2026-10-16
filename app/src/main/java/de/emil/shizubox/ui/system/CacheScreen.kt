@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import de.emil.shizubox.R
import de.emil.shizubox.system.AppCache
import de.emil.shizubox.system.CacheCleaner
import de.emil.shizubox.t
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

@Composable
fun CacheScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<AppCache>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var freed by remember { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { list = runCatching { CacheCleaner.list(context) }.getOrDefault(emptyList()) }
    val total = list.orEmpty().sumOf { it.bytes }
    val max = list.orEmpty().maxOfOrNull { it.bytes } ?: 1L
    val listStart = rememberListStart(list != null)
    fun size(b: Long) = Formatter.formatShortFileSize(context, b)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.cc_title), style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.cc_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingText(if (list == null) "…" else size(total), MaterialTheme.typography.displaySmall)
                freed?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RollingText(size(it), MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(" " + t(R.string.cc_freed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.padding(4.dp))
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator()
                        Text(t(R.string.cc_cleaning), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady && list != null, onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CleaningServices, null, Modifier.padding(end = 8.dp))
                        Text(t(R.string.cc_clean_all))
                    }
                }
                Text(t(R.string.cc_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        itemsIndexed(list.orEmpty(), key = { _, a -> a.pkg }) { i, a ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg, 36)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(size(a.bytes), style = MaterialTheme.typography.bodyLarge)
                            }
                            LinearProgressIndicator(progress = { a.bytes.toFloat() / max }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                        // Own cache directly; other apps: Android 17 only lets the settings do it.
                        if (a.pkg == context.packageName) {
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    freed = CacheCleaner.clearOwn(context)
                                    busy = false
                                    reload++
                                }
                            }) { Text(t(R.string.cc_clear)) }
                        } else {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }) { Text(t(R.string.cc_open)) }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t(R.string.cc_clean_all_q)) },
            text = {
                Text(t(R.string.cc_clean_all_body, size(total)))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    scope.launch {
                        freed = CacheCleaner.clearAll(context)
                        busy = false
                        reload++
                    }
                }) { Text(t(R.string.cc_clean_all)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t(R.string.cancel)) } },
        )
    }
}
