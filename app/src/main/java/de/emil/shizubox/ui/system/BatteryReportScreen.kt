@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.NetBlock
import de.emil.shizubox.battery.AppDrain
import de.emil.shizubox.battery.BatteryReport
import de.emil.shizubox.battery.DrainWindow
import de.emil.shizubox.battery.LevelSample
import de.emil.shizubox.t
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryReportScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val version by BatteryReport.version.collectAsStateWithLifecycle()
    val s by BatteryReport.settings.collectAsStateWithLifecycle()
    var win by rememberSaveable { mutableStateOf(DrainWindow.SinceCharge) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // Opening the report takes a fresh snapshot (the worker only runs hourly).
    LaunchedEffect(shizukuReady) {
        loading = true
        withContext(Dispatchers.IO) { runCatching { BatteryReport.sampleLevel() } }
        if (shizukuReady && System.currentTimeMillis() - BatteryReport.lastSnapshot() > 5 * 60_000L) runCatching { BatteryReport.snapshot() }
        loading = false
    }
    val range = remember(win, version, s) { BatteryReport.window(win) }
    val drains by produceState<List<AppDrain>?>(null, win, version, s) { value = BatteryReport.drain(win) }
    val levels by produceState(emptyList<LevelSample>(), win, version, s) {
        // The chart shows at least the last 24 hours so the context is visible.
        value = BatteryReport.levels(minOf(range.first, System.currentTimeMillis() - 86_400_000L))
    }
    val drop by produceState(0, win, version, s) {
        value = if (win == DrainWindow.SinceCharge) BatteryReport.sinceChargePercent() ?: BatteryReport.drop(range) else BatteryReport.drop(range)
    }
    val labels = remember { mutableMapOf<Int, String>() }
    val list = drains.orEmpty()
    val total = list.sumOf { it.mAh }
    val listStart = rememberListStart(win)

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.br_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (loading) LoadingIndicator(Modifier.padding(end = 8.dp))
                IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Rounded.Settings, t(R.string.settings)) }
            }
        }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        item {
            AnimatedVisibility(showSettings, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(t(R.string.br_night_hours, s.nightStart, s.nightEnd), style = MaterialTheme.typography.bodyLarge)
                    Text(t(R.string.br_night_from), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = s.nightStart.toFloat(), onValueChange = { v -> BatteryReport.update { it.copy(nightStart = v.roundToInt()) } }, valueRange = 18f..23f, steps = 4)
                    Text(t(R.string.br_night_to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = s.nightEnd.toFloat(), onValueChange = { v -> BatteryReport.update { it.copy(nightEnd = v.roundToInt()) } }, valueRange = 4f..11f, steps = 6)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.br_morning), style = MaterialTheme.typography.bodyMedium)
                            Text(t(R.string.br_morning_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = s.morningNotify, onCheckedChange = { v -> BatteryReport.update { it.copy(morningNotify = v) } })
                    }
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val names = listOf(t(R.string.br_since_charge), t(R.string.br_24h), t(R.string.br_night))
                DrainWindow.entries.forEachIndexed { i, w ->
                    SegmentedButton(selected = win == w, onClick = { win = w }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(names[i], maxLines = 1) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText("${levels.lastOrNull()?.level ?: "–"} %", MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        t(R.string.br_used, drop) + " · " + t(R.string.br_apps_mah, "%.0f".format(total)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    DateUtils.formatDateRange(context, if (win == DrainWindow.SinceCharge) range.first else range.first.coerceAtLeast(levels.firstOrNull()?.time ?: range.first), range.last, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LevelChart(levels, range)
            }
        }
        if (drains != null && list.isEmpty()) {
            item { EmptyState(Icons.Rounded.BatteryChargingFull, t(R.string.br_empty), t(R.string.br_empty_hint)) }
        }
        itemsIndexed(list.take(40), key = { _, d -> d.uid }) { i, d ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                DrainRow(d, total, labels, shizukuReady)
            }
        }
    }
}

/** Battery level over time: one series (no legend), one axis, thin line, recessive grid, tap/drag shows the value. */
@Composable
private fun LevelChart(samples: List<LevelSample>, range: LongRange) {
    val context = LocalContext.current
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val charge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val window = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    var touch by remember { mutableStateOf<Float?>(null) }
    if (samples.size < 2) {
        Text(t(R.string.br_chart_wait), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val t0 = samples.first().time
    val t1 = maxOf(samples.last().time, t0 + 1)
    fun xOf(t: Long, w: Float) = (t - t0).toFloat() / (t1 - t0) * w
    val picked = touch?.let { fx -> samples.minByOrNull { kotlin.math.abs(it.time - (t0 + (fx * (t1 - t0)).toLong())) } }
    Text(
        picked?.let { DateUtils.formatDateTime(context, it.time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY) + " · ${it.level} %" + if (it.charging) " · " + t(R.string.br_charging) else "" }
            ?: t(R.string.br_chart_hint),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(top = 6.dp)
            .pointerInput(samples) {
                detectTapGestures { touch = (it.x / size.width).coerceIn(0f, 1f) }
            }
            .pointerInput(samples) {
                detectDragGestures(onDragEnd = { }) { change, _ -> touch = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val w = size.width
        val h = size.height
        fun y(level: Int) = h - level / 100f * h
        // Selected window as a faint band; charging periods in a second faint tone.
        drawRect(window, topLeft = Offset(xOf(range.first.coerceAtLeast(t0), w), 0f), size = androidx.compose.ui.geometry.Size((xOf(range.last.coerceAtMost(t1), w) - xOf(range.first.coerceAtLeast(t0), w)).coerceAtLeast(0f), h))
        for (i in 1 until samples.size) if (samples[i].charging) {
            drawRect(charge, topLeft = Offset(xOf(samples[i - 1].time, w), 0f), size = androidx.compose.ui.geometry.Size(xOf(samples[i].time, w) - xOf(samples[i - 1].time, w), h))
        }
        for (lv in listOf(0, 50, 100)) drawLine(grid, Offset(0f, y(lv)), Offset(w, y(lv)), strokeWidth = 1.dp.toPx())
        val path = Path()
        samples.forEachIndexed { i, sm -> if (i == 0) path.moveTo(xOf(sm.time, w), y(sm.level)) else path.lineTo(xOf(sm.time, w), y(sm.level)) }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        picked?.let {
            val x = xOf(it.time, w)
            drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            drawCircle(line, radius = 4.dp.toPx(), center = Offset(x, y(it.level)))
        }
    }
    Row {
        Text(DateUtils.formatDateTime(context, t0, DateUtils.FORMAT_SHOW_TIME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(t(R.string.br_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrainRow(d: AppDrain, total: Double, labels: MutableMap<Int, String>, shizukuReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val pkg = remember(d.uid) { BatteryReport.packageOf(d.uid) }
    val label by produceState(labels[d.uid] ?: pkg ?: "UID ${d.uid}", d.uid) {
        value = labels[d.uid] ?: withContext(Dispatchers.IO) { BatteryReport.label(d.uid) }.also { labels[d.uid] = it }
    }
    val self = pkg == context.packageName
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pkg != null && d.uid >= 10_000) AppIcon(pkg, 36) else Box(Modifier.width(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (self) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t(R.string.br_self), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                LinearProgressIndicator(progress = { if (total > 0) (d.mAh / total).toFloat() else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("%.0f %%".format(if (total > 0) d.mAh / total * 100 else 0.0), style = MaterialTheme.typography.titleSmall)
                Text("%.1f mAh".format(d.mAh), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            t(R.string.br_split, "%.1f".format(d.fg), "%.1f".format(d.bg + d.cached), "%.1f".format(d.fgs)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
        if (d.wakelockMs > 0 || d.mobileBytes > 0 || d.wakeups > 0) {
            Text(
                listOfNotNull(
                    if (d.wakelockMs >= 1000) t(R.string.br_wakelock, DateUtils.formatElapsedTime(d.wakelockMs / 1000)) else null,
                    if (d.mobileBytes > 0) t(R.string.br_mobile, Formatter.formatShortFileSize(context, d.mobileBytes)) else null,
                    if (d.wakeups > 0) t(R.string.br_wakeups, d.wakeups) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        done?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        AnimatedVisibility(open && pkg != null && d.uid >= 10_000 && !self, enter = fadeIn(Motion.quick()) + expandVertically(Motion.smooth()), exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.smooth())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                fun act(block: suspend () -> Unit) { scope.launch { done = if (runCatching { block() }.isSuccess) t(R.string.sel_done) else t(R.string.error) } }
                AssistChip(enabled = shizukuReady, onClick = { act { Apps.setBattery(pkg!!, de.emil.shizubox.apps.BatteryMode.Restricted) } }, label = { Text(t(R.string.br_restrict)) })
                AssistChip(enabled = shizukuReady, onClick = { act { Apps.forceStop(pkg!!) } }, label = { Text(t(R.string.force_stop)) })
                AssistChip(enabled = shizukuReady, onClick = { act { Apps.freeze(listOf(pkg!!)) } }, label = { Text(t(R.string.freeze)) })
                if (NetBlock.canBlock(pkg!!)) AssistChip(enabled = shizukuReady, onClick = { act { NetBlock.set(listOf(pkg), true) } }, label = { Text(t(R.string.nb_block)) })
            }
        }
    }
}
