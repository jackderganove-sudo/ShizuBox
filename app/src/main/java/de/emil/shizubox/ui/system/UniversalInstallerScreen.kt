@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import de.emil.shizubox.ui.motion.popIn
import de.emil.shizubox.ui.motion.animatedProgress
import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.emil.shizubox.installer.ApkInfo
import de.emil.shizubox.installer.ApkParser
import de.emil.shizubox.installer.InstallOptions
import de.emil.shizubox.installer.InstallResult
import de.emil.shizubox.installer.ShizukuApkInstaller
import de.emil.shizubox.installer.SplitEntry
import de.emil.shizubox.installer.SplitType
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

private sealed interface Stage {
    data object Parsing : Stage
    data class Preview(val info: ApkInfo) : Stage
    data class Installing(val info: ApkInfo) : Stage
    data class Done(val info: ApkInfo, val result: InstallResult) : Stage
    data class ParseError(val message: String) : Stage
}

/**
 * Install flow based on Universal Installer: inspect the file, choose splits, install via Shizuku.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalInstallerScreen(uri: Uri, shizukuReady: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<Stage>(Stage.Parsing) }
    val splits = remember { mutableStateListOf<SplitEntry>() }
    val appSettings = de.emil.shizubox.settings.AppSettings.current
    var options by remember {
        mutableStateOf(
            InstallOptions(
                allowDowngrade = appSettings.defaultDowngrade,
                grantAllPermissions = appSettings.defaultGrantAll,
                spoofPlayStore = appSettings.defaultPlayStoreSource,
            ),
        )
    }
    val silent = ShizukuApkInstaller.useShizuku()
    var progress by remember { mutableFloatStateOf(0f) }
    var showPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        stage = runCatching { ApkParser.parse(context, uri) }.fold(
            onSuccess = { info ->
                splits.clear()
                splits.addAll(info.splitEntries)
                options = options.copy(allowDowngrade = options.allowDowngrade || info.isDowngrade)
                Stage.Preview(info)
            },
            onFailure = { Stage.ParseError(it.message ?: t(R.string.file_unreadable_long)) },
        )
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text("Universal Installer", style = MaterialTheme.typography.titleLarge)
            }
        }
        when (val s = stage) {
            Stage.Parsing -> item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp))
                        Text(t(R.string.reading_file))
                    }
                }
            }
            is Stage.ParseError -> item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(t(R.string.file_unreadable), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is Stage.Preview, is Stage.Installing, is Stage.Done -> {
                val info = when (s) {
                    is Stage.Preview -> s.info
                    is Stage.Installing -> s.info
                    is Stage.Done -> s.info
                    else -> error("unreachable")
                }
                item { AppHeader(info) }
                if (s !is Stage.Done) {
                    item {
                        VirusTotalCard(
                            key = uri,
                            autoScan = appSettings.vtAutoScan,
                            open = { context.contentResolver.openInputStream(uri) },
                            asFile = {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val f = java.io.File(context.cacheDir, "vt_upload_${ApkParser.fileName(context, uri)}")
                                    context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                                    f
                                }
                            },
                        )
                    }
                }
                if (info.signatureMismatch == true) {
                    item {
                        GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    t(R.string.sig_mismatch_warn),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                when (s) {
                    is Stage.Preview -> {
                        if (silent) {
                            item { OptionsCard(info, options) { options = it } }
                        } else {
                            item {
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Text(t(R.string.via_system_dialog), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (shizukuReady) t(R.string.via_system_set) else t(R.string.via_system_noshz),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (splits.size > 1) {
                            item { SplitsCard(splits) }
                        }
                        if (info.permissions.isNotEmpty()) {
                            item {
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${info.permissions.size} Berechtigungen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { showPermissions = !showPermissions }) { Text(if (showPermissions) "Ausblenden" else "Anzeigen") }
                                    }
                                    if (showPermissions) {
                                        info.permissions.sorted().forEach {
                                            Text(it.removePrefix("android.permission."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            val selected = splits.filter { it.selected }
                            val hasBase = selected.any { it.type == SplitType.Base }
                            val blocked = appSettings.blockSignatureMismatch && info.signatureMismatch == true
                            Button(shapes = ButtonDefaults.shapes(), 
                                onClick = {
                                    stage = Stage.Installing(info)
                                    progress = 0f
                                    scope.launch {
                                        val result = ShizukuApkInstaller.install(
                                            context, info.appName, selected.map { it.uri }, options,
                                        ) { progress = it }
                                        stage = Stage.Done(info, result)
                                    }
                                },
                                enabled = (shizukuReady || !silent) && hasBase && !blocked,
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                            ) {
                                Text(
                                    when {
                                        blocked -> t(R.string.blocked_sig)
                                        silent && !shizukuReady -> t(R.string.shizuku_not_conn)
                                        info.isUpdate -> t(R.string.update)
                                        info.isDowngrade -> t(R.string.install_downgrade)
                                        info.isReinstall -> t(R.string.reinstall)
                                        else -> t(R.string.install)
                                    } + (if (!silent && !blocked) t(R.string.system_dialog_sfx) else "") + if (splits.size > 1) t(R.string.parts_sfx, selected.size, Formatter.formatShortFileSize(context, selected.sumOf { it.sizeBytes })) else "",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                    is Stage.Installing -> item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text(t(R.string.installing), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(progress = animatedProgress(progress), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is Stage.Done -> item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            when (val r = s.result) {
                                InstallResult.Success -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.popIn())
                                        Spacer(Modifier.width(12.dp))
                                        Text(t(R.string.app_installed, info.appName), style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        context.packageManager.getLaunchIntentForPackage(info.packageName)?.let { launch ->
                                            Button(shapes = ButtonDefaults.shapes(), onClick = { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onClose() }) { Text(t(R.string.open)) }
                                        }
                                        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onClose) { Text(t(R.string.done)) }
                                    }
                                }
                                is InstallResult.Failure -> {
                                    Text(r.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                    Text(r.hint, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { stage = Stage.Preview(info) }) { Text(t(R.string.back)) }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppHeader(info: ApkInfo) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(info) { runCatching { info.icon?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull() }
            if (bitmap != null) {
                Image(bitmap, null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(info.appName, style = MaterialTheme.typography.headlineSmall)
                Text(info.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        val version = when {
            info.installedVersionName == null -> t(R.string.v_new, info.versionName)
            info.isUpdate -> "Update · ${info.installedVersionName} → ${info.versionName}"
            info.isDowngrade -> "Downgrade · ${info.installedVersionName} → ${info.versionName}"
            else -> t(R.string.v_same, info.versionName)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(version) })
            AssistChip(onClick = {}, label = { Text(info.fileFormat) })
            if (info.fileSizeBytes > 0) AssistChip(onClick = {}, label = { Text(Formatter.formatShortFileSize(context, info.fileSizeBytes)) })
            if (info.minSdkVersion > 0) AssistChip(onClick = {}, label = { Text("Android ${sdkName(info.minSdkVersion)}+") })
            if (info.supportedAbis.isNotEmpty()) AssistChip(onClick = {}, label = { Text(info.supportedAbis.joinToString(", ")) })
        }
    }
}

@Composable
private fun OptionsCard(info: ApkInfo, options: InstallOptions, onChange: (InstallOptions) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.options), style = MaterialTheme.typography.titleMedium)
        OptionRow(t(R.string.opt_downgrade), options.allowDowngrade, highlight = info.isDowngrade) { onChange(options.copy(allowDowngrade = it)) }
        OptionRow(t(R.string.opt_grant_all), options.grantAllPermissions) { onChange(options.copy(grantAllPermissions = it)) }
        OptionRow(t(R.string.opt_play), options.spoofPlayStore) { onChange(options.copy(spoofPlayStore = it)) }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, highlight: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SplitsCard(splits: MutableList<SplitEntry>) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.splits), style = MaterialTheme.typography.titleMedium)
        Text(
            t(R.string.splits_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SplitType.entries.forEach { type ->
            val group = splits.withIndex().filter { it.value.type == type }
            if (group.isEmpty()) return@forEach
            Text(type.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            group.forEach { (i, e) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = e.selected,
                        enabled = e.type != SplitType.Base,
                        onCheckedChange = { splits[i] = e.copy(selected = it) },
                    )
                    Text(e.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Formatter.formatShortFileSize(context, e.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun sdkName(sdk: Int) = when (sdk) {
    in 0..20 -> "4"
    21, 22 -> "5"
    23 -> "6"
    24, 25 -> "7"
    26, 27 -> "8"
    28 -> "9"
    else -> (sdk - 19).toString()
}
