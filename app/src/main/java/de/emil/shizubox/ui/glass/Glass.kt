package de.emil.shizubox.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.cos
import kotlin.math.sin

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Full-screen glass backdrop: the (system-blurred) wallpaper shows through the window,
 * tinted with a scrim, plus slowly drifting Material-You color blobs that the glass cards blur.
 */
@Composable
fun GlassBackground(
    windowBlurActive: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = rememberHazeState()
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    // Without window blur (e.g. battery saver) the wallpaper would be too busy, so cover it more.
    val scrimAlpha = when {
        !windowBlurActive -> 0.92f
        dark -> 0.55f
        else -> 0.45f
    }

    val transition = rememberInfiniteTransition(label = "blobs")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            drawRect(colors.surface.copy(alpha = scrimAlpha))
            val w = size.width
            val h = size.height
            val blobs = listOf(
                Triple(colors.primary, Offset(0.15f, 0.25f), 0.55f),
                Triple(colors.tertiary, Offset(0.85f, 0.45f), 0.5f),
                Triple(colors.secondary, Offset(0.3f, 0.85f), 0.6f),
            )
            blobs.forEachIndexed { i, (color, pos, radius) ->
                val drift = phase + i * 2.1f
                val center = Offset(
                    w * (pos.x + 0.08f * cos(drift)),
                    h * (pos.y + 0.05f * sin(drift * 1.3f)),
                )
                val r = w * radius
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = if (dark) 0.35f else 0.28f), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
            }
        }
        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalContentColor provides colors.onSurface,
        ) {
            content()
        }
    }
}

/** Frosted glass surface, like the Pixel quick-settings tiles. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tintAlpha: Float = if (isSystemInDarkTheme()) 0.45f else 0.55f,
    blurRadius: Dp = 28.dp,
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    val dark = isSystemInDarkTheme()
    val style = remember(tint, tintAlpha, blurRadius) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            backgroundColor(tint)
            noiseFactor(0.06f)
            colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = tintAlpha))))
        }
    }
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.22f else 0.6f),
            Color.White.copy(alpha = if (dark) 0.04f else 0.15f),
        ),
    )
    Column(
        modifier
            .clip(shape)
            .then(
                if (hazeState != null) Modifier.hazeBlur(HazeInput.Sources(hazeState), style)
                else Modifier,
            )
            .border(1.dp, edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}
