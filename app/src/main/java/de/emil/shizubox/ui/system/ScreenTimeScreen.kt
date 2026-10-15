package de.emil.shizubox.ui.system

import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.rememberListStart
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.Motion
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.system.AppUsage
import de.emil.shizubox.system.ScreenTime
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard

fun formatUsage(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> t(R.string.st_under_min)
        min < 60 -> t(R.string.dur_min, min.toInt())
        else -> t(R.string.dur_h_min, (min / 60).toInt(), (min % 60).toInt())
    }
}

@Composable
fun ScreenTimeScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var days by rememberSaveable { mutableStateOf(1) }
    var access by remember { mutableStateOf(ScreenTime.hasAccess(context)) }
    var usage by remember { mutableStateOf<List<AppUsage>?>(null) }
    LaunchedEffect(days, shizukuReady) {
        access = ScreenTime.ensureAccess(context)
        if (access) usage = runCatching { ScreenTime.load(context, days) }.getOrDefault(emptyList())
    }
    val labels = remember { mutableMapOf<String, String>() }
    fun label(pkg: String) = labels.getOrPut(pkg) { runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg) }
    val list = usage.orEmpty()
    val stListStart = rememberListStart(usage != null)
    val total = list.sumOf { it.foregroundMs }
    val max = list.maxOfOrNull { it.foregroundMs } ?: 1L

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.st_title), style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (!access) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.st_no_access)) } }
            return@LazyColumn
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                RollingText(formatUsage(total), MaterialTheme.typography.displaySmall)
                Text(
                    if (days == 1) t(R.string.st_today_total) else t(R.string.st_avg, formatUsage(total / days)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = days == 1, onClick = { days = 1 }, label = { Text(t(R.string.st_today)) })
                    FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text(t(R.string.st_7days)) })
                }
            }
        }
        if (usage == null) item { Text(t(R.string.loading), modifier = Modifier.padding(8.dp)) }
        itemsIndexed(list, key = { _, u -> u.pkg }) { index, u ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(index, stListStart)) {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = remember(u.pkg) { Apps.icon(u.pkg)?.asImageBitmap() }
                    Box(Modifier.size(36.dp)) { if (icon != null) Image(icon, null, Modifier.size(36.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(label(u.pkg), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatUsage(u.foregroundMs), style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            de.emil.shizubox.tp(R.plurals.st_opened, u.launches, u.launches),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(progress = { u.foregroundMs.toFloat() / max }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
            }
        }
    }
}
