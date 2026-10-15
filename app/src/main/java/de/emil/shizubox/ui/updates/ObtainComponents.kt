package de.emil.shizubox.ui.updates

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.popScale
import kotlinx.coroutines.launch
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

/** Logo per source (from ObtainX's assets), null for unknown sources. */
fun sourceLogo(label: String?): Int? = when (label) {
    "GitHub" -> de.emil.shizubox.R.drawable.src_github
    "GitLab" -> de.emil.shizubox.R.drawable.src_gitlab
    "Codeberg" -> de.emil.shizubox.R.drawable.src_codeberg
    "F-Droid" -> de.emil.shizubox.R.drawable.src_fdroid
    "IzzyOnDroid" -> de.emil.shizubox.R.drawable.src_izzydroid
    "APKPure" -> de.emil.shizubox.R.drawable.src_apkpure
    "APKMirror" -> de.emil.shizubox.R.drawable.src_apkmirror
    else -> null
}

@Composable
fun SourceBadge(label: String?, size: Int = 20) {
    val logo = sourceLogo(label)
    if (logo == null) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Color.Gray),
            contentAlignment = Alignment.Center,
        ) { Text("?", color = Color.White, fontSize = (size * 0.5f).sp, fontWeight = FontWeight.Bold) }
        return
    }
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(logo),
        contentDescription = label,
        modifier = Modifier.size(size.dp),
        // The GitHub mark is black: show it in the text color so it stays visible in dark mode.
        colorFilter = if (label == "GitHub") androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
    )
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
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val pillColor = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    // Like the Clock app: the leading edge runs ahead, the trailing one follows → the pill stretches.
    LaunchedEffect(selected, bounds[selected]) {
        val (l, r) = bounds[selected] ?: return@LaunchedEffect
        if (left.value == 0f && right.value == 0f) {
            left.snapTo(l); right.snapTo(r); return@LaunchedEffect
        }
        val movingRight = l > left.value
        val fast = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
        val slow = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
        launch { left.animateTo(l, if (movingRight) slow else fast) }
        launch { right.animateTo(r, if (movingRight) fast else slow) }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .padding(6.dp)
                .animateContentSize(Motion.size)
                .drawBehind {
                    if (right.value > left.value) {
                        drawRoundRect(pillColor, Offset(left.value, 0f), Size(right.value - left.value, size.height), CornerRadius(size.height / 2))
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val active = i == selected
                val content by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, Motion.quick(), label = "navColor",
                )
                val pop = popScale(active.takeIf { it })
                Row(
                    Modifier
                        .onGloballyPositioned { c -> bounds[i] = c.positionInParent().x to c.positionInParent().x + c.size.width }
                        .clip(RoundedCornerShape(50))
                        .clickable { if (!active) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(i) }
                        .padding(horizontal = if (active) 18.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, label, tint = content, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
                    AnimatedVisibility(
                        active,
                        enter = fadeIn(Motion.quick()) + expandHorizontally(Motion.size),
                        exit = fadeOut(Motion.quick()) + shrinkHorizontally(Motion.size),
                    ) {
                        Text(
                            label,
                            color = content,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
