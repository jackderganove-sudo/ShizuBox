package de.emil.shizubox.ui.system

import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.notifications.NApp
import de.emil.shizubox.notifications.NConv
import de.emil.shizubox.notifications.NEntry
import de.emil.shizubox.notifications.NState
import de.emil.shizubox.notifications.NotifHistory
import de.emil.shizubox.system.ScreenTime
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.openClose
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class NhMode { All, Apps, Removed }

@Composable
fun NotificationHistoryScreen(shizukuReady: Boolean, onBack: () -> Unit, initialPkg: String? = null, initialConv: String? = null) {
    val context = LocalContext.current
    val s by NotifHistory.settings.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(NotifHistory.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = NotifHistory.hasAccess(context)
        onPauseOrDispose { }
    }
    var unlocked by remember { mutableStateOf(!s.lock) }
    // Opened app (conversations) and opened chat.
    var openPkg by rememberSaveable { mutableStateOf(initialPkg) }
    var openConv by rememberSaveable { mutableStateOf(initialConv) }
    BackHandler(openConv != null) { openConv = null }
    BackHandler(openConv == null && openPkg != null) { openPkg = null }

    if (s.lock && !unlocked) {
        LockedView(onBack) { unlocked = true }
        return
    }
    val page = when {
        openPkg != null && openConv != null -> 2
        openPkg != null -> 1
        else -> 0
    }
    AnimatedContent(page, transitionSpec = { openClose(opening = targetState > initialState) }, label = "nhPage") { p ->
        when (p) {
            2 -> ThreadView(openPkg.orEmpty(), openConv.orEmpty(), onBack = { openConv = null })
            1 -> AppView(openPkg.orEmpty(), onOpen = { openConv = it }, onBack = { openPkg = null })
            else -> MainView(shizukuReady, access, onAccess = { access = it }, onOpenApp = { openPkg = it }, onOpenConv = { pkg, c -> openPkg = pkg; openConv = c }, onBack = onBack)
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun LockedView(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val prompt = {
        BiometricPrompt.Builder(context)
            .setTitle(t(R.string.nh_title))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onUnlocked()
            })
    }
    LaunchedEffect(Unit) { runCatching { prompt() } }
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
        Header(t(R.string.nh_title), onBack)
        EmptyState(Icons.Rounded.Lock, t(R.string.nh_locked), modifier = Modifier.padding(top = 48.dp))
        Button(shapes = ButtonDefaults.shapes(), onClick = { runCatching { prompt() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(t(R.string.nh_unlock))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainView(
    shizukuReady: Boolean,
    access: Boolean,
    onAccess: (Boolean) -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenConv: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by NotifHistory.settings.collectAsStateWithLifecycle()
    val version by NotifHistory.version.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(NhMode.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val apps by produceState(emptyList<NApp>(), version) { value = NotifHistory.apps() }
    val entries by produceState<List<NEntry>?>(null, version, mode, query, filterPkg) {
        value = when (mode) {
            NhMode.Apps -> emptyList()
            NhMode.All -> NotifHistory.entries(query, filterPkg)
            NhMode.Removed -> NotifHistory.entries(query, filterPkg, onlyRemoved = true)
        }
    }
    val listStart = rememberListStart(mode)
    val ready = s.enabled && access

    fun enable() {
        scope.launch {
            NotifHistory.update { it.copy(enabled = true) }
            // Usage access lets the history tell "opened by you" apart from "withdrawn by the app".
            runCatching { ScreenTime.ensureAccess(context) }
            if (!NotifHistory.setAccess(context, true)) {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotifHistory.component(context).flattenToString()),
                )
            }
            onAccess(NotifHistory.hasAccess(context))
        }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Header(t(R.string.nh_title), onBack) {
                if (s.enabled) IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Rounded.Settings, t(R.string.settings)) }
            }
        }
        if (!ready) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(t(R.string.nh_setup), style = MaterialTheme.typography.bodyMedium)
                    Text(t(R.string.nh_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    if (s.enabled && !access) {
                        Text(t(R.string.nh_no_access), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    Button(shapes = ButtonDefaults.shapes(), onClick = ::enable, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(if (shizukuReady) t(R.string.nh_enable) else t(R.string.nh_open_settings))
                    }
                }
            }
        }
        item {
            AnimatedVisibility(showSettings && s.enabled, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
                SettingsCard(onDeleteAll = { confirmDeleteAll = true })
            }
        }
        if (s.enabled) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(t(R.string.search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val labels = listOf(t(R.string.nh_mode_all), t(R.string.nh_mode_apps), t(R.string.nh_mode_removed))
                    NhMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = mode == m, onClick = { mode = m }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(labels[i], maxLines = 1) }
                    }
                }
            }
            if (mode != NhMode.Apps && apps.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(apps, key = { it.pkg }) { a ->
                            FilterChip(
                                selected = filterPkg == a.pkg,
                                onClick = { filterPkg = if (filterPkg == a.pkg) null else a.pkg },
                                label = { Text(appLabel(a.pkg), maxLines = 1) },
                                leadingIcon = { AppIcon(a.pkg, 18) },
                            )
                        }
                    }
                }
            }
            if (mode == NhMode.Apps) {
                if (apps.isEmpty()) item { EmptyState(Icons.Rounded.NotificationsNone, t(R.string.nh_empty), t(R.string.nh_empty_hint)) }
                itemsIndexed(apps, key = { _, a -> "app-" + a.pkg }) { i, a ->
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                        AppRow(a) { onOpenApp(a.pkg) }
                    }
                }
            } else {
                val list = entries
                if (list != null && list.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Rounded.NotificationsNone,
                            if (mode == NhMode.Removed) t(R.string.nh_empty_removed) else t(R.string.nh_empty),
                            if (mode == NhMode.Removed) t(R.string.nh_legend) else t(R.string.nh_empty_hint),
                        )
                    }
                }
                itemsIndexed(list.orEmpty(), key = { _, e -> e.id }) { i, e ->
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                        EntryRow(e, showApp = true) { if (e.message) onOpenConv(e.pkg, e.conv) else onOpenApp(e.pkg) }
                    }
                }
                if (mode == NhMode.Removed && !list.isNullOrEmpty()) {
                    item { Text(t(R.string.nh_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                }
            }
        }
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(t(R.string.nh_delete_all_q)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    scope.launch { NotifHistory.deleteAll() }
                }) { Text(t(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(t(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(onDeleteAll: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by NotifHistory.settings.collectAsStateWithLifecycle()
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.nh_record), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = s.enabled, onCheckedChange = { on ->
                NotifHistory.update { it.copy(enabled = on) }
                // Off also gives the listener access back, so nothing keeps running.
                if (!on) scope.launch { NotifHistory.setAccess(context, false) }
            })
        }
        Text(t(R.string.nh_keep), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90, 0).forEach { d ->
                FilterChip(
                    selected = s.keepDays == d,
                    onClick = {
                        NotifHistory.update { it.copy(keepDays = d) }
                        scope.launch { NotifHistory.prune() }
                    },
                    label = { Text(if (d == 0) t(R.string.nh_forever) else tp(R.plurals.nh_days, d, d)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(t(R.string.nh_lock), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = s.lock, onCheckedChange = { v -> NotifHistory.update { it.copy(lock = v) } })
        }
        Text(t(R.string.nh_excluded), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        if (s.excluded.isEmpty()) {
            Text(t(R.string.nh_excluded_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.excluded.sorted().forEach { pkg ->
                InputChip(
                    selected = false,
                    onClick = { NotifHistory.update { it.copy(excluded = it.excluded - pkg) } },
                    label = { Text(appLabel(pkg)) },
                    avatar = { AppIcon(pkg, 18) },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) },
                )
            }
        }
        Text(t(R.string.nh_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onDeleteAll, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.DeleteSweep, null, Modifier.padding(end = 8.dp))
            Text(t(R.string.nh_delete_all))
        }
    }
}

@Composable
private fun AppView(pkg: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val version by NotifHistory.version.collectAsStateWithLifecycle()
    val convs by produceState<List<NConv>?>(null, version) { value = NotifHistory.conversations(pkg) }
    var menu by remember { mutableStateOf(false) }
    val listStart = rememberListStart(Unit)
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Header(appLabel(pkg), onBack) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(t(R.string.nh_exclude)) }, onClick = {
                            menu = false
                            NotifHistory.update { it.copy(excluded = it.excluded + pkg) }
                        })
                        DropdownMenuItem(text = { Text(t(R.string.nh_delete_app)) }, onClick = {
                            menu = false
                            scope.launch { NotifHistory.deleteApp(pkg); onBack() }
                        })
                    }
                }
            }
        }
        itemsIndexed(convs.orEmpty(), key = { _, c -> c.conv }) { i, c ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { onOpen(c.conv) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.conv, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(timeLabel(c.last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(c.lastText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(tp(R.plurals.nh_entries, c.count, c.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (c.deleted > 0) Text(t(R.string.nh_removed_n, c.deleted), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadView(pkg: String, conv: String, onBack: () -> Unit) {
    val version by NotifHistory.version.collectAsStateWithLifecycle()
    val msgs by produceState<List<NEntry>?>(null, version) { value = NotifHistory.thread(pkg, conv) }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Header(conv, onBack) { Box(Modifier.padding(end = 12.dp)) { AppIcon(pkg, 28) } } }
        items(msgs.orEmpty(), key = { it.id }) { m -> Bubble(m) }
    }
}

@Composable
private fun Bubble(m: NEntry) {
    val bg = if (m.state == NState.Deleted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Column(
        Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (m.sender.isNotBlank() && m.sender != m.conv) {
            Text(m.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium)
        m.image?.let { Thumb(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StateBadge(m.state)
            Text(timeLabel(m.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StateBadge(state: NState) {
    val label = when (state) {
        NState.Deleted -> t(R.string.nh_deleted)
        NState.Withdrawn -> t(R.string.nh_withdrawn)
        else -> return
    }
    val deleted = state == NState.Deleted
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (deleted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (deleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun AppRow(a: NApp, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.pkg, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(appLabel(a.pkg), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tp(R.plurals.nh_entries, a.count, a.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (a.deleted > 0) Text(t(R.string.nh_removed_n, a.deleted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(timeLabel(a.last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryRow(e: NEntry, showApp: Boolean, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onClick) {
        Row {
            if (showApp) {
                AppIcon(e.pkg, 32)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.conv, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(timeLabel(e.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val line = if (e.message && e.sender.isNotBlank() && e.sender != e.conv) "${e.sender}: ${e.text}" else e.text
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                e.image?.let { Thumb(it) }
                if (e.state == NState.Deleted || e.state == NState.Withdrawn) Box(Modifier.padding(top = 4.dp)) { StateBadge(e.state) }
            }
        }
    }
}

/** Saved picture of a notification; tap opens it in the gallery/viewer. */
@Composable
private fun Thumb(path: String) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull()
        }
    }
    bmp?.let {
        Image(
            it.asImageBitmap(), null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                },
        )
    }
}

@Composable
private fun appLabel(pkg: String): String {
    val context = LocalContext.current
    return remember(pkg) {
        runCatching { context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString() }.getOrDefault(pkg)
    }
}

@Composable
private fun timeLabel(time: Long): String {
    val context = LocalContext.current
    return remember(time) {
        val flags = if (DateUtils.isToday(time)) DateUtils.FORMAT_SHOW_TIME
        else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        DateUtils.formatDateTime(context, time, flags)
    }
}
