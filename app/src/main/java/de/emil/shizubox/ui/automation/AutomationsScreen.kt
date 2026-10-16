@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.automation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import de.emil.shizubox.apps.ActivityLauncher
import de.emil.shizubox.automation.Action
import de.emil.shizubox.automation.ActionKind
import de.emil.shizubox.automation.AutomationEngine
import de.emil.shizubox.automation.Rule
import de.emil.shizubox.automation.Rules
import de.emil.shizubox.automation.Trigger
import de.emil.shizubox.automation.TriggerKind
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.system.ScreenTime
import de.emil.shizubox.t
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

@Composable
fun AutomationsScreen(shizukuReady: Boolean, onBack: () -> Unit, initialEdit: Rule? = null) {
    // The rule being edited; null = list.
    var editing by remember { mutableStateOf(initialEdit) }
    BackHandler(editing != null) { editing = null }
    AnimatedContent(editing, transitionSpec = { openClose(opening = targetState != null) }, contentKey = { it?.id }, label = "auto") { rule ->
        if (rule == null) RuleList(shizukuReady, onEdit = { editing = it }, onBack = onBack)
        else RuleEditor(rule, onDone = { editing = null })
    }
}

@Composable
private fun RuleList(shizukuReady: Boolean, onEdit: (Rule) -> Unit, onBack: () -> Unit) {
    val rules by Rules.rules.collectAsStateWithLifecycle()
    val active by AutomationEngine.active.collectAsStateWithLifecycle()
    val school by SchoolMode.state.collectAsStateWithLifecycle()
    val listStart = rememberListStart(Unit)
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text(t(R.string.sec_auto), style = MaterialTheme.typography.displaySmall)
                Text(t(R.string.au_sub), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (school.active) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.au_paused_school), color = MaterialTheme.colorScheme.tertiary) } }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        if (rules.isEmpty()) item { EmptyState(Icons.Rounded.AutoMode, t(R.string.au_empty), t(R.string.au_empty_hint)) }
        itemsIndexed(rules, key = { _, r -> r.id }) { i, r ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                GlassCard(Modifier.fillMaxWidth(), onClick = { onEdit(r) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name.ifBlank { triggerText(r.trigger) }, style = MaterialTheme.typography.titleMedium)
                            Text(t(R.string.au_when, triggerText(r.trigger)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                t(R.string.au_then, r.actions.joinToString(", ") { actionText(it) }.ifBlank { "–" }) +
                                    if (r.revert && r.canRevert) " · " + t(R.string.au_revert_short) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (r.id in active) Text(t(R.string.au_active_now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { Rules.setEnabled(r.id, it) })
                    }
                }
            }
        }
        item {
            Button(shapes = ButtonDefaults.shapes(), onClick = { onEdit(Rule(trigger = Trigger(TriggerKind.Charger), actions = emptyList())) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
                Text(t(R.string.au_new))
            }
        }
        item { Text(t(R.string.au_templates), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
        items(Rules.templates(), key = { "tpl-" + it.name }) { tpl ->
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { onEdit(tpl) }) {
                Text(tpl.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    t(R.string.au_when, triggerText(tpl.trigger)) + " → " + tpl.actions.joinToString(", ") { actionText(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(initial: Rule, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rule by remember { mutableStateOf(initial) }
    val exists = Rules.rules.value.any { it.id == initial.id }
    var addMenu by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(if (exists) t(R.string.au_edit) else t(R.string.au_new), style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            OutlinedTextField(
                value = rule.name, onValueChange = { rule = rule.copy(name = it) },
                label = { Text(t(R.string.au_name)) }, placeholder = { Text(triggerText(rule.trigger)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.au_if), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TriggerKind.entries.forEach { k ->
                        FilterChip(selected = rule.trigger.kind == k, onClick = { rule = rule.copy(trigger = rule.trigger.copy(kind = k)) }, label = { Text(k.label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                TriggerParams(rule.trigger) { rule = rule.copy(trigger = it) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.au_do), style = MaterialTheme.typography.titleMedium)
                if (rule.actions.isEmpty()) Text(t(R.string.au_no_actions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rule.actions.forEachIndexed { i, a ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.kind.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { rule = rule.copy(actions = rule.actions.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, t(R.string.delete)) }
                    }
                    ActionParams(a) { na -> rule = rule.copy(actions = rule.actions.mapIndexed { j, x -> if (j == i) na else x }) }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { addMenu = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
                        Text(t(R.string.au_add_action))
                    }
                    DropdownMenu(addMenu, onDismissRequest = { addMenu = false }) {
                        ActionKind.entries.forEach { k ->
                            DropdownMenuItem(text = { Text(k.label) }, onClick = {
                                addMenu = false
                                rule = rule.copy(actions = rule.actions + Action(k, value = if (k == ActionKind.Volume || k == ActionKind.AppVolume) 50 else if (k == ActionKind.Animations) 100 else 0))
                            })
                        }
                    }
                }
            }
        }
        if (rule.canRevert) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.au_revert), style = MaterialTheme.typography.bodyLarge)
                            Text(t(R.string.au_revert_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rule.revert, onCheckedChange = { rule = rule.copy(revert = it) })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    enabled = rule.actions.isNotEmpty() && triggerComplete(rule.trigger),
                    onClick = {
                        scope.launch {
                            // App triggers need usage access (granted via Shizuku like for screen time).
                            if (rule.trigger.kind == TriggerKind.AppOpen || rule.trigger.kind == TriggerKind.AppClose) runCatching { ScreenTime.ensureAccess(context) }
                            Rules.save(rule)
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(t(R.string.save)) }
                OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = rule.actions.isNotEmpty(), onClick = { scope.launch { AutomationEngine.tryNow(rule) } }) {
                    Text(t(R.string.au_try))
                }
            }
        }
        if (exists) {
            item {
                TextButton(onClick = { Rules.delete(rule.id); onDone() }) { Text(t(R.string.au_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun triggerComplete(tr: Trigger) = when (tr.kind) {
    TriggerKind.AppOpen, TriggerKind.AppClose -> tr.pkg.isNotBlank()
    TriggerKind.Time -> tr.days != 0
    else -> true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerParams(tr: Trigger, onChange: (Trigger) -> Unit) {
    when (tr.kind) {
        TriggerKind.AppOpen, TriggerKind.AppClose -> AppButton(tr.pkg) { onChange(tr.copy(pkg = it)) }
        TriggerKind.Time -> {
            var pick by remember { mutableStateOf(false) }
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { pick = true }) { Text(timeText(tr.minute), style = MaterialTheme.typography.titleLarge) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayNames().forEachIndexed { i, name ->
                    FilterChip(selected = tr.days shr i and 1 == 1, onClick = { onChange(tr.copy(days = tr.days xor (1 shl i))) }, label = { Text(name) })
                }
            }
            if (pick) {
                val state = rememberTimePickerState(tr.minute / 60, tr.minute % 60, is24Hour = true)
                AlertDialog(
                    onDismissRequest = { pick = false },
                    confirmButton = { TextButton(onClick = { onChange(tr.copy(minute = state.hour * 60 + state.minute)); pick = false }) { Text(t(R.string.ok)) } },
                    dismissButton = { TextButton(onClick = { pick = false }) { Text(t(R.string.cancel)) } },
                    text = { TimePicker(state) },
                )
            }
        }
        TriggerKind.Battery -> {
            Text(t(R.string.au_below, tr.percent), style = MaterialTheme.typography.bodyLarge)
            Slider(value = tr.percent.toFloat(), onValueChange = { onChange(tr.copy(percent = it.toInt())) }, valueRange = 5f..95f, steps = 17)
        }
        TriggerKind.Headphones, TriggerKind.Charger, TriggerKind.Bluetooth, TriggerKind.Wifi -> {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = tr.connected, onClick = { onChange(tr.copy(connected = true)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(t(R.string.au_connected)) }
                SegmentedButton(selected = !tr.connected, onClick = { onChange(tr.copy(connected = false)) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(t(R.string.au_disconnected)) }
            }
            if (tr.kind == TriggerKind.Bluetooth || tr.kind == TriggerKind.Wifi) {
                val state by AutomationEngine.state.collectAsStateWithLifecycle()
                OutlinedTextField(
                    value = tr.name, onValueChange = { onChange(tr.copy(name = it)) }, singleLine = true,
                    label = { Text(if (tr.kind == TriggerKind.Wifi) t(R.string.au_ssid) else t(R.string.au_bt_name)) },
                    placeholder = { Text(t(R.string.au_any)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // Suggestions from what is connected right now.
                val now = if (tr.kind == TriggerKind.Wifi) listOfNotNull(state.ssid?.takeIf { it.isNotBlank() }) else state.bluetooth.toList()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    now.forEach { n -> FilterChip(selected = tr.name == n, onClick = { onChange(tr.copy(name = n)) }, label = { Text(n) }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionParams(a: Action, onChange: (Action) -> Unit) {
    @Composable
    fun onOff(onLabel: String = t(R.string.on), offLabel: String = t(R.string.off)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = a.on, onClick = { onChange(a.copy(on = true)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(onLabel) }
            SegmentedButton(selected = !a.on, onClick = { onChange(a.copy(on = false)) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(offLabel) }
        }
    }
    when (a.kind) {
        ActionKind.Gaming, ActionKind.Boost, ActionKind.Grayscale, ActionKind.Dnd, ActionKind.StayAwake -> onOff()
        ActionKind.AppVolume -> {
            AppButton(a.pkg) { onChange(a.copy(pkg = it)) }
            Text("${a.value} %", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Slider(value = a.value.toFloat(), onValueChange = { onChange(a.copy(value = it.toInt())) }, valueRange = 0f..100f)
        }
        ActionKind.Volume -> {
            Text("${a.value} %", style = MaterialTheme.typography.bodyMedium)
            Slider(value = a.value.toFloat(), onValueChange = { onChange(a.copy(value = it.toInt())) }, valueRange = 0f..100f)
        }
        ActionKind.Refresh -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefreshMode.entries.forEach { m -> FilterChip(selected = a.value == m.ordinal, onClick = { onChange(a.copy(value = m.ordinal)) }, label = { Text(m.label) }) }
        }
        ActionKind.Animations -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 50, 100, 150).forEach { v ->
                FilterChip(selected = a.value == v, onClick = { onChange(a.copy(value = v)) }, label = { Text(if (v == 0) t(R.string.off) else "${v / 100f}×".replace(".0×", "×")) })
            }
        }
        ActionKind.NetBlock -> {
            AppButton(a.pkg) { onChange(a.copy(pkg = it)) }
            Spacer(Modifier.height(4.dp))
            onOff(t(R.string.nb_block), t(R.string.nb_allow))
        }
        ActionKind.StartApp -> AppButton(a.pkg) { onChange(a.copy(pkg = it)) }
        ActionKind.StartActivity -> {
            val favs by ActivityLauncher.favorites.collectAsStateWithLifecycle()
            OutlinedTextField(
                value = a.text, onValueChange = { onChange(a.copy(text = it.trim())) }, singleLine = true,
                label = { Text(t(R.string.au_activity)) }, placeholder = { Text("com.android.settings/.Settings") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (favs.isNotEmpty()) {
                Text(t(R.string.au_from_favorites), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    favs.sorted().forEach { f ->
                        FilterChip(selected = a.text == f, onClick = { onChange(a.copy(text = f)) }, label = { Text(f.substringAfterLast('.'), maxLines = 1) }, leadingIcon = { AppIcon(f.substringBefore('/'), 18) })
                    }
                }
            }
        }
        ActionKind.Shell -> OutlinedTextField(
            value = a.text, onValueChange = { onChange(a.copy(text = it)) },
            label = { Text(t(R.string.au_command)) }, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shows the chosen app; tap opens a searchable list of launchable apps. */
@Composable
private fun AppButton(pkg: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        if (pkg.isNotBlank()) {
            AppIcon(pkg, 24)
            Spacer(Modifier.width(8.dp))
            Text(appLabel(pkg), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(t(R.string.au_pick_app))
        }
    }
    if (open) AppPicker(onDismiss = { open = false }) { onPick(it); open = false }
}

@Composable
internal fun AppPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } },
        title = { Text(t(R.string.au_pick_app)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t(R.string.search)) },
                    shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    val list = apps.orEmpty().filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }
                    items(list, key = { it.first }) { (p, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 8.dp),
                        ) {
                            AppIcon(p, 32)
                            Spacer(Modifier.width(12.dp))
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------- texts

/** Monday … Sunday, short, in the app language. */
private fun dayNames() = java.time.DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }

private fun timeText(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun appLabel(pkg: String): String = runCatching {
    val pm = de.emil.shizubox.Str.context.packageManager
    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
}.getOrDefault(pkg)

private fun triggerText(tr: Trigger): String = when (tr.kind) {
    TriggerKind.AppOpen -> t(R.string.au_tt_app_open, if (tr.pkg.isBlank()) "…" else appLabel(tr.pkg))
    TriggerKind.AppClose -> t(R.string.au_tt_app_close, if (tr.pkg.isBlank()) "…" else appLabel(tr.pkg))
    TriggerKind.Time -> timeText(tr.minute) + if (tr.days and 0b1111111 == 0b1111111) "" else " (" +
        dayNames().filterIndexed { i, _ -> tr.days shr i and 1 == 1 }.joinToString(" ") + ")"
    TriggerKind.Battery -> t(R.string.au_below, tr.percent)
    else -> {
        val what = when (tr.kind) {
            TriggerKind.Headphones -> t(R.string.au_t_headphones)
            TriggerKind.Charger -> t(R.string.au_t_charger)
            TriggerKind.Bluetooth -> tr.name.ifBlank { t(R.string.au_t_bluetooth) }
            else -> tr.name.ifBlank { t(R.string.au_t_wifi) }
        }
        "$what " + if (tr.connected) t(R.string.au_connected) else t(R.string.au_disconnected)
    }
}

private fun actionText(a: Action): String = when (a.kind) {
    ActionKind.Volume -> "${a.kind.label} ${a.value} %"
    ActionKind.AppVolume -> appLabel(a.pkg) + " ${a.value} %"
    ActionKind.Refresh -> RefreshMode.entries.getOrElse(a.value) { RefreshMode.Adaptive }.label
    ActionKind.Animations -> "${a.kind.label} " + if (a.value == 0) t(R.string.off) else "${a.value / 100f}×"
    ActionKind.NetBlock -> (if (a.on) t(R.string.nb_block) else t(R.string.nb_allow)) + " (" + appLabel(a.pkg) + ")"
    ActionKind.StartApp -> t(R.string.au_tt_start, appLabel(a.pkg))
    ActionKind.StartActivity -> t(R.string.au_tt_start, a.text.substringAfterLast('.'))
    ActionKind.Shell -> "$ " + a.text.take(30)
    else -> "${a.kind.label} " + if (a.on) t(R.string.on) else t(R.string.off)
}
