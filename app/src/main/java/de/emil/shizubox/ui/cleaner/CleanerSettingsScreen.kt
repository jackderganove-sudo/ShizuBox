@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.rememberAppLabel
import de.emil.shizubox.cleaner.CleanerSettings
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CleanerSettingsScreen(onBack: () -> Unit) {
    val cfg by CleanerSettings.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var newPath by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(t(R.string.cl_settings), onBack) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.cl_trash), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.cl_keep_days), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 7, 14, 30).forEach { d ->
                        FilterChip(selected = cfg.trashDays == d, onClick = { CleanerSettings.update { it.copy(trashDays = d) } }, label = { Text(tp(R.plurals.nh_days, d, d)) })
                    }
                }
            }
        }
        item { ScheduleCard() }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.cl_exclusions), style = MaterialTheme.typography.titleMedium)
                Text(t(R.string.cl_exclusions_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t(R.string.cl_excl_paths), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cfg.excludedPaths.sorted().forEach { p ->
                        // Tapping the chip removes the exclusion.
                        InputChip(
                            selected = false,
                            onClick = { CleanerSettings.update { it.copy(excludedPaths = it.excludedPaths - p) } },
                            label = { Text(p) },
                            trailingIcon = { Icon(Icons.Rounded.Close, t(R.string.cl_remove)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newPath, { newPath = it }, label = { Text(t(R.string.cl_excl_path_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = newPath.isNotBlank(), onClick = {
                        val p = newPath.trim().trim('/').removePrefix("sdcard/").removePrefix("storage/emulated/0/")
                        CleanerSettings.update { it.copy(excludedPaths = it.excludedPaths + p) }
                        newPath = ""
                    }) { Icon(Icons.Rounded.Add, t(R.string.cl_add)) }
                }
                Text(t(R.string.cl_excl_apps), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                cfg.excludedApps.sorted().forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rememberAppLabel(pkg), style = MaterialTheme.typography.bodyMedium)
                            Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { CleanerSettings.update { it.copy(excludedApps = it.excludedApps - pkg) } }) { Icon(Icons.Rounded.Close, t(R.string.cl_remove)) }
                    }
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { picker = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
                    Text(t(R.string.cl_excl_add_app))
                }
                Text(t(R.string.cl_locked_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t(R.string.cl_aicore), style = MaterialTheme.typography.bodyLarge)
                        Text(t(R.string.cl_aicore_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = cfg.includeAiCore, onCheckedChange = { v -> CleanerSettings.update { it.copy(includeAiCore = v) } })
                }
            }
        }
    }
    if (picker) {
        de.emil.shizubox.ui.automation.AppPicker(onDismiss = { picker = false }) { pkg ->
            picker = false
            CleanerSettings.update { it.copy(excludedApps = it.excludedApps + pkg) }
        }
    }
}

/** Scheduled cleaning: day(s), time, only while charging, and what may be cleaned without asking. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cfg by CleanerSettings.state.collectAsStateWithLifecycle()
    fun set(f: (de.emil.shizubox.cleaner.CleanerConfig) -> de.emil.shizubox.cleaner.CleanerConfig) {
        CleanerSettings.update(f)
        de.emil.shizubox.cleaner.CleanerSchedule.schedule(context)
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.cs_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = cfg.schedule, onCheckedChange = { v -> set { it.copy(schedule = v) } })
        }
        Text(t(R.string.cs_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(t(R.string.cs_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
                android.app.TimePickerDialog(context, { _, h, m -> set { it.copy(scheduleMinute = h * 60 + m) } },
                    cfg.scheduleMinute / 60, cfg.scheduleMinute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
            }) { Text("%02d:%02d".format(cfg.scheduleMinute / 60, cfg.scheduleMinute % 60), style = MaterialTheme.typography.titleMedium) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            java.time.DayOfWeek.entries.forEach { d ->
                val bit = 1 shl (d.value - 1)
                FilterChip(selected = cfg.scheduleDays and bit != 0, onClick = { set { it.copy(scheduleDays = it.scheduleDays xor bit) } },
                    label = { Text(d.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(t(R.string.cs_charging), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = cfg.scheduleCharging, onCheckedChange = { v -> set { it.copy(scheduleCharging = v) } })
        }
        if (cfg.schedule) {
            val next = de.emil.shizubox.cleaner.CleanerSchedule.next(cfg)
            Text(
                next?.let { t(R.string.sch_next, android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(it)) + " " + android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it))) }
                    ?: t(R.string.sch_no_days),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(t(R.string.cs_what), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        val keys = de.emil.shizubox.cleaner.SysCat.entries.map { it.name to it.title() } + listOf(
            de.emil.shizubox.cleaner.AutoKeys.APP_CACHES to t(R.string.cs_app_caches),
            de.emil.shizubox.cleaner.AutoKeys.CORPSES to t(R.string.cl_corpses),
            de.emil.shizubox.cleaner.AutoKeys.DUPLICATES to t(R.string.cs_dupes),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            keys.forEach { (k, label) ->
                FilterChip(selected = k in cfg.autoKeys, onClick = { set { it.copy(autoKeys = if (k in it.autoKeys) it.autoKeys - k else it.autoKeys + k) } }, label = { Text(label) })
            }
        }
        Text(t(R.string.cs_what_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
