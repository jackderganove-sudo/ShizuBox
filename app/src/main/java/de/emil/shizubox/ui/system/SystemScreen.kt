@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import de.emil.shizubox.nav.highlight
import de.emil.shizubox.R
import de.emil.shizubox.t
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.system.ApkInstaller
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemScreen(
    shizukuReady: Boolean,
    onOpenPage: (String) -> Unit,
    onOpenUniversalInstaller: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val highlightList = androidx.compose.foundation.lazy.rememberLazyListState()
    de.emil.shizubox.nav.HighlightScroll(highlightList, setOf("gaming", "boost", "tweaks", "installer"))
    LazyColumn(
        state = highlightList,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back))
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text(t(R.string.system), style = MaterialTheme.typography.displaySmall)
                Text(
                    t(R.string.sys_sub),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!shizukuReady) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Icons.Rounded.PhoneAndroid, t(R.string.di_title), t(R.string.di_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.DEVICE) }
                ToolTile(Icons.Rounded.Memory, t(R.string.pr_title), t(R.string.pr_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.PROCESSES) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Icons.Rounded.Terminal, t(R.string.term_title), t(R.string.term_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.TERMINAL) }
                ToolTile(Icons.AutoMirrored.Rounded.Notes, "Logcat", t(R.string.logcat_tile_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.LOGCAT) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Icons.Rounded.HourglassBottom, t(R.string.st_title), t(R.string.st_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.SCREEN_TIME) }
                ToolTile(Icons.Rounded.History, t(R.string.nh_title_short), t(R.string.nh_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.NOTIF_HISTORY) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Icons.Rounded.CleaningServices, t(R.string.cc_title), t(R.string.cc_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.CACHE) }
                ToolTile(Icons.Rounded.PrivacyTip, t(R.string.pv_title_short), t(R.string.pv_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.PRIVACY) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Icons.Rounded.BatteryChargingFull, t(R.string.br_title_short), t(R.string.br_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.BATTERY) }
                ToolTile(Icons.Rounded.ContentPaste, t(R.string.cb_title_short), t(R.string.cb_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.CLIPBOARD) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTile(Icons.Rounded.GraphicEq, t(R.string.av_title_short), t(R.string.av_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.APP_VOLUME) }
                // Only on the Pixel 11 Pro family with Android 17 – nowhere else are there LEDs to drive.
                if (de.emil.shizubox.hilight.HiLight.supported) {
                    ToolTile(Icons.Rounded.Lightbulb, "HiLight", t(R.string.hl_sub), Modifier.weight(1f)) { onOpenPage(SystemPage.HILIGHT) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        item { androidx.compose.foundation.layout.Box(Modifier.highlight("gaming")) { GamingCard(shizukuReady) } }
        item { androidx.compose.foundation.layout.Box(Modifier.highlight("boost")) { BoostCard(shizukuReady) } }
        item { androidx.compose.foundation.layout.Box(Modifier.highlight("tweaks")) { TweaksCard(shizukuReady) } }
        item { androidx.compose.foundation.layout.Box(Modifier.highlight("installer")) { InstallerCard(shizukuReady, onOpenUniversalInstaller) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamingCard(shizukuReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by Gaming.active.collectAsStateWithLifecycle()
    val o by Gaming.options.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val games by androidx.compose.runtime.produceState(emptyList<de.emil.shizubox.system.Game>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Gaming.games() }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t(R.string.gaming), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (active) t(R.string.gaming_active) else t(R.string.gaming_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.padding(4.dp))
            } else {
                Switch(
                    checked = active,
                    enabled = shizukuReady,
                    onCheckedChange = { on ->
                        busy = true
                        scope.launch {
                            runCatching { if (on) Gaming.enable() else Gaming.disable() }
                            busy = false
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple(t(R.string.g_block), o.blockNotifications) { v: Boolean -> o.copy(blockNotifications = v) },
            Triple(t(R.string.g_headsup), o.noHeadsUp) { v: Boolean -> o.copy(noHeadsUp = v) },
            Triple(t(R.string.g_refresh), o.maxRefresh) { v: Boolean -> o.copy(maxRefresh = v) },
            Triple(t(R.string.g_brightness), o.fixedBrightness) { v: Boolean -> o.copy(fixedBrightness = v) },
            Triple(t(R.string.g_kill), o.killBackground) { v: Boolean -> o.copy(killBackground = v) },
        ).forEach { (label, checked, update) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = checked, enabled = !active, onCheckedChange = { Gaming.setOptions(update(it)) })
            }
        }
        if (games.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(t(R.string.launch_games), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                games.forEach { g ->
                    AssistChip(
                        onClick = {
                            scope.launch {
                                if (!active && shizukuReady) runCatching { Gaming.enable() }
                                Gaming.launchIntent(g.pkg)?.let(context::startActivity)
                            }
                        },
                        label = { Text(g.label) },
                        leadingIcon = { AppIcon(g.pkg, 20) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallerCard(shizukuReady: Boolean, onOpenUniversalInstaller: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var universal by remember { mutableStateOf(true) }
    val universalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(onOpenUniversalInstaller)
    }
    var downgrade by remember { mutableStateOf(false) }
    var grantAll by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ApkInstaller.Result?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        result = null
        scope.launch {
            result = ApkInstaller.install(context, uris, downgrade, grantAll) { progress = it }
            progress = null
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.apk_installer), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = universal, onClick = { universal = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(t(R.string.universal)) }
            SegmentedButton(selected = !universal, onClick = { universal = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(t(R.string.quick)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (universal) {
                t(R.string.universal_hint)
            } else {
                t(R.string.quick_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (universal) {
            Spacer(Modifier.height(8.dp))
            Button(shapes = ButtonDefaults.shapes(), 
                onClick = { universalPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t(R.string.pick_file)) }
            Text(
                t(R.string.ui_based_on),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.opt_downgrade), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = downgrade, onCheckedChange = { downgrade = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.opt_grant_all), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = grantAll, onCheckedChange = { grantAll = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(shapes = ButtonDefaults.shapes(), 
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = shizukuReady && progress == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(t(R.string.pick_apk)) }
        progress?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text(it)
            }
        }
        result?.let {
            Text(
                if (it.ok) t(R.string.installed_ok) else t(R.string.error_msg, it.message),
                color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ToolTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
    }
}
