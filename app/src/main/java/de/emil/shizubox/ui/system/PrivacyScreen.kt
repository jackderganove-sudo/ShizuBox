@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import de.emil.shizubox.apps.NetBlock
import de.emil.shizubox.apps.rememberAppLabel
import de.emil.shizubox.privacy.PrivAccess
import de.emil.shizubox.privacy.PrivOp
import de.emil.shizubox.privacy.PrivacyLog
import de.emil.shizubox.system.ScreenTime
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

/** The main sensitive groups; coarse location is shown together with location. */
private val FILTERS = listOf(PrivOp.Camera, PrivOp.Microphone, PrivOp.Location, PrivOp.Contacts, PrivOp.Clipboard, PrivOp.Sensors)

private fun expand(selected: Set<PrivOp>) = if (PrivOp.Location in selected) selected + PrivOp.CoarseLocation else selected

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by PrivacyLog.settings.collectAsStateWithLifecycle()
    val version by PrivacyLog.version.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(setOf(PrivOp.Camera.name, PrivOp.Microphone.name, PrivOp.Location.name)) }
    val ops = expand(selected.map { PrivOp.valueOf(it) }.toSet())
    var perApp by rememberSaveable { mutableStateOf(false) }
    var onlyBg by rememberSaveable { mutableStateOf(false) }
    var openPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var collecting by remember { mutableStateOf(false) }
    BackHandler(openPkg != null) { openPkg = null }

    // Opening the screen fetches what's new right away (the worker only runs hourly).
    LaunchedEffect(s.enabled, shizukuReady) {
        if (s.enabled && shizukuReady) {
            collecting = true
            runCatching { ScreenTime.ensureAccess(context); PrivacyLog.collect() }
            collecting = false
        }
    }
    val entries by produceState<List<PrivAccess>?>(null, version, ops, openPkg, onlyBg, perApp) {
        value = if (perApp && openPkg == null) emptyList() else PrivacyLog.entries(ops, openPkg, onlyBg)
    }
    val apps by produceState(emptyList<PrivacyLog.AppSummary>(), version, ops, perApp) {
        value = if (perApp) PrivacyLog.perApp(ops) else emptyList()
    }
    val listStart = rememberListStart(Triple(perApp, openPkg, ops))

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = { if (openPkg != null) openPkg = null else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(
                    if (openPkg != null) rememberAppLabel(openPkg!!) else t(R.string.pv_title),
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (collecting) LoadingIndicator(Modifier.padding(end = 8.dp))
                if (s.enabled) IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Rounded.Settings, t(R.string.settings)) }
            }
        }
        if (!s.enabled) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(t(R.string.pv_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(t(R.string.pv_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = { PrivacyLog.update { it.copy(enabled = true) } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(t(R.string.pv_enable))
                    }
                    if (!shizukuReady) Text(t(R.string.shizuku_must), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            return@LazyColumn
        }
        item {
            AnimatedVisibility(showSettings, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t(R.string.pv_record), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = s.enabled, onCheckedChange = { v -> PrivacyLog.update { it.copy(enabled = v) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.pv_notify), style = MaterialTheme.typography.bodyMedium)
                            Text(t(R.string.pv_notify_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = s.notifyBackground, onCheckedChange = { v -> PrivacyLog.update { it.copy(notifyBackground = v) } })
                    }
                    Text(t(R.string.nh_keep), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90, 365).forEach { d ->
                            FilterChip(selected = s.keepDays == d, onClick = { PrivacyLog.update { it.copy(keepDays = d) } }, label = { Text(tp(R.plurals.nh_days, d, d)) })
                        }
                    }
                    Text(t(R.string.pv_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { PrivacyLog.deleteAll() } }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(t(R.string.nh_delete_all))
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTERS.forEach { o ->
                    FilterChip(
                        selected = o.name in selected,
                        onClick = { selected = if (o.name in selected && selected.size > 1) selected - o.name else selected + o.name },
                        label = { Text(o.label) },
                    )
                }
            }
            if (openPkg == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SegmentedButton(selected = !perApp, onClick = { perApp = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(t(R.string.pv_timeline)) }
                    SegmentedButton(selected = perApp, onClick = { perApp = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(t(R.string.nh_mode_apps)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(t(R.string.pv_only_bg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = onlyBg, onCheckedChange = { onlyBg = it })
            }
        }
        if (perApp && openPkg == null) {
            if (apps.isEmpty()) item { EmptyState(Icons.Rounded.PrivacyTip, t(R.string.pv_empty), t(R.string.pv_empty_hint)) }
            itemsIndexed(apps.filter { !onlyBg || it.background > 0 }, key = { _, a -> a.pkg }) { i, a ->
                Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { openPkg = a.pkg }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.pkg, 36)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(rememberAppLabel(a.pkg), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    a.counts.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key.label} ${it.value}×" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                )
                                if (a.background > 0) Text(t(R.string.pv_bg_n, a.background), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text(timeAgo(a.last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            val list = entries
            if (list != null && list.isEmpty()) item { EmptyState(Icons.Rounded.PrivacyTip, t(R.string.pv_empty), t(R.string.pv_empty_hint)) }
            var lastDay = ""
            list.orEmpty().forEachIndexed { i, a ->
                val day = DateUtils.formatDateTime(context, a.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "day-$day") { Text(day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
                }
                item(key = a.id) {
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                        AccessRow(a, shizukuReady, showApp = openPkg == null, onOpenApp = { openPkg = a.pkg })
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessRow(a: PrivAccess, shizukuReady: Boolean, showApp: Boolean, onOpenApp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val blocked by NetBlock.blocked.collectAsStateWithLifecycle()
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = if (showApp) onOpenApp else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showApp) {
                AppIcon(a.pkg, 32)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                if (showApp) Text(rememberAppLabel(a.pkg), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    a.op.label + " · " + DateUtils.formatDateTime(context, a.time, DateUtils.FORMAT_SHOW_TIME) +
                        (if (a.durationMs >= 1000) " · " + DateUtils.formatElapsedTime(a.durationMs / 1000) else "") +
                        (if (a.count > 1) " · ${a.count}×" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (a.background) Badge(t(R.string.pv_bg), MaterialTheme.colorScheme.error)
                    if (a.service) Badge(t(R.string.pv_service), MaterialTheme.colorScheme.tertiary)
                    if (a.screenOff) Badge(t(R.string.pv_screen_off), MaterialTheme.colorScheme.error)
                }
                done?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(t(R.string.pv_revoke, a.op.label)) }, enabled = shizukuReady, onClick = {
                        menu = false
                        scope.launch { done = if (PrivacyLog.revoke(a.pkg, a.op).ok) t(R.string.pv_revoked) else t(R.string.error) }
                    })
                    DropdownMenuItem(text = { Text(t(R.string.app_info)) }, onClick = {
                        menu = false
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                    if (NetBlock.canBlock(a.pkg)) {
                        val isBlocked = a.pkg in blocked
                        DropdownMenuItem(text = { Text(if (isBlocked) t(R.string.nb_allow) else t(R.string.nb_block)) }, enabled = shizukuReady, onClick = {
                            menu = false
                            scope.launch { runCatching { NetBlock.set(listOf(a.pkg), !isBlocked) } }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun timeAgo(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
