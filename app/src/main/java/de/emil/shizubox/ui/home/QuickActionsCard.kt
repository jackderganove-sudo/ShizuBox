package de.emil.shizubox.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.popScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.t
import de.emil.shizubox.tiles.QuickAction
import de.emil.shizubox.tiles.QuickActions
import de.emil.shizubox.tiles.TileInfo
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

private fun QuickAction.icon() = when (this) {
    QuickAction.Gaming -> R.drawable.ic_tile_gaming
    QuickAction.Animations -> R.drawable.ic_tile_animation
    QuickAction.Refresh -> R.drawable.ic_tile_refresh
    QuickAction.StayAwake -> R.drawable.ic_tile_coffee
    QuickAction.Boost -> R.drawable.ic_tile_boost
    QuickAction.Cache -> R.drawable.ic_tile_cache
    QuickAction.Clean -> R.drawable.ic_tile_clean
}

/** The quick settings tiles, also on the home screen. */
@Composable
fun QuickActionsCard() {
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<QuickAction, TileInfo>() }
    LaunchedEffect(Unit) {
        QuickAction.entries.forEach { a -> runCatching { QuickActions.read(a) }.onSuccess { states[a] = it } }
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(
            t(R.string.qa_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuickAction.entries.forEach { a ->
                val info = states[a]
                val active = info?.active == true
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = info != null) {
                            scope.launch {
                                runCatching { QuickActions.toggle(a, info!!) }
                                runCatching { QuickActions.read(a) }.onSuccess { states[a] = it }
                            }
                        }
                        .padding(vertical = 8.dp),
                ) {
                    // Active: filled, rounded square; inactive: muted circle – morphs with a spring.
                    val bg by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, Motion.quick(), label = "qaBg",
                    )
                    val fg by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, Motion.quick(), label = "qaFg",
                    )
                    val corner by animateDpAsState(if (active) 16.dp else 24.dp, Motion.bouncy(), label = "qaCorner")
                    val pop = popScale(info?.active)
                    Box(
                        Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = pop; scaleY = pop }
                            .clip(RoundedCornerShape(corner))
                            .background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(a.icon()), null, tint = fg)
                    }
                    Text(a.shortLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    AnimatedContent(
                        targetState = info?.subtitle ?: "…",
                        transitionSpec = {
                            (slideInVertically(Motion.offset) { it } + fadeIn(Motion.quick())) togetherWith
                                (slideOutVertically(Motion.offset) { -it } + fadeOut(Motion.quick()))
                        },
                        label = "qaValue",
                    ) { value ->
                        Text(
                            value,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
