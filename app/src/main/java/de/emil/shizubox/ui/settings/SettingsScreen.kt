@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.settings

import de.emil.shizubox.R
import de.emil.shizubox.t
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
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
import de.emil.shizubox.settings.Language
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

    var errorLog by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(errorLog) { errorLog = false }
    if (errorLog) {
        ErrorLogScreen(onBack = { errorLog = false })
        return
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
            Text(t(R.string.settings), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp))
        }

        // ------------------------------------------------------------ appearance
        item { Header(t(R.string.set_appearance)) }
        if (Language.supported) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.set_language), style = MaterialTheme.typography.titleMedium)
                val current = remember { Language.current(context) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Language.OPTIONS.forEach { (tag, name) ->
                        FilterChip(
                            selected = current == tag,
                            onClick = { if (current != tag) Language.set(context, tag) },
                            label = { Text(name ?: t(R.string.lang_system)) },
                        )
                    }
                }
                Text(t(R.string.lang_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.set_design), style = MaterialTheme.typography.titleMedium)
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
                    Text(t(R.string.set_amoled), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                    Switch(checked = s.amoled, onCheckedChange = { v -> AppSettings.update { it.copy(amoled = v) } })
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.set_colors), style = MaterialTheme.typography.titleMedium)
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
                    if (s.colorSource == ColorSource.Wallpaper) t(R.string.set_colors_wallpaper) else t(R.string.set_colors_accent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (s.colorSource == ColorSource.Accent) {
                    Text(t(R.string.set_palette), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
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
                Text(t(R.string.set_glass), style = MaterialTheme.typography.titleMedium)
                SwitchRow(t(R.string.set_glass_blur), s.glass) { v -> AppSettings.update { it.copy(glass = v) } }
                if (s.glass) {
                    var strength by remember(s.blurStrength) { mutableFloatStateOf(s.blurStrength.toFloat()) }
                    Text(t(R.string.set_strength, strength.roundToInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        valueRange = 25f..150f,
                        steps = 4,
                        onValueChangeFinished = { AppSettings.update { it.copy(blurStrength = strength.roundToInt()) } },
                    )
                }
                SwitchRow(t(R.string.set_anim_bg), s.animatedBackground) { v -> AppSettings.update { it.copy(animatedBackground = v) } }
                Text(
                    t(R.string.set_glass_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ------------------------------------------------------------ installer & security
        item { Header(t(R.string.home_title)) }
        item { HomeSettingsCard(s) }
        item { Header(t(R.string.set_installer_security)) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t(R.string.set_install_method), style = MaterialTheme.typography.titleMedium)
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
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(t(R.string.set_allow_unknown)) }
                }
                Text(t(R.string.set_default_options), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                SwitchRow(t(R.string.opt_downgrade), s.defaultDowngrade) { v -> AppSettings.update { it.copy(defaultDowngrade = v) } }
                SwitchRow(t(R.string.opt_grant_all), s.defaultGrantAll) { v -> AppSettings.update { it.copy(defaultGrantAll = v) } }
                SwitchRow(t(R.string.opt_play), s.defaultPlayStoreSource) { v -> AppSettings.update { it.copy(defaultPlayStoreSource = v) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("VirusTotal", style = MaterialTheme.typography.titleMedium)
                Text(
                    t(R.string.set_vt_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = vtKey, onValueChange = { vtKey = it.trim() }, singleLine = true,
                    placeholder = { Text(t(R.string.api_key)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    // Masked like a password: the key is a secret.
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (showKey) t(R.string.hide) else t(R.string.show),
                            )
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { VirusTotal.apiKey = vtKey; message = t(R.string.key_saved) }) { Text(t(R.string.save)) }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { open("https://www.virustotal.com/gui/my-apikey") }) { Text(t(R.string.get_key)) }
                }
                SwitchRow(t(R.string.set_vt_auto), s.vtAutoScan) { v -> AppSettings.update { it.copy(vtAutoScan = v) } }
                SwitchRow(t(R.string.set_block_sig), s.blockSignatureMismatch) { v -> AppSettings.update { it.copy(blockSignatureMismatch = v) } }
                Text(
                    t(R.string.set_block_sig_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        // ------------------------------------------------------------ other areas
        item { Header(t(R.string.set_areas)) }
        item { LinkCard(t(R.string.set_link_updates), t(R.string.set_link_updates_sub), onOpenUpdates) }
        item { LinkCard(t(R.string.sec_school), t(R.string.set_link_school_sub), onOpenSchool) }
        item {
            LinkCard(t(R.string.set_notifications), t(R.string.set_notifications_sub)) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        // ------------------------------------------------------------ shizuku
        item { Header(t(R.string.shizuku)) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    t(R.string.set_status, when (shizuku) {
                        de.emil.shizubox.shizuku.ShizukuStatus.Ready -> t(R.string.st_connected) + (ShizukuManager.serverMode?.let { " ($it)" } ?: "")
                        de.emil.shizubox.shizuku.ShizukuStatus.Connecting -> t(R.string.shz_connecting)
                        de.emil.shizubox.shizuku.ShizukuStatus.NoPermission -> t(R.string.st_no_permission)
                        de.emil.shizubox.shizuku.ShizukuStatus.PermissionDenied -> t(R.string.shz_denied)
                        de.emil.shizubox.shizuku.ShizukuStatus.NotRunning -> t(R.string.st_not_running)
                        de.emil.shizubox.shizuku.ShizukuStatus.NotInstalled -> t(R.string.st_not_installed)
                    }),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(t(R.string.set_manager_app, ShizukuManager.shizukuPackage ?: t(R.string.not_found)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    t(R.string.set_without_shizuku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ShizukuManager.shizukuLaunchIntent()?.let { launch ->
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { context.startActivity(launch) }) { Text(t(R.string.open_shizuku)) }
                }
            }
        }

        // ------------------------------------------------------------ about
        item { Header(t(R.string.bk_title)) }
        item { BackupCard() }
        item {
            val errors by de.emil.shizubox.debug.ErrorLog.entries.collectAsStateWithLifecycle()
            LinkCard(t(R.string.el_title), if (errors.isEmpty()) t(R.string.el_none) else de.emil.shizubox.tp(R.plurals.el_n, errors.size, errors.size)) { errorLog = true }
        }
        item { Header(t(R.string.set_about)) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("ShizuBox ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                Text(t(R.string.set_license), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { open(REPO_URL) }) { Text("GitHub") }
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { open("$REPO_URL/releases") }) { Text(t(R.string.releases)) }
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                        scope.launch {
                            message = runCatching { Updates.add(REPO_URL) }.fold(
                                { t(R.string.set_self_tracked) },
                                { it.message ?: t(R.string.error) },
                            )
                        }
                    }) { Text(t(R.string.set_self_update)) }
                }
                Text(t(R.string.set_thanks), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
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
            icon -> Icon(Icons.Rounded.Palette, t(R.string.wallpaper), tint = Color.White)
        }
    }
}
