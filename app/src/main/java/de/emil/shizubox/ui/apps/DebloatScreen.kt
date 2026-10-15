@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.Debloat
import de.emil.shizubox.apps.DebloatItem
import de.emil.shizubox.apps.DebloatRating
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

private enum class DbFilter { Safe, Caution, DontTouch, Frozen }

@Composable
private fun ratingColor(r: DebloatRating) = when (r) {
    DebloatRating.Safe -> MaterialTheme.colorScheme.primary
    DebloatRating.Caution -> Color(0xFFE0A030)
    DebloatRating.DontTouch -> MaterialTheme.colorScheme.error
}

private fun ratingLabel(r: DebloatRating) = when (r) {
    DebloatRating.Safe -> t(R.string.db_safe)
    DebloatRating.Caution -> t(R.string.db_caution)
    DebloatRating.DontTouch -> t(R.string.db_dont)
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DebloatScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<DebloatItem>?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { items = runCatching { Debloat.items() }.getOrDefault(emptyList()) }
    val log by Debloat.log.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(DbFilter.Safe) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    val listStart = rememberListStart(filter to (items != null))

    fun run(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            runCatching { block() }
            selection = emptySet()
            busy = false
            reload++
        }
    }

    val visible = items.orEmpty().filter { i ->
        when (filter) {
            DbFilter.Frozen -> i.frozen
            DbFilter.Safe -> i.rating == DebloatRating.Safe && !i.frozen
            DbFilter.Caution -> i.rating == DebloatRating.Caution && !i.frozen
            DbFilter.DontTouch -> i.rating == DebloatRating.DontTouch && !i.frozen
        } && (query.isBlank() || i.label.contains(query, true) || i.pkg.contains(query, true))
    }
    val open = log.filter { !it.undone }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.db_title), style = MaterialTheme.typography.headlineSmall)
            }
            Text(t(R.string.db_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            Text(t(R.string.db_credit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(8.dp))
        }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_for_changes)) } }
        // Log with undo.
        if (log.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth().animateContentSize(Motion.smooth()), onClick = { showLog = !showLog }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.db_log), style = MaterialTheme.typography.titleMedium)
                            Text(tp(R.plurals.db_log_n, open.size, open.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (open.isNotEmpty()) {
                            OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = shizukuReady && !busy, onClick = { run { Debloat.undoAll() } }) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.padding(end = 6.dp).size(18.dp))
                                Text(t(R.string.db_undo_all))
                            }
                        }
                    }
                    if (showLog) {
                        log.forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                AppIcon(e.pkg, 28)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(e.time)) +
                                            " · " + if (e.undone) t(R.string.db_undone) else t(R.string.f_frozen),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!e.undone) TextButton(enabled = shizukuReady && !busy, onClick = { run { Debloat.undo(listOf(e)) } }) { Text(t(R.string.db_undo)) }
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t(R.string.search)) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
                shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                DbFilter.entries.forEach { f ->
                    val n = items.orEmpty().count {
                        when (f) {
                            DbFilter.Frozen -> it.frozen
                            DbFilter.Safe -> it.rating == DebloatRating.Safe && !it.frozen
                            DbFilter.Caution -> it.rating == DebloatRating.Caution && !it.frozen
                            DbFilter.DontTouch -> it.rating == DebloatRating.DontTouch && !it.frozen
                        }
                    }
                    val label = when (f) {
                        DbFilter.Frozen -> t(R.string.f_frozen)
                        DbFilter.Safe -> t(R.string.db_safe)
                        DbFilter.Caution -> t(R.string.db_caution)
                        DbFilter.DontTouch -> t(R.string.db_dont)
                    }
                    FilterChip(selected = filter == f, onClick = { filter = f; selection = emptySet() }, label = { Text("$label ($n)") })
                }
            }
            Text(
                when (filter) {
                    DbFilter.Safe -> t(R.string.db_safe_hint)
                    DbFilter.Caution -> t(R.string.db_caution_hint)
                    DbFilter.DontTouch -> t(R.string.db_dont_hint)
                    DbFilter.Frozen -> t(R.string.db_frozen_hint)
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
        if (selection.isNotEmpty()) {
            stickyHeader(key = "dbsel") {
                GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Rounded.Close, t(R.string.select_none)) }
                        Text(tp(R.plurals.sel_n, selection.size, selection.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady && !busy, onClick = {
                            val chosen = items.orEmpty().filter { it.pkg in selection }
                            run { Debloat.freeze(chosen) }
                        }) { Text(t(R.string.freeze)) }
                    }
                }
            }
        }
        if (items == null) item { Text(t(R.string.loading_apps), modifier = Modifier.padding(8.dp)) }
        itemsIndexed(visible, key = { _, i -> i.pkg }) { index, i ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(index, listStart)) {
                DebloatRow(
                    i,
                    selected = i.pkg in selection,
                    canSelect = shizukuReady && !i.frozen && !i.locked && i.rating != DebloatRating.DontTouch,
                    busy = busy,
                    onSelect = { selection = if (it) selection + i.pkg else selection - i.pkg },
                    onUnfreeze = {
                        run {
                            val entry = log.firstOrNull { it.pkg == i.pkg && !it.undone }
                            if (entry != null) Debloat.undo(listOf(entry)) else de.emil.shizubox.apps.Apps.unfreeze(listOf(i.pkg))
                        }
                    },
                    shizukuReady = shizukuReady,
                )
            }
        }
    }
}

@Composable
private fun DebloatRow(
    i: DebloatItem,
    selected: Boolean,
    canSelect: Boolean,
    busy: Boolean,
    shizukuReady: Boolean,
    onSelect: (Boolean) -> Unit,
    onUnfreeze: () -> Unit,
) {
    var details by remember { mutableStateOf(false) }
    GlassCard(
        Modifier.fillMaxWidth().animateContentSize(Motion.smooth()),
        contentPadding = 12.dp,
        tint = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { if (canSelect) onSelect(!selected) else details = !details },
        onLongClick = { details = !details },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(i.pkg, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(i.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        ratingLabel(i.rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ratingColor(i.rating)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(i.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                i.locked -> Icon(Icons.Rounded.Lock, t(R.string.db_locked), tint = MaterialTheme.colorScheme.outline)
                i.frozen -> TextButton(enabled = shizukuReady && !busy, onClick = onUnfreeze) { Text(t(R.string.unfreeze)) }
                canSelect -> Checkbox(checked = selected, onCheckedChange = onSelect)
            }
        }
        val text = i.explainRes?.let { t(it) }
        Text(
            text ?: i.uad.lineSequence().first(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (details) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        AnimatedVisibility(details, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
            Column {
                if (i.locked) Text(t(R.string.db_locked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                if (text != null) {
                    Text(t(R.string.db_uad_details), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    Text(i.uad, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
