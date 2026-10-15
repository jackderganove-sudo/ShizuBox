package de.emil.shizubox.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Set by MainActivity around the top-level screens, so a card can grow into its screen. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks both ends of a container transform: the home card with [key] and the screen it opens.
 * Without the scopes (e.g. offscreen rendering) this does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedSection(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedSection.sharedBounds(
            rememberSharedContentState(key),
            nav,
            enter = fadeIn(Motion.quick()),
            exit = fadeOut(Motion.quick()),
            boundsTransform = { _, _ -> Motion.smooth() },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp)),
        )
    }
}
