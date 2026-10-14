package de.emil.shizubox.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.BuildConfig
import de.emil.shizubox.installer.VirusTotal
import de.emil.shizubox.settings.AppSettings
import de.emil.shizubox.settings.ColorSource
import de.emil.shizubox.settings.InstallMethod
import de.emil.shizubox.settings.Palette
import de.emil.shizubox.settings.ThemeMode
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ACCENTS = listOf(
    0xFF00897BL, 0xFF1E88E5L, 0xFF5E35B1L, 0xFFD81B60L, 0xFFE53935L,
    0xFFFB8C00L, 0xFFFDD835L, 0xFF43A047L, 0xFF6D4C41L, 0xFF546E7AL,
)

const val REPO_URL = "https://github.com/jackderganove-sudo/ShizuBox"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenUpdates: () -> Unit, onOpenSchool: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by AppSettings.state.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
    var vtKey by rememberSaveable { mutableStateOf(VirusTotal.apiKey) }
    var message by remember { mutableStateOf<String?>(null) }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            Text("Einstellungen", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp))
        }

        // ------------------------------------------------------------ appearance
        item { Header("Darstellung") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Design", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.themeMode == m,
                            onClick = { AppSettings.update { it.copy(themeMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                Row(Modifier.padding(top = 12.dp)) {
                    Text("Reines Schwarz (AMOLED)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                    Switch(checked = s.amoled, onCheckedChange = { v -> AppSettings.update { it.copy(amoled = v) } })
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Farben", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    // Wallpaper (Material You)
                    ColorDot(
                        color = MaterialTheme.colorScheme.primary,
                        selected = s.colorSource == ColorSource.Wallpaper,
                        icon = true,
                    ) { AppSettings.update { it.copy(colorSource = ColorSource.Wallpaper) } }
                    ACCENTS.forEach { a ->
                        ColorDot(Color(a.toULong().toLong()), s.colorSource == ColorSource.Accent && s.accent == a) {
                            AppSettings.update { it.copy(colorSource = ColorSource.Accent, accent = a) }
                        }
                    }
                }
                Text(
                    if (s.colorSource == ColorSource.Wallpaper) "Farben aus dem Hintergrundbild (Material You)" else "Eigene Akzentfarbe",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (s.colorSource == ColorSource.Accent) {
                    Text("Paletten-Stil", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Palette.entries.forEach { p ->
                            FilterChip(selected = s.palette == p, onClick = { AppSettings.update { it.copy(palette = p) } }, label = { Text(p.label) })
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Glas-Effekt", style = MaterialTheme.typography.titleMedium)
                SwitchRow("Unschärfe hinter Karten und Fenster", s.glass) { v -> AppSettings.update { it.copy(glass = v) } }
                if (s.glass) {
                    var strength by remember(s.blurStrength) { mutableFloatStateOf(s.blurStrength.toFloat()) }
                    Text("Stärke: ${strength.roundToInt()} %", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        valueRange = 25f..150f,
                        steps = 4,
                        onValueChangeFinished = { AppSettings.update { it.copy(blurStrength = strength.roundToInt()) } },
                    )
                }
                SwitchRow("Animierter Hintergrund", s.animatedBackground) { v -> AppSettings.update { it.copy(animatedBackground = v) } }
                Text(
                    "Glas und Animation aus spart etwas Akku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ------------------------------------------------------------ installer & security
        item { Header("Installer & Sicherheit") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Installationsart", style = MaterialTheme.typography.titleMedium)
                InstallMethod.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { AppSettings.update { it.copy(installMethod = m) } }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = s.installMethod == m, onClick = { AppSettings.update { it.copy(installMethod = m) } })
                        Column {
                            Text(m.label, style = MaterialTheme.typography.bodyLarge)
                            Text(m.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (s.installMethod != InstallMethod.Shizuku && !context.packageManager.canRequestPackageInstalls()) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("„Unbekannte Apps installieren“ für ShizuBox erlauben") }
                }
                Text("Standard-Optionen (nur mit Shizuku)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                SwitchRow("Downgrade erlauben", s.defaultDowngrade) { v -> AppSettings.update { it.copy(defaultDowngrade = v) } }
                SwitchRow("Alle Berechtigungen gewähren", s.defaultGrantAll) { v -> AppSettings.update { it.copy(defaultGrantAll = v) } }
                SwitchRow("Als Play-Store-Installation ausgeben", s.defaultPlayStoreSource) { v -> AppSettings.update { it.copy(defaultPlayStoreSource = v) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("VirusTotal", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kostenloser API-Schlüssel von virustotal.com (Profil → API Key). Bleibt nur auf diesem Handy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = vtKey, onValueChange = { vtKey = it.trim() }, singleLine = true,
                    placeholder = { Text("API-Schlüssel") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = { VirusTotal.apiKey = vtKey; message = "Schlüssel gespeichert ✓" }) { Text("Speichern") }
                    TextButton(onClick = { open("https://www.virustotal.com/gui/my-apikey") }) { Text("Schlüssel holen") }
                }
                SwitchRow("Beim Installieren automatisch prüfen", s.vtAutoScan) { v -> AppSettings.update { it.copy(vtAutoScan = v) } }
                SwitchRow("Andere Signatur blockieren", s.blockSignatureMismatch) { v -> AppSettings.update { it.copy(blockSignatureMismatch = v) } }
                Text(
                    "Eine andere Signatur als die installierte App bedeutet: nicht vom selben Entwickler (oder neu signiert).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        // ------------------------------------------------------------ other areas
        item { Header("Bereiche") }
        item { LinkCard("App-Updates", "Prüfintervall, Hintergrund-Installation, GitHub-Token", onOpenUpdates) }
        item { LinkCard("Schulmodus", "Automatisches Ende, Verhalten nach Neustart", onOpenSchool) }
        item {
            LinkCard("Benachrichtigungen", "Kanäle für Updates und Schulmodus") {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        // ------------------------------------------------------------ shizuku
        item { Header("Shizuku") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Status: ${shizuku.name}" + (ShizukuManager.serverMode?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                Text("Manager-App: ${ShizukuManager.shizukuPackage ?: "nicht gefunden"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Ohne Shizuku funktionieren: App-Updates (prüfen und per System-Dialog installieren), Universal Installer, App-Liste mit Details, APK teilen/extrahieren und VirusTotal. Schulmodus, Customization, Einfrieren, AppOps, Gaming-Modus und Logcat brauchen Shizuku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ShizukuManager.shizukuLaunchIntent()?.let { launch ->
                    TextButton(onClick = { context.startActivity(launch) }) { Text("Shizuku öffnen") }
                }
            }
        }

        // ------------------------------------------------------------ about
        item { Header("Über") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("ShizuBox ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                Text("Open Source unter GPL-3.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = { open(REPO_URL) }) { Text("GitHub") }
                    FilledTonalButton(onClick = { open("$REPO_URL/releases") }) { Text("Releases") }
                    FilledTonalButton(onClick = {
                        scope.launch {
                            message = runCatching { Updates.add(REPO_URL) }.fold(
                                { "ShizuBox wird jetzt unter App-Updates verfolgt ✓" },
                                { it.message ?: "Fehler" },
                            )
                        }
                    }) { Text("ShizuBox selbst aktualisieren") }
                }
                Text("Dank an", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                listOf(
                    "Shizuku (RikkaApps) – Apache-2.0" to "https://github.com/RikkaApps/Shizuku",
                    "Universal Installer (pass-with-high-score) – GPL-3.0" to "https://github.com/pass-with-high-score/universal-installer",
                    "Obtainium / ObtainX – GPL-3.0" to "https://github.com/bikram-agarwal/ObtainX",
                    "Ackpine (solrudev) – Apache-2.0" to "https://github.com/solrudev/Ackpine",
                    "Haze (Chris Banes) – Apache-2.0" to "https://github.com/chrisbanes/haze",
                    "MaterialKolor (jordond) – MIT" to "https://github.com/jordond/MaterialKolor",
                ).forEach { (label, url) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { open(url) }.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkCard(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, null, tint = Color.White)
            icon -> Icon(Icons.Rounded.Palette, "Hintergrundbild", tint = Color.White)
        }
    }
}
