package de.emil.shizubox.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * One place for all motion in the app (Material 3 Expressive style): springs with a little
 * overshoot, used everywhere so the app moves consistently.
 */
object Motion {
    /** Playful, slightly bouncy – for presses, toggles, icons. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** Calm spring without visible overshoot – for layout, sizes, page transitions. */
    fun <T> smooth() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Fast effects such as colors and fades. */
    fun <T> quick() = spring<T>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    val offset = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))
    val size = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize(1, 1))

    /** Scale of a pressed card or button. */
    const val PRESSED_SCALE = 0.95f
}

/**
 * True when the user turned off animations ("Remove animations" sets the animator scale to 0).
 * Compose already snaps finite animations then; this is for endless ones like pulsing.
 */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Springy shrink while pressed. Pass the same interaction source as the clickable. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = Motion.PRESSED_SCALE): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.bouncy(), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Page transition: content slides in the navigation direction and fades (kept short, tabs must feel instant). */
fun <S> AnimatedContentTransitionScope<S>.slideFade(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    val fast = spring(dampingRatio = 0.9f, stiffness = 1800f, visibilityThreshold = IntOffset(1, 1))
    return (slideInHorizontally(fast) { it / 10 * dir } + fadeIn(tween(140))) togetherWith
        (slideOutHorizontally(fast) { -it / 12 * dir } + fadeOut(tween(90)))
}

/** Opening a sub page: grows a little and fades in. */
fun <S> AnimatedContentTransitionScope<S>.openClose(opening: Boolean): ContentTransform =
    if (opening) {
        (fadeIn(Motion.quick()) + scaleIn(Motion.smooth(), initialScale = 0.94f)) togetherWith fadeOut(tween(100))
    } else {
        fadeIn(Motion.quick()) togetherWith (fadeOut(tween(120)) + androidx.compose.animation.scaleOut(Motion.smooth(), targetScale = 0.94f))
    }

/**
 * Text whose characters roll vertically when they change (like the Clock app): only the
 * characters that differ move, up when the number grows and down when it shrinks.
 */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    fun number(t: String) = t.filter { it.isDigit() }.toLongOrNull() ?: 0
    val last = remember { longArrayOf(number(text)) }
    val up = number(text) >= last[0]
    androidx.compose.runtime.SideEffect { last[0] = number(text) }
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            // Keyed from the right, so "9" → "10" rolls the last digit and adds the new one.
            androidx.compose.runtime.key(text.length - 1 - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val d = if (up) 1 else -1
                        (slideInVertically(Motion.offset) { it * d } + fadeIn(Motion.quick())) togetherWith
                            (slideOutVertically(Motion.offset) { -it * d } + fadeOut(Motion.quick()))
                    },
                    label = "char",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/** Endless gentle pulse between [min] and 1 (or a constant 1 with reduced motion). */
@Composable
fun pulse(min: Float = 0.55f, periodMs: Int = 1400, enabled: Boolean = true): Float {
    if (!enabled || reduceMotion()) return 1f
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(
        initialValue = 1f, targetValue = min,
        animationSpec = infiniteRepeatable(tween(periodMs / 2, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    return v
}

/**
 * Like [pulse], but battery-friendly for something that runs all the time: the value is a State
 * to be read only in a draw/graphicsLayer lambda (no recomposition), updated at [fps] instead of
 * every display frame, and paused while the app is not visible.
 */
@Composable
fun throttledPulse(min: Float = 0.55f, periodMs: Int = 1400, enabled: Boolean = true, fps: Int = 30): androidx.compose.runtime.State<Float> {
    val state = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val still = !enabled || reduceMotion()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(still, lifecycle) {
        if (still) { state.floatValue = 1f; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            val start = android.os.SystemClock.uptimeMillis()
            while (true) {
                val t = ((android.os.SystemClock.uptimeMillis() - start) % periodMs) / periodMs.toFloat()
                // 1 → min → 1 with a soft ease, like the infinite transition it replaces.
                val tri = if (t < 0.5f) t * 2 else (1 - t) * 2
                val eased = androidx.compose.animation.core.FastOutSlowInEasing.transform(tri)
                state.floatValue = 1f - (1f - min) * eased
                kotlinx.coroutines.delay(1000L / fps)
            }
        }
    }
    return state
}

/** Short "pop" when [trigger] changes: 1 → [peak] → 1 with a bouncy spring. */
@Composable
fun popScale(trigger: Any?, peak: Float = 1.25f): Float {
    val a = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        a.snapTo(peak)
        a.animateTo(1f, Motion.bouncy())
    }
    return a.value
}

/** Time a list became visible; items shown shortly after it enter staggered (see [enterStagger]). */
@Composable
fun rememberListStart(key: Any? = Unit): Long = remember(key) { android.os.SystemClock.uptimeMillis() }

/**
 * Staggered entrance for the first items of a list: each slides up and fades in a little
 * after the previous one. Items scrolled into view later appear immediately.
 */
@Composable
fun Modifier.enterStagger(index: Int, listStart: Long): Modifier {
    val late = remember { android.os.SystemClock.uptimeMillis() - listStart > 700 }
    if (late || reduceMotion()) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(minOf(index, 12) * 35L)
        a.animateTo(1f, Motion.smooth())
    }
    return this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 40.dp.toPx()
    }
}

/** Pops in with a bouncy spring the first time it is shown (e.g. a success check mark). */
@Composable
fun Modifier.popIn(): Modifier {
    val still = reduceMotion()
    val a = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    return this.graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = a.value.coerceIn(0f, 1f) }
}

/** Progress that glides to new values instead of jumping; pass straight to `progress =`. */
@Composable
fun animatedProgress(target: Float): () -> Float {
    val v = animateFloatAsState(target.coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), label = "progress")
    return { v.value }
}
