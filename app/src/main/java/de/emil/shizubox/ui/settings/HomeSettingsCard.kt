package de.emil.shizubox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.settings.AppSettings
import de.emil.shizubox.settings.Settings
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.home.Section

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSettingsCard(s: Settings) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.home_start), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(
                selected = s.startSection.isEmpty(),
                onClick = { AppSettings.update { it.copy(startSection = "") } },
                label = { Text(t(R.string.home_home)) },
            )
            Section.entries.forEach { sec ->
                FilterChip(
                    selected = s.startSection == sec.name,
                    onClick = { AppSettings.update { it.copy(startSection = sec.name) } },
                    label = { Text(sec.title) },
                )
            }
        }
        Text(t(R.string.home_shown), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Section.entries.forEach { sec ->
                val shown = sec.name !in s.hiddenSections
                FilterChip(
                    selected = shown,
                    onClick = {
                        AppSettings.update { it.copy(hiddenSections = if (shown) it.hiddenSections + sec.name else it.hiddenSections - sec.name) }
                    },
                    label = { Text(sec.title) },
                )
            }
        }
        Text(t(R.string.home_shown_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(t(R.string.home_school_card), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = s.showSchoolCard, onCheckedChange = { v -> AppSettings.update { it.copy(showSchoolCard = v) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.home_quick_actions), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = s.showQuickActions, onCheckedChange = { v -> AppSettings.update { it.copy(showQuickActions = v) } })
        }
    }
}
