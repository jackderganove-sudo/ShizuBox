@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.rememberAppLabel
import de.emil.shizubox.clipboard.Clip
import de.emil.shizubox.clipboard.ClipHistory
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipboardScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by ClipHistory.settings.collectAsStateWithLifecycle()
    val listening by ClipHistory.listening.collectAsStateWithLifecycle()
    val version by ClipHistory.version.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(!s.lock) }
    var copied by remember { mutableStateOf<Long?>(null) }
    val clips by produceState<List<Clip>?>(null, version, query) { value = ClipHistory.clips(query) }
    val listStart = rememberListStart(Unit)

    if (s.lock && !unlocked) {
        val prompt = {
            BiometricPrompt.Builder(context).setTitle(t(R.string.cb_title))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
                .authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { unlocked = true }
                })
        }
        LaunchedEffect(Unit) { runCatching { prompt() } }
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
            EmptyState(Icons.Rounded.Lock, t(R.string.nh_locked), modifier = Modifier.padding(top = 48.dp))
            Button(shapes = ButtonDefaults.shapes(), onClick = { runCatching { prompt() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t(R.string.nh_unlock)) }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.cb_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (s.enabled) IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Rounded.Settings, t(R.string.settings)) }
            }
        }
        if (!s.enabled) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(t(R.string.cb_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(t(R.string.cb_toast_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Text(t(R.string.pv_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = { ClipHistory.update { it.copy(enabled = true) } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(t(R.string.cb_enable))
                    }
                    if (!shizukuReady) Text(t(R.string.shizuku_must), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            return@LazyColumn
        }
        if (!listening) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.cb_not_listening), color = MaterialTheme.colorScheme.error) } }
        item {
            AnimatedVisibility(showSettings, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t(R.string.cb_record), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = s.enabled, onCheckedChange = { v -> ClipHistory.update { it.copy(enabled = v) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.cb_hide_toast), style = MaterialTheme.typography.bodyMedium)
                            Text(t(R.string.cb_hide_toast_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = remember(version) { ClipHistory.hidesAccessToast() }, enabled = shizukuReady, onCheckedChange = { v -> scope.launch { ClipHistory.setHideAccessToast(v) } })
                    }
                    Text(t(R.string.nh_keep), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 7, 30, 0).forEach { d ->
                            FilterChip(selected = s.keepDays == d, onClick = { ClipHistory.update { it.copy(keepDays = d) } }, label = { Text(if (d == 0) t(R.string.nh_forever) else tp(R.plurals.nh_days, d, d)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(t(R.string.nh_lock), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = s.lock, onCheckedChange = { v -> ClipHistory.update { it.copy(lock = v) } })
                    }
                    Text(t(R.string.nh_excluded), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(t(R.string.cb_never), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.excluded.sorted().forEach { pkg ->
                            InputChip(
                                selected = false, onClick = { ClipHistory.update { it.copy(excluded = it.excluded - pkg) } },
                                label = { Text(rememberAppLabel(pkg)) }, avatar = { AppIcon(pkg, 18) },
                                trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                    Text(t(R.string.pv_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { ClipHistory.deleteAll() } }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(t(R.string.cb_delete_all))
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t(R.string.search)) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
            )
        }
        val list = clips
        if (list != null && list.isEmpty()) item { EmptyState(Icons.Rounded.ContentPaste, t(R.string.cb_empty), t(R.string.cb_empty_hint)) }
        itemsIndexed(list.orEmpty(), key = { _, c -> c.id }) { i, c ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                ClipRow(c, copied == c.id) {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ShizuBox", c.text))
                    copied = c.id
                }
            }
        }
    }
}

@Composable
private fun ClipRow(c: Clip, copied: Boolean, onCopy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onCopy) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, color = if (c.isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (c.pinned) { Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                    if (c.source.isNotBlank()) { AppIcon(c.source, 16); Spacer(Modifier.width(6.dp)) }
                    Text(
                        (if (copied) t(R.string.el_copied) + " · " else "") +
                            DateUtils.getRelativeTimeSpanString(c.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE),
                        style = MaterialTheme.typography.labelSmall, color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (c.pinned) t(R.string.cb_unpin) else t(R.string.cb_pin)) }, onClick = { menu = false; scope.launch { ClipHistory.setPinned(c.id, !c.pinned) } })
                    DropdownMenuItem(text = { Text(t(R.string.el_share)) }, onClick = {
                        menu = false
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, c.text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                    if (c.source.isNotBlank() && c.source != context.packageName) {
                        DropdownMenuItem(text = { Text(t(R.string.cb_exclude_app)) }, onClick = { menu = false; ClipHistory.update { it.copy(excluded = it.excluded + c.source) } })
                    }
                    DropdownMenuItem(text = { Text(t(R.string.delete)) }, onClick = { menu = false; scope.launch { ClipHistory.delete(c.id) } })
                }
            }
        }
    }
}
