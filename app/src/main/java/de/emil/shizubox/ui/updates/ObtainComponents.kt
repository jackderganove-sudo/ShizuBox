package de.emil.shizubox.ui.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Outlined, slightly tinted card like ObtainX's list items. */
@Composable
fun ObCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** Colors and short marks for each source (shown as a badge on the app icon). */
fun sourceStyle(label: String?): Pair<Color, String> = when (label) {
    "GitHub" -> Color(0xFF24292F) to "GH"
    "GitLab" -> Color(0xFFE24329) to "GL"
    "Codeberg" -> Color(0xFF2185D0) to "CB"
    "F-Droid" -> Color(0xFF1976D2) to "F"
    "IzzyOnDroid" -> Color(0xFF00A3A3) to "IZ"
    "APKPure" -> Color(0xFF24CD77) to "A"
    "APKMirror" -> Color(0xFFFF8B14) to "AM"
    else -> Color.Gray to "?"
}

@Composable
fun SourceBadge(label: String?, size: Int = 20) {
    val (color, mark) = sourceStyle(label)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, color = Color.White, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Version + date chip on the right of a list row. */
@Composable
fun VersionChip(version: String?, date: String?, highlight: Boolean) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .width(92.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(version ?: "?", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        if (!date.isNullOrBlank()) {
            Text(
                date,
                style = MaterialTheme.typography.labelMedium,
                textDecoration = if (highlight) TextDecoration.Underline else null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Collapsible group header ("Updates 4"). */
@Composable
fun GroupHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    ObCard(Modifier.fillMaxWidth(), onClick = onToggle, padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Entry of the "add app" menu: round icon, title, chevron. */
@Composable
fun MenuEntry(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Floating pill navigation: the active entry shows its label (like ObtainX). */
@Composable
fun PillNav(items: List<Pair<ImageVector, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(6.dp).animateContentSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val active = i == selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = if (active) 18.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    AnimatedVisibility(active) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
