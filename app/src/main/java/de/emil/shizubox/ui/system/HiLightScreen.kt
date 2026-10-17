@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.rememberAppLabel
import de.emil.shizubox.hilight.AppRule
import de.emil.shizubox.hilight.ContactRule
import de.emil.shizubox.hilight.HiLight
import de.emil.shizubox.hilight.HiLightConfig
import de.emil.shizubox.hilight.HiLightSettings
import de.emil.shizubox.hilight.Layer
import de.emil.shizubox.hilight.LightRequest
import de.emil.shizubox.hilight.Pattern
import de.emil.shizubox.hilight.Patterns
import de.emil.shizubox.notifications.NotifHistory
import de.emil.shizubox.t
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Pattern.label() = t(when (this) {
    Pattern.Solid -> R.string.hl_p_solid
    Pattern.Pulse -> R.string.hl_p_pulse
    Pattern.Breathe -> R.string.hl_p_breathe
    Pattern.Blink -> R.string.hl_p_blink
    Pattern.Chase -> R.string.hl_p_chase
    Pattern.Comet -> R.string.hl_p_comet
    Pattern.Wave -> R.string.hl_p_wave
    Pattern.Heartbeat -> R.string.hl_p_heartbeat
    Pattern.Rainbow -> R.string.hl_p_rainbow
    Pattern.Progress -> R.string.hl_p_progress
    Pattern.Scan -> R.string.hl_p_scan
})

private val PRESETS = listOf(
    0xFFFFFFFF, 0xFFFF1744, 0xFFFF9100, 0xFFFFEA00, 0xFFFFFC00, 0xFF00E676, 0xFF25D366, 0xFF00E5FF,
    0xFF1E88E5, 0xFF2979FF, 0xFF651FFF, 0xFFD500F9, 0xFFFF4081,
).map { it.toInt() }

