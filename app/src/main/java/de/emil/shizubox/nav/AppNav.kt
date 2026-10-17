package de.emil.shizubox.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.ui.home.Section
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where to go: a home section, optionally a sub page inside it and a card to light up.
 * [page] depends on the section: System → SystemPage, Cleaner → CleanerPage, Apps → AppsNav key,
 * Downloader → page index, Settings (section null + [settings]) → nothing.
 */
data class NavTarget(
    val section: Section? = null,
    val page: String? = null,
    val settings: Boolean = false,
    val highlight: String? = null,
)

/** One way to jump anywhere – used by the search and the home favourites. MainActivity carries it out. */
object AppNav {
    private val _requests = MutableSharedFlow<NavTarget>(extraBufferCapacity = 4)
    val requests = _requests.asSharedFlow()

    fun go(t: NavTarget) {
        _requests.tryEmit(t)
        t.highlight?.let { Highlight.flash(it) }
    }
}

/** A card that should light up once it is on screen ([Modifier.highlight]). */
object Highlight {
    val pending = MutableStateFlow<String?>(null)
    fun flash(key: String) { pending.value = key }
    fun done(key: String) { if (pending.value == key) pending.value = null }
}

/**
 * Lights the element up (two soft pulses of the primary colour and a ring) when [Highlight] asks
 * for [key], after scrolling it into view.
 */
@Composable
fun Modifier.highlight(key: String): Modifier {
    val pending by Highlight.pending.collectAsStateWithLifecycle()
    val bring = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.primary
    // Lazy lists also compose items ahead of time without placing them; only a placed card may
    // claim the highlight, otherwise the list stops scrolling before the card is on screen.
    val placed = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(pending, placed.value) {
        if (pending != key || !placed.value) return@LaunchedEffect
        delay(250)
        runCatching { bring.bringIntoView() }
        // The glow runs in its own scope: done() changes `pending`, which restarts this effect.
        scope.launch {
            repeat(2) {
                glow.animateTo(1f, tween(280))
                glow.animateTo(0f, tween(520))
            }
        }
        Highlight.done(key)
    }
    return this.onGloballyPositioned { placed.value = it.isAttached }.bringIntoViewRequester(bring).drawWithContent {
        drawContent()
        val g = glow.value
        if (g > 0f) {
            val r = CornerRadius(28.dp.toPx())
            drawRoundRect(color.copy(alpha = 0.18f * g), cornerRadius = r)
            drawRoundRect(color.copy(alpha = 0.9f * g), cornerRadius = r, style = Stroke(3.dp.toPx()))
        }
    }
}

/**
 * Cards further down a lazy list are not composed yet, so they cannot scroll themselves into
 * view: while one of [keys] is pending, the list scrolls down step by step until the card appears
 * (it then takes over) or the end is reached.
 */
@Composable
fun HighlightScroll(list: LazyListState, keys: Set<String>) {
    val pending by Highlight.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val k = pending ?: return@LaunchedEffect
        if (k !in keys) return@LaunchedEffect
        delay(400)
        repeat(40) {
            if (Highlight.pending.value != k || !list.canScrollForward) return@LaunchedEffect
            list.animateScrollBy(500f)
        }
    }
}
