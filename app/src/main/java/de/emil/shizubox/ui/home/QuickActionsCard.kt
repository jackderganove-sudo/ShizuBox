package de.emil.shizubox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(a.icon()),
                            null,
                            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(a.shortLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        info?.subtitle ?: "…",
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