/** The 8 LEDs on screen, animated with the same maths as the hardware (only while visible). */
@Composable
fun LedBar(pattern: Pattern, color: Int, modifier: Modifier = Modifier, progress: Double = 0.4) {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pattern, color) {
        val start = System.currentTimeMillis()
        while (true) { t = System.currentTimeMillis() - start; delay(50) }
    }
    val frame = Patterns.frame(pattern, intArrayOf(color), 1500, t, 8, progress)
    val off = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val step = size.width / 8
        val r = minOf(step, size.height) * 0.34f
        frame.forEachIndexed { i, c ->
            val col = Color(c)
            val lit = Patterns.visible(intArrayOf(c))
            drawCircle(if (lit) col else off, r, Offset(step * (i + 0.5f), size.height / 2))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiLightScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by HiLightSettings.state.collectAsStateWithLifecycle()
    val last by HiLight.last.collectAsStateWithLifecycle()
    var previewPattern by remember { mutableStateOf(Pattern.Pulse) }
    var previewColor by remember { mutableStateOf(0xFF2979FF.toInt()) }
    var pickColor by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var editApp by remember { mutableStateOf<AppRule?>(null) }
    var editContact by remember { mutableStateOf<ContactRule?>(null) }
    var addProgressApp by remember { mutableStateOf(false) }
    var addRuleApp by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val studio = remember { HiLight.studioInstalled(context) }
    val access by produceState(NotifHistory.hasAccess(context), message) { value = NotifHistory.hasAccess(context) }
    fun set(f: (HiLightConfig) -> HiLightConfig) = HiLightSettings.update(f)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text("HiLight", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Text(t(R.string.hl_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(R.string.hl_enabled), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = cfg.enabled, onCheckedChange = { v -> set { it.copy(enabled = v) } })
                }
                if (!shizukuReady) Text(t(R.string.shizuku_must), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (studio) Text(t(R.string.hl_studio_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                if (!access) {
                    Text(t(R.string.hl_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    TextButton(enabled = shizukuReady, onClick = { scope.launch { NotifHistory.setAccess(context, true); message = "access" } }) { Text(t(R.string.hl_grant)) }
                }
                Text(t(R.string.hl_brightness, (cfg.brightness * 100).toInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(value = cfg.brightness, onValueChange = { v -> set { it.copy(brightness = v) } }, valueRange = 0.1f..1f)
                SwitchRow(t(R.string.hl_facedown), t(R.string.hl_facedown_sub), cfg.faceDownOnly) { v -> set { it.copy(faceDownOnly = v) } }
                Text(t(R.string.hl_alert_s, cfg.alertSeconds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(value = cfg.alertSeconds.toFloat(), onValueChange = { v -> set { it.copy(alertSeconds = v.toInt()) } }, valueRange = 3f..60f)
                Text(t(R.string.hl_ambient_s, cfg.ambientSeconds), style = MaterialTheme.typography.bodyMedium)
                Slider(value = cfg.ambientSeconds.toFloat(), onValueChange = { v -> set { it.copy(ambientSeconds = v.toInt()) } }, valueRange = 5f..300f)
                Text(t(R.string.hl_safety), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = { scope.launch { HiLight.reset(); message = t(R.string.hl_reset_done) } }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.padding(end = 8.dp))
                    Text(t(R.string.hl_reset))
                }
                message?.takeIf { it != "access" }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                last?.let { Text(t(R.string.hl_last, reasonText(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.hl_preview), style = MaterialTheme.typography.titleMedium)
                LedBar(previewPattern, previewColor, Modifier.padding(vertical = 8.dp))
                PatternChips(previewPattern) { previewPattern = it }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    ColorDot(previewColor, 36) { pickColor = { previewColor = it } }
                    Spacer(Modifier.width(12.dp))
                    Button(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = {
                        scope.launch {
                            val ok = HiLight.show(LightRequest("preview", Layer.Preview, previewPattern, intArrayOf(previewColor), 8000, 0.5))
                            message = if (ok) t(R.string.hl_preview_on) else null
                        }
                    }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(end = 8.dp))
                        Text(t(R.string.hl_play))
                    }
                    TextButton(onClick = { scope.launch { HiLight.off() } }) { Text(t(R.string.hl_stop)) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.hl_progress), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.hl_progress_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow(t(R.string.hl_own), t(R.string.hl_own_sub), cfg.ownProgress) { v -> set { it.copy(ownProgress = v) } }
                val sources = (HiLightConfig.DEFAULT_PROGRESS + cfg.progressSources).sorted()
                sources.forEach { src ->
                    val label = when (src) {
                        HiLightConfig.SYSTEM_UPDATE -> t(R.string.hl_sysupdate)
                        HiLightConfig.PLAY_STORE -> "Play Store"
                        else -> rememberAppLabel(src.substringBefore('/'))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(src.substringBefore('/'), 28)
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        Switch(checked = src in cfg.progressSources, onCheckedChange = { v -> set { it.copy(progressSources = if (v) it.progressSources + src else it.progressSources - src) } })
                    }
                }
                TextButton(onClick = { addProgressApp = true }) { Icon(Icons.Rounded.Add, null, Modifier.padding(end = 4.dp)); Text(t(R.string.hl_add_app)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.hl_apps), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.hl_apps_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                cfg.appRules.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editApp = r }.padding(vertical = 4.dp)) {
                        AppIcon(r.pkg, 32)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(rememberAppLabel(r.pkg), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Pattern.of(r.pattern).label() + if (r.faceDown) " · " + t(R.string.hl_facedown_short) else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ColorDot(r.color, 24) { editApp = r }
                        Switch(checked = r.on, onCheckedChange = { v -> set { c -> c.copy(appRules = c.appRules.map { if (it.pkg == r.pkg) it.copy(on = v) else it }) } }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                TextButton(onClick = { addRuleApp = true }) { Icon(Icons.Rounded.Add, null, Modifier.padding(end = 4.dp)); Text(t(R.string.hl_add_app)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.hl_contacts), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.hl_contacts_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                cfg.contactRules.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editContact = r }.padding(vertical = 4.dp)) {
                        ColorDot(r.color, 28) { editContact = r }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium)
                            Text(Pattern.of(r.pattern).label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { set { c -> c.copy(contactRules = c.contactRules - r) } }) { Icon(Icons.Rounded.Close, t(R.string.cl_remove)) }
                    }
                }
                TextButton(onClick = { editContact = ContactRule("", 0xFFFF4081.toInt()) }) { Icon(Icons.Rounded.Add, null, Modifier.padding(end = 4.dp)); Text(t(R.string.hl_add_contact)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow(t(R.string.hl_charging), t(R.string.hl_charging_sub), cfg.charging) { v -> set { it.copy(charging = v) } }
                SwitchRow(t(R.string.hl_privacy), t(R.string.hl_privacy_sub), cfg.privacy) { v -> set { it.copy(privacy = v) } }
                if (cfg.privacy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = cfg.privacyCamera, onClick = { set { it.copy(privacyCamera = !it.privacyCamera) } }, label = { Text(t(R.string.pv_camera)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = cfg.privacyMic, onClick = { set { it.copy(privacyMic = !it.privacyMic) } }, label = { Text(t(R.string.pv_mic)) })
                        Spacer(Modifier.weight(1f))
                        ColorDot(cfg.privacyColor, 28) { pickColor = { c -> set { it.copy(privacyColor = c) } } }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.hl_quiet), style = MaterialTheme.typography.titleMedium)
                SwitchRow(t(R.string.hl_q_dnd), null, cfg.quietDnd) { v -> set { it.copy(quietDnd = v) } }
                SwitchRow(t(R.string.hl_q_night), null, cfg.quietNight) { v -> set { it.copy(quietNight = v) } }
                if (cfg.quietNight) Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeButton(cfg.nightFrom) { m -> set { it.copy(nightFrom = m) } }
                    Text(" – ")
                    TimeButton(cfg.nightTo) { m -> set { it.copy(nightTo = m) } }
                }
                SwitchRow(t(R.string.hl_q_power), null, cfg.quietPowerSave) { v -> set { it.copy(quietPowerSave = v) } }
                SwitchRow(t(R.string.hl_q_battery, cfg.lowBatteryPercent), null, cfg.quietLowBattery) { v -> set { it.copy(quietLowBattery = v) } }
                Text(t(R.string.hl_q_school), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    pickColor?.let { cb -> ColorPickerDialog(onDismiss = { pickColor = null }) { cb(it); pickColor = null } }
    if (addProgressApp) de.emil.shizubox.ui.automation.AppPicker(onDismiss = { addProgressApp = false }) { pkg ->
        addProgressApp = false
        set { it.copy(progressSources = it.progressSources + pkg) }
    }
    if (addRuleApp) de.emil.shizubox.ui.automation.AppPicker(onDismiss = { addRuleApp = false }) { pkg ->
        addRuleApp = false
        editApp = cfg.appRules.firstOrNull { it.pkg == pkg } ?: AppRule(pkg, PRESETS[8])
    }
    editApp?.let { r ->
        RuleDialog(rememberAppLabel(r.pkg), r.color, Pattern.of(r.pattern), r.faceDown, null,
            onDelete = { set { c -> c.copy(appRules = c.appRules.filterNot { it.pkg == r.pkg }) }; editApp = null },
            onDismiss = { editApp = null }) { color, pattern, faceDown, _ ->
            set { c -> c.copy(appRules = c.appRules.filterNot { it.pkg == r.pkg } + r.copy(color = color, pattern = pattern.key, faceDown = faceDown)) }
            editApp = null
        }
    }
    editContact?.let { r ->
        RuleDialog(t(R.string.hl_contact), r.color, Pattern.of(r.pattern), r.faceDown, r.name,
            onDelete = { set { c -> c.copy(contactRules = c.contactRules - r) }; editContact = null },
            onDismiss = { editContact = null }) { color, pattern, faceDown, name ->
            if (!name.isNullOrBlank()) set { c -> c.copy(contactRules = c.contactRules - r + ContactRule(name.trim(), color, pattern.key, faceDown)) }
            editContact = null
        }
    }
}

private fun reasonText(r: String) = when (r) {
    "ok" -> t(R.string.hl_r_ok)
    "busy" -> t(R.string.hl_r_busy)
    "school" -> t(R.string.hl_r_school)
    "off" -> t(R.string.hl_r_off)
    "dnd", "night", "powersave", "battery" -> t(R.string.hl_r_quiet)
    "facedown" -> t(R.string.hl_r_facedown)
    "lower" -> t(R.string.hl_r_lower)
    "shizuku" -> t(R.string.shizuku_must)
    else -> r
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeButton(minute: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
        TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minute / 60, minute % 60, DateFormat.is24HourFormat(context)).show()
    }) { Text("%02d:%02d".format(minute / 60, minute % 60)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatternChips(selected: Pattern, onPick: (Pattern) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pattern.entries.filter { it.shown }.forEach { p -> FilterChip(selected = p == selected, onClick = { onPick(p) }, label = { Text(p.label()) }) }
    }
}

@Composable
fun ColorDot(color: Int, sizeDp: Int, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).background(Color(color), CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size((sizeDp / 2).dp)) }
}

/** Preset colours plus the wallpaper's (Material You) as suggestions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val wallpaper = remember { HiLight.wallpaperColors(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.hl_color)) },
        text = {
            Column {
                if (wallpaper.isNotEmpty()) {
                    Text(t(R.string.hl_color_wallpaper), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        wallpaper.forEach { c -> ColorDot(c, 40) { onPick(c) } }
                    }
                }
                Text(t(R.string.hl_color_presets), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    PRESETS.forEach { c -> ColorDot(c, 40) { onPick(c) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } },
    )
}

/** Colour, pattern, face-down (and name for contacts) of one rule, with a live LED preview. */
@Composable
private fun RuleDialog(
    title: String, color0: Int, pattern0: Pattern, faceDown0: Boolean, name0: String?,
    onDelete: () -> Unit, onDismiss: () -> Unit, onSave: (Int, Pattern, Boolean, String?) -> Unit,
) {
    var color by remember { mutableStateOf(color0) }
    var pattern by remember { mutableStateOf(pattern0) }
    var faceDown by remember { mutableStateOf(faceDown0) }
    var name by remember { mutableStateOf(name0) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (name != null) OutlinedTextField(name!!, { name = it }, label = { Text(t(R.string.hl_contact_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LedBar(pattern, color, Modifier.padding(vertical = 8.dp))
                PatternChips(pattern) { pattern = it }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    ColorDot(color, 36) { picking = true }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                        scope.launch { HiLight.show(LightRequest("preview", Layer.Preview, pattern, intArrayOf(color), 6000)) }
                    }) { Text(t(R.string.hl_test)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(t(R.string.hl_facedown_rule), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = faceDown, onCheckedChange = { faceDown = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(color, pattern, faceDown, name) }) { Text(t(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDelete) { Text(t(R.string.cl_remove)) } },
    )
    if (picking) ColorPickerDialog(onDismiss = { picking = false }) { color = it; picking = false }
}
