@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.updates

import de.emil.shizubox.ui.motion.animatedProgress
import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.updates.Source
import de.emil.shizubox.updates.TrackedApp
import de.emil.shizubox.updates.UpdateState
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDetailScreen(app: TrackedApp, resumeTick: Int, onBack: () -> Unit, onOpenBrowser: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(app, resumeTick) { Updates.state(app) }
    val realVersion = remember(app, resumeTick) { Updates.installedVersionName(app.id) }
    val downloads by Updates.downloads.collectAsStateWithLifecycle()
    val progress = downloads[app.key]
    var checking by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var filter by remember(app.key) { mutableStateOf(app.apkFilterRegex) }
    var pkg by remember(app.key) { mutableStateOf(app.id) }
    var showLog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 16.dp)) {
                if (realVersion != null) {
                    AppIcon(app.id, 64)
                    Spacer(Modifier.padding(8.dp))
                }
                Column {
                    Text(app.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        t(R.string.by_author_source, app.author, Source.forUrl(app.url)?.label ?: t(R.string.unsupported_source)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text(
                    when (state) {
                        UpdateState.Update -> t(R.string.st_update)
                        UpdateState.UpToDate -> t(R.string.st_uptodate)
                        UpdateState.NotInstalled -> t(R.string.st_notinstalled)
                        UpdateState.Unknown -> t(R.string.st_unknown)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(t(R.string.latest_v, app.latestVersion ?: "?") + if (app.releaseDate > 0) " (${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(Date(app.releaseDate))})" else "", style = MaterialTheme.typography.bodyMedium)
                Text(t(R.string.installed_v, realVersion ?: "–"), style = MaterialTheme.typography.bodyMedium)
                if (Updates.isSkipped(app)) {
                    Text(t(R.string.upd_skipped, app.latestVersion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                app.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                progress?.let {
                    LinearProgressIndicator(progress = animatedProgress(it), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                val trackOnly = Source.forUrl(app.url)?.trackOnly == true
                if (trackOnly) {
                    Text(
                        t(R.string.apkmirror_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trackOnly) {
                        Button(shapes = ButtonDefaults.shapes(), onClick = { onOpenBrowser(app.releaseUrl ?: app.url) }) { Text(t(R.string.load_apkmirror)) }
                    } else Button(shapes = ButtonDefaults.shapes(), 
                        enabled = progress == null && app.chosenApk != null,
                        onClick = { Updates.downloadAndInstall(app) },
                    ) { Text(if (state == UpdateState.NotInstalled) t(R.string.install) else if (state == UpdateState.Update) t(R.string.update) else t(R.string.reinstall)) }
                    OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = !checking, onClick = {
                        checking = true
                        scope.launch { Updates.check(app); checking = false }
                    }) { Text(if (checking) t(R.string.checking) else t(R.string.check)) }
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.releaseUrl ?: app.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text(t(R.string.open_source)) }
                    if (state == UpdateState.Update || state == UpdateState.Unknown) {
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { Updates.markInstalled(app) }) { Text(t(R.string.mark_current)) }
                    }
                    if (state == UpdateState.Update) {
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { Updates.update(app.copy(skippedVersion = app.latestVersion)) }) { Text(t(R.string.upd_skip)) }
                    }
                    if (Updates.isSkipped(app)) {
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { Updates.update(app.copy(skippedVersion = null)) }) { Text(t(R.string.upd_unskip)) }
                    }
                }
            }
        }
        if (app.apks.size > 1) {
            item {
                ObCard(Modifier.fillMaxWidth()) {
                    Text(t(R.string.apk_choice), style = MaterialTheme.typography.titleMedium)
                    app.apks.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = app.chosenApk == a, onClick = { Updates.update(app.copy(preferredApk = i)) })
                            Text(a.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        app.changeLog?.let { log ->
            item {
                ObCard(Modifier.fillMaxWidth(), onClick = { showLog = !showLog }) {
                    Text(t(R.string.changes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        log.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (showLog) Int.MAX_VALUE else 6,
                    )
                }
            }
        }
        item {
            ObCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.settings), style = MaterialTheme.typography.titleMedium)
                Toggle(t(R.string.include_pre), app.includePrereleases) {
                    scope.launch { Updates.check(app.copy(includePrereleases = it)) }
                }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(t(R.string.apk_filter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Toggle(t(R.string.invert_filter), app.invertApkFilter) {
                    scope.launch { Updates.check(app.copy(invertApkFilter = it, apkFilterRegex = filter)) }
                }
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim() },
                    label = { Text(t(R.string.pkg_of_installed)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { Updates.check(app.copy(apkFilterRegex = filter, id = pkg)) } }) { Text(t(R.string.apply)) }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRemove = true }) { Text(t(R.string.remove)) }
                }
            }
        }
        item { Text(app.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(t(R.string.untrack_q)) },
            text = { Text(t(R.string.untrack_hint)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { Updates.remove(app); onBack() }) { Text(t(R.string.remove)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRemove = false }) { Text(t(R.string.cancel)) } },
        )
    }
}
