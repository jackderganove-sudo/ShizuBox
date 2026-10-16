@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.system

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.rememberAppLabel
import de.emil.shizubox.audio.AppVolume
import de.emil.shizubox.t
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Volume per app: apps playing right now on top, then those with their own level. */
@Composable
fun AppVolumeScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val levels by AppVolume.levels.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(emptyList<String>()) }
    var picker by remember { mutableStateOf(false) }
    // Who plays right now – refreshed every few seconds while the screen is open.
    LaunchedEffect(shizukuReady) {
        while (shizukuReady) {
            playing = runCatching { AppVolume.players() }.getOrDefault(emptyList())
                .filter { it.playing && it.uid >= 10_000 }.mapNotNull { AppVolume.packageOf(it.uid) }.distinct()
            delay(3000)
        }
    }
    val apps = (playing + levels.keys.sorted()).distinct()
    val listStart = rememberListStart(Unit)
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.av_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Text(t(R.string.av_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (!shizukuReady) item { GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) } }
        if (apps.isEmpty()) item { EmptyState(Icons.Rounded.GraphicEq, t(R.string.av_empty), t(R.string.av_empty_hint)) }
        itemsIndexed(apps, key = { _, p -> p }) { i, pkg ->
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = Motion.quick()).enterStagger(i, listStart)) {
                VolumeRow(pkg, pkg in playing, levels[pkg] ?: 100, shizukuReady)
            }
        }
        item {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = shizukuReady, onClick = { picker = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.padding(end = 8.dp))
                Text(t(R.string.av_add))
            }
        }
    }
    if (picker) {
        val scope = rememberCoroutineScope()
        de.emil.shizubox.ui.automation.AppPicker(onDismiss = { picker = false }) { pkg ->
            picker = false
            // Added at 80 %: a visible change, and the slider is right there to adjust.
            scope.launch { AppVolume.set(pkg, 80) }
        }
    }
}

@Composable
private fun VolumeRow(pkg: String, playing: Boolean, level: Int, shizukuReady: Boolean) {
    val scope = rememberCoroutineScope()
    var value by remember(pkg, level) { mutableFloatStateOf(level.toFloat()) }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rememberAppLabel(pkg), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (playing) Text(t(R.string.av_playing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            RollingText("${value.toInt()} %", MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = value,
            enabled = shizukuReady,
            onValueChange = { value = it },
            // Applied when the finger lets go: every value is a binder call to the shell service.
            onValueChangeFinished = { scope.launch { AppVolume.set(pkg, value.toInt()) } },
            valueRange = 0f..100f,
            steps = 19,
        )
        if (level < 100) {
            TextButton(onClick = { value = 100f; scope.launch { AppVolume.set(pkg, 100) } }) { Text(t(R.string.av_reset)) }
        }
    }
}
