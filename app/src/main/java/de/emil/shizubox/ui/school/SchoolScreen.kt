package de.emil.shizubox.ui.school

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
    minutes == 0 -> "Aus"
    minutes < 60 -> "$minutes Min"
    else -> "${minutes / 60} Std"
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
            }
            Column(Modifier.padding(start = 8.dp, top = 32.dp, bottom = 12.dp)) {
                Text("Schulmodus", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Das Handy bleibt an, aber es passiert absolut nichts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Was passiert", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Flugmodus an, WLAN, Bluetooth und mobile Daten aus",
                    "Bitte nicht stören: keine Unterbrechungen, kein Ton, keine Vibration",
                    "Always-on-Display, Sperrbildschirm-Benachrichtigungen und Kamera-Doppeldruck aus",
                    "Alle Apps pausiert (außer Telefon/Notruf, Einstellungen, Shizuku)",
                    "Bildschirm komplett schwarz, auch über dem Sperrbildschirm",
                ).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Beenden", style = MaterialTheme.typography.titleMedium)
                Text(
                    "3× schnell hintereinander die Power-Taste drücken. Danach wird alles exakt so wiederhergestellt, wie es vorher war.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Automatisch beenden nach", style = MaterialTheme.typography.titleMedium)
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
                Text("Nach einem Neustart", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.waitForShizukuAfterReboot,
                        onClick = { onWaitForShizukuChange(false) },
                        label = { Text("Sofort schwarz") },
                    )
                    FilterChip(
                        selected = state.waitForShizukuAfterReboot,
                        onClick = { onWaitForShizukuChange(true) },
                        label = { Text("Erst mit Shizuku") },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.waitForShizukuAfterReboot) {
                        "Das Handy bleibt nach dem Neustart bedienbar. Sobald Shizuku wieder läuft, wird der Bildschirm wieder schwarz – 3× Power stellt dann direkt alles wieder her."
                    } else {
                        "Der Bildschirm ist direkt nach dem Neustart schwarz. Nach 3× Power führt eine kurze Anleitung durch den Neustart von Shizuku, danach wird alles automatisch wiederhergestellt."
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
                Text(if (busy) "Wird aktiviert …" else "Schulmodus aktivieren", style = MaterialTheme.typography.titleMedium)
            }
            if (!shizukuReady) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Shizuku muss verbunden sein.", style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        }
    }
}
