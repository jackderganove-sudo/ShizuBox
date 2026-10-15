package de.emil.shizubox.ui.system

import de.emil.shizubox.ui.motion.RollingText
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.system.CpuCore
import de.emil.shizubox.system.DeviceInfo
import de.emil.shizubox.system.formatDuration
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var cpu by remember { mutableStateOf<List<CpuCore>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            cpu = DeviceInfo.cpu()
            delay(2000)
            tick++
        }
    }
    // Re-read the cheap values on every tick.
    val battery = remember(tick) { DeviceInfo.battery(context) }
    val memory = remember(tick) { DeviceInfo.memory(context) }
    val storage = remember(tick / 15) { DeviceInfo.storage() }
    val uptime = remember(tick) { DeviceInfo.uptimeMs() }
    val dm = context.resources.displayMetrics
    val display = context.display
    fun size(b: Long) = Formatter.formatShortFileSize(context, b)

    val sections = buildList {
        add(
            t(R.string.di_device) to listOf(
                t(R.string.di_model) to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                t(R.string.di_codename) to Build.DEVICE,
                "Android" to DeviceInfo.androidName(),
                t(R.string.di_patch) to Build.VERSION.SECURITY_PATCH,
                t(R.string.di_build) to Build.DISPLAY,
                "Kernel" to DeviceInfo.kernel(),
                t(R.string.di_uptime) to formatDuration(uptime),
            ),
        )
        add(
            t(R.string.di_display) to listOf(
                t(R.string.cust_resolution) to "${dm.widthPixels} × ${dm.heightPixels}",
                t(R.string.di_density) to "${dm.densityDpi} dpi",
                t(R.string.cust_refresh) to "${display?.refreshRate?.toInt() ?: "?"} Hz" +
                    (display?.supportedModes?.maxOfOrNull { it.refreshRate }?.let { " (max. ${it.toInt()} Hz)" } ?: ""),
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.di_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val text = buildString {
                        sections.forEach { (title, rows) ->
                            appendLine("== $title")
                            rows.forEach { (k, v) -> appendLine("$k: $v") }
                        }
                        battery?.let { appendLine("== ${t(R.string.battery)}\n${it.level} % · ${it.temperatureC} °C" + (it.cycleCount?.let { c -> " · $c" } ?: "")) }
                        appendLine("== RAM\n${size(memory.availBytes)} / ${size(memory.totalBytes)}")
                        appendLine("== ${t(R.string.di_storage)}\n${size(storage.freeBytes)} / ${size(storage.totalBytes)}")
                        appendLine("== CPU\n${DeviceInfo.soc()} · ${cpu.size}")
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("device", text))
                    Toast.makeText(context, t(R.string.copied), Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.ContentCopy, t(R.string.copy)) }
            }
        }
        battery?.let { b ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingText("${b.level} %", MaterialTheme.typography.displaySmall)
                        Text(
                            listOfNotNull(b.status, b.plugged).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                        )
                    }
                    LinearProgressIndicator(progress = { b.level / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    InfoRows(
                        listOfNotNull(
                            b.health?.let { t(R.string.di_health) to it },
                            t(R.string.di_temperature) to "%.1f °C".format(b.temperatureC),
                            t(R.string.di_voltage) to "${b.voltageMv} mV",
                            b.currentMa?.let { t(R.string.di_current) to "$it mA" },
                            b.cycleCount?.let { t(R.string.di_cycles) to it.toString() },
                            b.technology?.let { t(R.string.di_technology) to it },
                        ),
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.di_memory), style = MaterialTheme.typography.titleMedium)
                UsageBar(t(R.string.di_ram), memory.totalBytes - memory.availBytes, memory.totalBytes, ::size)
                UsageBar(t(R.string.di_storage), storage.totalBytes - storage.freeBytes, storage.totalBytes, ::size)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("CPU", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${DeviceInfo.soc()} · ${tp(R.plurals.di_cores, cpu.size.coerceAtLeast(1))} · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    cpu.forEach { c ->
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            RollingText(c.curKhz?.let { "${it / 1000}" } ?: "–", MaterialTheme.typography.titleMedium)
                            Text(
                                "MHz · #${c.index}" + (c.maxKhz?.let { "\n≤ %.2f GHz".format(it / 1_000_000f) } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        sections.forEach { (title, rows) ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    InfoRows(rows)
                }
            }
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    rows.forEach { (k, v) ->
        Row(Modifier.padding(top = 6.dp)) {
            Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
            Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
        }
    }
}

@Composable
private fun UsageBar(label: String, used: Long, total: Long, size: (Long) -> String) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${size(used)} / ${size(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (total > 0) used.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

private fun tp(id: Int, n: Int) = de.emil.shizubox.tp(id, n, n)
