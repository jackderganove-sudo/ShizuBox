package de.emil.shizubox.ui.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.system.BoostService
import de.emil.shizubox.system.BoostSettings
import de.emil.shizubox.system.VolumeBoost
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import kotlinx.coroutines.launch

/** Volume above the maximum, with separate levels for speaker and headphones. */
@Composable
fun BoostCard(shizukuReady: Boolean) {
    val context = LocalContext.current
    val s by VolumeBoost.settings.collectAsStateWithLifecycle()
    val headphones by VolumeBoost.headphonesConnected.collectAsStateWithLifecycle()
    fun set(block: (BoostSettings) -> BoostSettings) {
        VolumeBoost.update(block)
        BoostService.sync(context)
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t(R.string.vb_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (s.enabled) t(R.string.vb_active, VolumeBoost.currentPercent(s), if (headphones) t(R.string.vb_out_headphones) else t(R.string.vb_out_speaker))
                    else t(R.string.vb_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.enabled, onCheckedChange = { on -> set { it.copy(enabled = on) } })
        }
        Spacer(Modifier.height(8.dp))
        if (s.split) {
            LevelSlider(t(R.string.vb_speaker), s.speaker, s.limit, current = !headphones) { v -> set { it.copy(speaker = v) } }
            LevelSlider(t(R.string.vb_headphones), s.headphones, s.limit, current = headphones) { v -> set { it.copy(headphones = v) } }
        } else {
            LevelSlider(t(R.string.vb_level), s.speaker, s.limit, current = true) { v -> set { it.copy(speaker = v) } }
        }
        val loudHeadphones = headphones && VolumeBoost.currentPercent(s) > VolumeBoost.HEADPHONE_WARN
        AnimatedVisibility(loudHeadphones, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
            Text(
                t(R.string.vb_warn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t(R.string.vb_split), style = MaterialTheme.typography.bodyMedium)
                Text(t(R.string.vb_split_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.split, onCheckedChange = { v -> set { it.copy(split = v) } })
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.vb_limit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("+${s.limit} %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = s.limit.toFloat(),
            onValueChange = { v -> set { it.copy(limit = v.toInt()) } },
            valueRange = 20f..100f,
            steps = 7,
        )
        Text(t(R.string.vb_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HearingProtection(shizukuReady)
    }
}

@Composable
private fun LevelSlider(label: String, value: Int, limit: Int, current: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label + if (current) " ●" else "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        RollingText("+$value %", MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = value.toFloat().coerceAtMost(limit.toFloat()),
        onValueChange = { onChange((it / 5).toInt() * 5) },
        valueRange = 0f..limit.toFloat(),
    )
}

/**
 * Android 17's sound dose ("listening level too high"). It can't be switched off by shell,
 * only reset to 0 – the old value is kept for "Restore".
 */
@Composable
private fun HearingProtection(shizukuReady: Boolean) {
    if (!shizukuReady) return
    val scope = rememberCoroutineScope()
    var dose by remember { mutableStateOf<Float?>(null) }
    var canRestore by remember { mutableStateOf(VolumeBoost.canRestoreDose()) }
    LaunchedEffect(Unit) { dose = VolumeBoost.soundDose() }
    val d = dose ?: return
    Spacer(Modifier.height(12.dp))
    Text(t(R.string.vb_dose_title), style = MaterialTheme.typography.titleSmall)
    Text(
        t(R.string.vb_dose_sub, (d * 100).toInt()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            shapes = ButtonDefaults.shapes(),
            onClick = {
                scope.launch {
                    VolumeBoost.resetSoundDose()
                    dose = VolumeBoost.soundDose()
                    canRestore = VolumeBoost.canRestoreDose()
                }
            },
        ) { Text(t(R.string.vb_dose_reset)) }
        if (canRestore) {
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    scope.launch {
                        VolumeBoost.restoreSoundDose()
                        dose = VolumeBoost.soundDose()
                        canRestore = false
                    }
                },
            ) { Text(t(R.string.vb_dose_restore)) }
        }
    }
}
