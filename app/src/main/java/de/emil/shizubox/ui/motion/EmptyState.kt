package de.emil.shizubox.ui.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Small living illustration for empty lists: a floating icon on two slowly turning blobs. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String? = null, modifier: Modifier = Modifier) {
    val still = reduceMotion()
    val t = rememberInfiniteTransition(label = "empty")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "float")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = androidx.compose.animation.core.LinearEasing)), label = "turn")
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(112.dp)
                    .graphicsLayer { rotationZ = if (still) 0f else turn }
                    .clip(RoundedCornerShape(38.dp))
                    .background(colors.secondaryContainer.copy(alpha = 0.55f)),
            )
            Box(
                Modifier.size(88.dp)
                    .graphicsLayer { rotationZ = if (still) 0f else -turn * 1.5f }
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.7f)),
            )
            Box(
                Modifier.size(60.dp)
                    .graphicsLayer { translationY = if (still) 0f else (float - 0.5f) * 14.dp.toPx() }
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = colors.onPrimary) }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}
