package de.emil.shizubox.ui.school

import de.emil.shizubox.R
import de.emil.shizubox.t
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.emil.shizubox.school.SchoolState
import de.emil.shizubox.ui.glass.GlassCard

val AUTO_END_OPTIONS = listOf(0, 45, 60, 120, 240, 360, 480)

fun autoEndLabel(minutes: Int) = when {
    minutes == 0 -> t(R.string.off)
    minutes < 60 -> t(R.string.dur_min, minutes)
    else -> t(R.string.dur_hours, minutes / 60)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolScreen(
    state: SchoolState,
    shizukuReady: Boolean,
    busy: Boolean,
    onActivate: () -> Unit,
    onAutoEndChange: (Int) -> Unit,
    onWaitForShizukuChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back))
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text(t(R.string.sec_school), style = MaterialTheme.typography.displaySmall)
                Text(
                    t(R.string.school_title_sub),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.school_what), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    t(R.string.school_what1),
                    t(R.string.school_what2),
                    t(R.string.school_what3),
                    t(R.string.school_what4),
                    t(R.string.school_what5),
                ).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.school_exit), style = MaterialTheme.typography.titleMedium)
                Text(
                    t(R.string.school_exit_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.school_autoend), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AUTO_END_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = state.autoEndMinutes == minutes,
                            onClick = { onAutoEndChange(minutes) },
                            label = { Text(autoEndLabel(minutes)) },
                        )
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.school_after_reboot), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.waitForShizukuAfterReboot,
                        onClick = { onWaitForShizukuChange(false) },
                        label = { Text(t(R.string.school_reboot_black)) },
                    )
                    FilterChip(
                        selected = state.waitForShizukuAfterReboot,
                        onClick = { onWaitForShizukuChange(true) },
                        label = { Text(t(R.string.school_reboot_wait)) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.waitForShizukuAfterReboot) {
                        t(R.string.school_reboot_wait_sub)
                    } else {
                        t(R.string.school_reboot_black_sub)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            Button(
                onClick = onActivate,
                enabled = shizukuReady && !busy && !state.pendingRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(64.dp),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) t(R.string.activating) else t(R.string.school_activate), style = MaterialTheme.typography.titleMedium)
            }
            if (!shizukuReady) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t(R.string.shizuku_required), style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        }
    }
}
