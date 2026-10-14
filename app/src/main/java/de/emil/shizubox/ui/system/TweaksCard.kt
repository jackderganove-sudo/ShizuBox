package de.emil.shizubox.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.system.NightMode
import de.emil.shizubox.system.TweakState
import de.emil.shizubox.system.Tweaks
import de.emil.shizubox.system.timeoutLabel
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TweaksCard(shizukuReady: Boolean) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TweakState?>(null) }
    LaunchedEffect(shizukuReady) { if (shizukuReady) state = runCatching { Tweaks.read() }.getOrNull() }

    fun change(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
            state = runCatching { Tweaks.read() }.getOrNull()
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.tw_title), style = MaterialTheme.typography.titleMedium)
        Text(t(R.string.tw_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val s = state
        if (!shizukuReady || s == null) {
            Text(
                if (shizukuReady) t(R.string.loading) else t(R.string.shizuku_must),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@GlassCard
        }
        Spacer(Modifier.height(4.dp))
        TweakSwitch(t(R.string.tw_stay_awake), t(R.string.tw_stay_awake_sub), s.stayAwake) { v -> change { Tweaks.setStayAwake(v) } }
        TweakSwitch(t(R.string.tw_battery_saver), null, s.batterySaver) { v -> change { Tweaks.setBatterySaver(v) } }
        TweakSwitch(t(R.string.tw_show_touches), null, s.showTouches) { v -> change { Tweaks.setShowTouches(v) } }
        TweakSwitch(t(R.string.tw_pointer), t(R.string.tw_pointer_sub), s.pointerLocation) { v -> change { Tweaks.setPointerLocation(v) } }

        Text(t(R.string.tw_dark), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NightMode.entries.forEach { m ->
                FilterChip(selected = s.night == m, onClick = { change { Tweaks.setNight(m) } }, label = { Text(m.label) })
            }
        }
        Text(t(R.string.tw_timeout), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tweaks.SCREEN_TIMEOUTS.forEach { ms ->
                FilterChip(selected = s.screenTimeoutMs == ms, onClick = { change { Tweaks.setScreenTimeout(ms) } }, label = { Text(timeoutLabel(ms)) })
            }
        }
        Text(
            t(R.string.tw_tiles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TweakSwitch(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
