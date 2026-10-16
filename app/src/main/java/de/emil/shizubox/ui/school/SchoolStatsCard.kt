package de.emil.shizubox.ui.school

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.schoolstats.EndReason
import de.emil.shizubox.schoolstats.SchoolSession
import de.emil.shizubox.schoolstats.SchoolStats
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.RollingText

/** School mode statistics (read from school mode's own log – school mode itself is unchanged). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolStatsCard() {
    val version by SchoolStats.version.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { SchoolStats.collect() } }
    val sessions by produceState(emptyList<SchoolSession>(), version) { value = SchoolStats.sessions() }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.ss_title), style = MaterialTheme.typography.titleMedium)
        if (sessions.isEmpty()) {
            Text(t(R.string.ss_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@GlassCard
        }
        val periods = listOf(
            t(R.string.ss_today) to SchoolStats.startOfDay(),
            t(R.string.ss_week) to SchoolStats.startOfWeek(),
            t(R.string.ss_month) to SchoolStats.startOfMonth(),
            t(R.string.ss_total) to 0L,
        )
        // Two per row: "10 h 28 min" doesn't fit four times next to each other.
        periods.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                pair.forEach { (label, from) ->
                    Column(Modifier.weight(1f)) {
                        RollingText(SchoolStats.format(SchoolStats.total(sessions, from)), MaterialTheme.typography.titleLarge)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        DayBars(sessions)
        val done = sessions.filter { it.end != null }
        if (done.isNotEmpty()) {
            val avg = done.sumOf { it.durationMs() } / done.size
            val longest = done.maxBy { it.durationMs() }
            Text(
                tp(R.plurals.ss_sessions, sessions.size, sessions.size) + " · " + t(R.string.ss_avg, SchoolStats.format(avg)) + " · " +
                    t(R.string.ss_longest, SchoolStats.format(longest.durationMs())),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
            val reasons = done.groupingBy { it.reason }.eachCount()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(EndReason.Power to R.string.ss_end_power, EndReason.Timeout to R.string.ss_end_timeout, EndReason.Emergency to R.string.ss_end_emergency).forEach { (r, res) ->
                    reasons[r]?.let { n -> Text(t(res, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val scheduled = sessions.count { it.scheduled }
                if (scheduled > 0) Text(t(R.string.ss_scheduled, scheduled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val screenOns = sessions.sumOf { it.screenOns }
                Text(t(R.string.ss_screen_ons, screenOns), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Minutes per day for the last 14 days: one series, bars from a shared baseline, tap shows the value. */
@Composable
private fun DayBars(sessions: List<SchoolSession>) {
    val context = LocalContext.current
    val days = 14
    val values = remember(sessions) {
        (days - 1 downTo 0).map { d ->
            val from = SchoolStats.startOfDay(d)
            from to SchoolStats.total(sessions, from, from + 86_400_000L)
        }
    }
    val max = values.maxOf { it.second }.coerceAtLeast(60_000L)
    var picked by remember { mutableStateOf<Int?>(null) }
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Text(
        picked?.let { i -> DateUtils.formatDateTime(context, values[i].first, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL) + " · " + SchoolStats.format(values[i].second) }
            ?: t(R.string.ss_chart_hint),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
    )
    Canvas(
        Modifier.fillMaxWidth().height(90.dp).padding(top = 6.dp).pointerInput(values) {
            detectTapGestures { picked = (it.x / size.width * days).toInt().coerceIn(0, days - 1) }
        },
    ) {
        val slot = size.width / days
        val w = slot - 2.dp.toPx() * 2
        values.forEachIndexed { i, (_, ms) ->
            val h = if (ms <= 0) 2.dp.toPx() else (ms.toFloat() / max * size.height).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                if (ms <= 0) empty else bar,
                topLeft = Offset(i * slot + 2.dp.toPx(), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(4.dp.toPx()),
                alpha = if (picked == null || picked == i) 1f else 0.5f,
            )
        }
    }
}
