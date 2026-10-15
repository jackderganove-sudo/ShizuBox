package de.emil.shizubox.ui.school

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.school.SchoolSchedule
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleCard(autoEndMinutes: Int) {
    val context = LocalContext.current
    val s by SchoolSchedule.state.collectAsStateWithLifecycle()
    val time = "%02d:%02d".format(s.minuteOfDay / 60, s.minuteOfDay % 60)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.sch_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = s.enabled, onCheckedChange = { v -> SchoolSchedule.update { it.copy(enabled = v, skipUntil = 0) } })
        }
        Text(t(R.string.sch_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(t(R.string.sch_start), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> SchoolSchedule.update { it.copy(minuteOfDay = h * 60 + m, skipUntil = 0) } },
                    s.minuteOfDay / 60, s.minuteOfDay % 60, DateFormat.is24HourFormat(context)).show()
            }) { Text(time, style = MaterialTheme.typography.titleMedium) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            DayOfWeek.entries.forEach { d ->
                val bit = 1 shl (d.value - 1)
                FilterChip(
                    selected = s.days and bit != 0,
                    onClick = { SchoolSchedule.update { it.copy(days = it.days xor bit) } },
                    label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }
        val next = SchoolSchedule.nextStart(s)
        if (s.enabled) {
            Text(
                if (next != null) {
                    t(R.string.sch_next, DateFormat.getMediumDateFormat(context).format(Date(next)) + " " + DateFormat.getTimeFormat(context).format(Date(next)))
                } else t(R.string.sch_no_days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (autoEndMinutes > 0) t(R.string.sch_end_auto, autoEndLabel(autoEndMinutes)) else t(R.string.sch_end_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (next != null) {
                TextButton(onClick = { SchoolSchedule.skipNext() }) { Text(t(R.string.sch_skip_next)) }
            }
        }
    }
}
