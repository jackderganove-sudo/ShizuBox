package de.emil.shizubox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.school.SchoolState
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.ui.school.autoEndLabel
import de.emil.shizubox.ui.glass.GlassCard

enum class Section(val title: String, val subtitle: String, val icon: ImageVector) {
    School("Schulmodus", "Handy an, aber absolut nichts passiert", Icons.Rounded.School),
    Customization("Customization", "Animationen, DPI, Statusleiste", Icons.Rounded.Palette),
    Apps("Apps", "Freezer, Berechtigungen, AppOps, Akku", Icons.Rounded.Apps),
    Updates("Updates", "GitHub, F-Droid, APKPure …", Icons.Rounded.SystemUpdate),
    System("System", "Gaming, Installer, Logcat", Icons.Rounded.Terminal),
}

@Composable
fun HomeScreen(
    shizukuStatus: ShizukuStatus,
    serverMode: String?,
    school: SchoolState,
    busy: Boolean,
    updateCount: Int,
    onShizukuAction: () -> Unit,
    onActivateSchool: () -> Unit,
    onOpenSection: (Section) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Big empty header area keeps everything interactive in the lower, thumb-friendly part.
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Settings, "Einstellungen")
                }
            }
            Column(Modifier.padding(start = 8.dp, top = 48.dp, bottom = 20.dp)) {
                Text("ShizuBox", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Systemwerkzeuge über Shizuku",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { ShizukuCard(shizukuStatus, serverMode, onShizukuAction) }
        item {
            SchoolCard(
                school = school,
                canActivate = shizukuStatus == ShizukuStatus.Ready && !busy && !school.pendingRestore,
                busy = busy,
                onActivate = onActivateSchool,
                onClick = { onOpenSection(Section.School) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTile(Section.Customization, Modifier.weight(1f)) { onOpenSection(Section.Customization) }
                SectionTile(Section.Apps, Modifier.weight(1f)) { onOpenSection(Section.Apps) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTile(
                    Section.Updates,
                    Modifier.weight(1f),
                    subtitle = if (updateCount > 0) "$updateCount Update${if (updateCount > 1) "s" else ""} verfügbar" else null,
                ) { onOpenSection(Section.Updates) }
                SectionTile(Section.System, Modifier.weight(1f)) { onOpenSection(Section.System) }
            }
        }
    }
}

@Composable
private fun ShizukuCard(status: ShizukuStatus, serverMode: String?, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (title, detail, action) = when (status) {
        ShizukuStatus.Ready -> Triple("Shizuku verbunden", "Läuft im ${serverMode ?: "ADB"}-Modus", null)
        ShizukuStatus.Connecting -> Triple("Verbinde …", "Shell-Dienst wird gestartet", null)
        ShizukuStatus.NoPermission -> Triple("Zugriff nötig", "ShizuBox braucht die Shizuku-Berechtigung", "Erlauben")
        ShizukuStatus.PermissionDenied -> Triple("Zugriff verweigert", "In der Shizuku-App unter „Autorisierte Apps“ erlauben", "Shizuku öffnen")
        ShizukuStatus.NotRunning -> Triple("Shizuku läuft nicht", "Updates, Installer und App-Liste gehen trotzdem", "Starten")
        ShizukuStatus.NotInstalled -> Triple("Shizuku fehlt", "Updates, Installer und App-Liste gehen trotzdem", "Installieren")
    }
    val dot = when (status) {
        ShizukuStatus.Ready -> Color(0xFF4CD964)
        ShizukuStatus.Connecting -> colors.tertiary
        else -> colors.error
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(start = 4.dp, end = 16.dp)
                    .size(12.dp)
                    .background(dot, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun SchoolCard(
    school: SchoolState,
    canActivate: Boolean,
    busy: Boolean,
    onActivate: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        tint = colors.primaryContainer,
        onClick = onClick,
        contentPadding = 24.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Section.School.icon, colors.primary, colors.onPrimary, 56)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Section.School.title, style = MaterialTheme.typography.headlineSmall, color = colors.onPrimaryContainer)
                Text(
                    when {
                        school.pendingRestore -> "Wiederherstellung ausstehend"
                        school.autoEndMinutes > 0 -> "Aus · endet nach ${autoEndLabel(school.autoEndMinutes)}"
                        else -> "Aus"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            Section.School.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(
            onClick = onActivate,
            enabled = canActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Wird aktiviert …" else "Aktivieren", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SectionTile(section: Section, modifier: Modifier, subtitle: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(modifier.height(148.dp), onClick = onClick) {
        IconBubble(section.icon, colors.secondaryContainer, colors.onSecondaryContainer, 44)
        Spacer(Modifier.weight(1f))
        Text(section.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle ?: section.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (subtitle != null) colors.primary else colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionRow(section: Section, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(section.icon, colors.tertiaryContainer, colors.onTertiaryContainer, 44)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun IconBubble(icon: ImageVector, container: Color, content: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}
