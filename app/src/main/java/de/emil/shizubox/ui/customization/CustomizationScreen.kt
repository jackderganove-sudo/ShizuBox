package de.emil.shizubox.ui.customization

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.customization.CustomState
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizationScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CustomState?>(null) }
    val pending by Customization.pending.collectAsStateWithLifecycle()

    suspend fun reload() {
        state = runCatching { Customization.read() }.getOrNull()
    }
    // Re-read after every change and after a display change was confirmed or reverted.
    LaunchedEffect(shizukuReady, pending) { if (shizukuReady) reload() }

    fun change(block: suspend (CustomState) -> Unit) {
        val s = state ?: return
        scope.launch {
            block(s)
            reload()
        }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text("Customization", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Änderungen wirken sofort.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val s = state
        if (!shizukuReady || s == null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (shizukuReady) CircularProgressIndicator(Modifier.padding(end = 16.dp))
                        Text(if (shizukuReady) "Lese Einstellungen …" else "Shizuku muss verbunden sein.")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            ChipCard(
                title = "Animationsgeschwindigkeit",
                hint = "Kleiner = schneller, 0 = aus",
                options = Customization.ANIMATION_SCALES,
                selected = s.animationScale,
                label = { if (it == 0f) "Aus" else "${fmt(it)}×" },
                onSelect = { v -> change { Customization.setAnimationScale(v) } },
            )
        }
        item {
            ChipCard(
                title = "Bildwiederholrate",
                hint = when (s.refresh) {
                    RefreshMode.Adaptive -> "Passt sich an (bis 120 Hz), spart Akku"
                    RefreshMode.Fixed60 -> "Max. 60 Hz, spart am meisten Akku"
                    RefreshMode.AlwaysMax -> "Immer 120 Hz, am flüssigsten, braucht mehr Akku"
                },
                options = RefreshMode.entries,
                selected = s.refresh,
                label = { it.label },
                onSelect = { v -> change { Customization.setRefresh(v) } },
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Schrift", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Customization.FONT_SCALES.forEach { v ->
                        FilterChip(
                            selected = kotlin.math.abs(s.fontScale - v) < 0.01f,
                            onClick = { change { Customization.setFontScale(v) } },
                            label = { Text("${(v * 100).roundToInt()} %") },
                        )
                    }
                }
                ToggleRow("Fett", s.boldText) { b -> change { Customization.setBoldText(b) } }
            }
        }
        item { DensityCard(s, enabled = pending == null) { d -> change { Customization.setDensity(it, d) } } }
        item {
            ChipCard(
                title = "Auflösung",
                hint = "${s.width}×${s.height} · weniger spart Akku, Inhalte bleiben gleich groß",
                options = Customization.RESOLUTION_PERCENTS,
                selected = s.resolutionPercent,
                label = { "$it %" },
                enabled = pending == null,
                onSelect = { v -> change { Customization.setResolution(it, v) } },
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Statusleiste", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Einzelne Icons lassen sich unter Android 17 nicht mehr ausblenden, nur diese Gruppen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val sb = s.statusBar
                ToggleRow("Systemsymbole zeigen", !sb.systemIcons) { show ->
                    change { Customization.setStatusBar(sb.copy(systemIcons = !show)) }
                }
                ToggleRow("Uhr zeigen", !sb.clock) { show ->
                    change { Customization.setStatusBar(sb.copy(clock = !show)) }
                }
                ToggleRow("Benachrichtigungssymbole zeigen", !sb.notificationIcons) { show ->
                    change { Customization.setStatusBar(sb.copy(notificationIcons = !show)) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Immersive-Modus pro App", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Nicht mehr möglich: Google hat die dafür nötige Einstellung (policy_control) mit Android 11 entfernt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pending?.let { p ->
        var secondsLeft by remember(p) { mutableIntStateOf(Customization.CONFIRM_SECONDS) }
        LaunchedEffect(p) {
            while (true) {
                secondsLeft = ((p.revertAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                delay(250)
            }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Behalten?") },
            text = { Text("${p.description}\nWird in $secondsLeft s automatisch zurückgesetzt.") },
            confirmButton = { TextButton(onClick = { Customization.confirm() }) { Text("Behalten") } },
            dismissButton = { TextButton(onClick = { Customization.revertNow() }) { Text("Zurücksetzen") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DensityCard(s: CustomState, enabled: Boolean, onApply: (Int) -> Unit) {
    val base = Customization.baseDensity(s)
    val min = (base * 0.7f / 10).roundToInt() * 10
    val max = (base * 1.3f / 10).roundToInt() * 10
    var value by remember(s.density) { mutableFloatStateOf(s.density.toFloat()) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anzeigegröße (DPI)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} dpi", style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "Kleiner = mehr Inhalt auf dem Bildschirm. Standard: $base dpi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / 10) - 1,
            enabled = enabled,
            onValueChangeFinished = {
                val d = (value / 10).roundToInt() * 10
                if (d != s.density) onApply(d)
            },
        )
        if (s.density != base) {
            TextButton(onClick = { onApply(base) }, enabled = enabled) { Text("Auf Standard zurücksetzen") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipCard(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { v ->
                FilterChip(
                    selected = v == selected,
                    enabled = enabled,
                    onClick = { if (v != selected) onSelect(v) },
                    label = { Text(label(v)) },
                )
            }
        }
    }
}

private fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString().replace('.', ',')
