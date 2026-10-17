@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.nav.AppNav
import de.emil.shizubox.nav.NavTarget
import de.emil.shizubox.search.Search
import de.emil.shizubox.search.SearchEntry
import de.emil.shizubox.search.SearchIndex
import de.emil.shizubox.search.SearchResult
import de.emil.shizubox.settings.AppSettings
import de.emil.shizubox.settings.Settings
import de.emil.shizubox.t
import de.emil.shizubox.tiles.QuickActions
import de.emil.shizubox.ui.apps.AppIcon
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Order of the home screen: saved keys first, then everything new in its default place. */
object HomeLayout {
    const val SHIZUKU = "shizuku"
    const val SCHOOL = "school"
    const val QUICK = "quick"

    fun defaultOrder(s: Settings) = listOf(SHIZUKU, SCHOOL, QUICK) + s.favorites.map { "fav:$it" } + Section.entries.map { "sec:${it.name}" }

    fun order(s: Settings): List<String> {
        val valid = defaultOrder(s).toSet()
        val saved = s.homeOrder.filter { it in valid }
        return saved + defaultOrder(s).filter { it !in saved }
    }

    fun favorite(id: String) = SearchIndex.byId(id)?.takeIf { it.available() }

    fun toggleFavorite(id: String) = AppSettings.update { s ->
        val favs = if (id in s.favorites) s.favorites - id else s.favorites + id
        // A new favourite goes right below the quick actions.
        val order = if (s.homeOrder.isEmpty() || id in s.favorites) s.homeOrder.filter { it != "fav:$id" }
        else s.homeOrder.toMutableList().apply { add(indexOf(QUICK).let { if (it < 0) 0 else it + 1 }, "fav:$id") }
        s.copy(favorites = favs, homeOrder = order)
    }

    fun reset() = AppSettings.update { it.copy(homeOrder = emptyList(), wideSections = emptySet(), hiddenSections = emptySet(), showSchoolCard = true, showQuickActions = true) }
}

/** Where a search entry leads, for a small second line ("System", "Settings", …). */
fun SearchEntry.place(): String = when {
    target.settings -> t(R.string.settings)
    target.section != null && (target.page != null || target.highlight != null) -> target.section.title
    else -> t(R.string.sx_area)
}

fun openEntry(entry: SearchEntry) {
    Search.remember(entry.id)
    AppNav.go(entry.target)
}

fun openApp(pkg: String) {
    Search.remember("app:$pkg")
    AppNav.go(NavTarget(Section.Apps, pkg))
}

/** The search results (or recent entries for an empty field) as list items of the home screen. */
fun LazyListScope.searchResults(query: String, results: List<SearchResult>, recent: List<String>, settings: Settings) {
    if (query.isBlank()) {
        if (recent.isNotEmpty()) item(key = "recent-h") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Text(t(R.string.sx_recent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { Search.clearRecent() }) { Text(t(R.string.sx_clear)) }
            }
        }
        itemsIndexed(recent, key = { _, id -> "r-$id" }) { _, id ->
            if (id.startsWith("app:")) {
                val pkg = id.removePrefix("app:")
                AppResult(pkg, de.emil.shizubox.apps.rememberAppLabel(pkg), Icons.Rounded.History)
            } else SearchIndex.byId(id)?.let { FeatureResult(it, settings, Icons.Rounded.History) }
        }
        if (recent.isEmpty()) item { Text(t(R.string.sx_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        return
    }
    if (results.isEmpty()) item { Text(t(R.string.sx_none, query), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
    itemsIndexed(results, key = { _, r -> if (r is SearchResult.Feature) "f-${r.entry.id}" else "a-${(r as SearchResult.App).pkg}" }) { i, r ->
        val listStart = rememberListStart(query)
        androidx.compose.foundation.layout.Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = null).enterStagger(i, listStart)) {
            when (r) {
                is SearchResult.Feature -> FeatureResult(r.entry, settings)
                is SearchResult.App -> AppResult(r.pkg, r.label)
            }
        }
    }
}

@Composable
private fun FeatureResult(e: SearchEntry, s: Settings, leading: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val hidden = e.target.section?.name?.let { it in s.hiddenSections } == true
    val fav = e.id in s.favorites
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { openEntry(e) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(leading ?: e.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(t(e.titleRes), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.place() + if (hidden) " · " + t(R.string.sx_hidden) else "", style = MaterialTheme.typography.bodySmall,
                    color = if (hidden) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.quick?.let { QuickSwitch(it) }
            IconButton(onClick = { HomeLayout.toggleFavorite(e.id) }) {
                Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, t(if (fav) R.string.sx_unpin else R.string.sx_pin),
                    tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A quick action switched right from the result (state read in the background). */
@Composable
private fun QuickSwitch(a: de.emil.shizubox.tiles.QuickAction) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    val info by produceState<de.emil.shizubox.tiles.TileInfo?>(null, version) { value = runCatching { withContext(Dispatchers.IO) { QuickActions.read(a) } }.getOrNull() }
    val i = info ?: return
    Switch(checked = i.active, onCheckedChange = {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { QuickActions.toggle(a, i) } }
            version++
        }
    })
}

@Composable
private fun AppResult(pkg: String, label: String, leading: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { openApp(pkg) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) Icon(leading, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            AppIcon(pkg, 32)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t(R.string.sx_app), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A pinned page or tool on the home screen (half width). */
@Composable
fun FavoriteTile(e: SearchEntry, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    GlassCard(modifier.height(96.dp), contentPadding = 14.dp, onClick = { openEntry(e) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(e.icon, colors.tertiaryContainer, colors.onTertiaryContainer, 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t(e.titleRes), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(e.place(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * Edit mode: drag the handle (long press anywhere on a row) to reorder – springy, with haptics –,
 * show/hide, choose the tile size, remove favourites, add favourites, reset.
 * Shizuku status stays (only movable); the school card can only be hidden, as before.
 */
@Composable
fun HomeEditor(onDone: () -> Unit) {
    val s by AppSettings.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var order by remember { mutableStateOf(HomeLayout.order(s)) }
    LaunchedEffect(s.favorites) { order = HomeLayout.order(AppSettings.current) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var picking by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun save() = AppSettings.update { it.copy(homeOrder = order) }

    LazyColumn(
        state = listState,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(t(R.string.he_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Button(shapes = ButtonDefaults.shapes(), onClick = { save(); onDone() }) { Text(t(R.string.he_done)) }
            }
            Text(t(R.string.he_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        itemsIndexed(order, key = { _, k -> k }) { _, key ->
            val dragging = key == dragKey
            val lift by animateFloatAsState(if (dragging) 1.03f else 1f, Motion.bouncy(), label = "lift")
            EditorRow(
                key, s,
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .then(if (dragging) Modifier else Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = null))
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f; scaleX = lift; scaleY = lift }
                    .pointerInput(key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragKey = key; dragOffset = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragEnd = { dragKey = null; dragOffset = 0f; save() },
                            onDragCancel = { dragKey = null; dragOffset = 0f; save() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val info = listState.layoutInfo.visibleItemsInfo
                                val me = info.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                                val center = me.offset + dragOffset + me.size / 2
                                val target = info.firstOrNull { it.key != key && it.key is String && (it.key as String) in order && center.toInt() in it.offset..(it.offset + it.size) }
                                    ?: return@detectDragGesturesAfterLongPress
                                val from = order.indexOf(key)
                                val to = order.indexOf(target.key as String)
                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                                // The row moved by one slot: keep it under the finger.
                                dragOffset -= (target.offset - me.offset).toFloat()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        )
                    },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { picking = true }) {
                    Icon(Icons.Rounded.Star, null, Modifier.padding(end = 8.dp))
                    Text(t(R.string.he_add_fav))
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { confirmReset = true }) { Text(t(R.string.he_reset)) }
            }
        }
    }
    if (picking) FavoritePicker(s) { picking = false }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(t(R.string.he_reset_q)) },
        text = { Text(t(R.string.he_reset_body)) },
        confirmButton = { TextButton(onClick = { confirmReset = false; HomeLayout.reset(); order = HomeLayout.order(AppSettings.current) }) { Text(t(R.string.he_reset)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(t(R.string.cancel)) } },
    )
}

@Composable
private fun EditorRow(key: String, s: Settings, modifier: Modifier) {
    val sec = key.removePrefix("sec:").takeIf { key.startsWith("sec:") }?.let { n -> Section.entries.firstOrNull { it.name == n } }
    val fav = key.removePrefix("fav:").takeIf { key.startsWith("fav:") }?.let { HomeLayout.favorite(it) }
    val (title, icon) = when {
        key == HomeLayout.SHIZUKU -> "Shizuku" to Icons.Rounded.Lock
        key == HomeLayout.SCHOOL -> t(R.string.he_school_card) to Section.School.icon
        key == HomeLayout.QUICK -> t(R.string.qa_title) to Icons.Rounded.GridView
        sec != null -> sec.title to sec.icon
        fav != null -> t(fav.titleRes) to fav.icon
        else -> key to Icons.Rounded.Close
    }
    GlassCard(modifier.fillMaxWidth(), contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragHandle, t(R.string.he_drag), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (fav != null) Text(t(R.string.he_favorite) + " · " + fav.place(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (key == HomeLayout.SHIZUKU) Text(t(R.string.he_fixed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sec == Section.School && s.showSchoolCard) Text(t(R.string.he_school_tile), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                key == HomeLayout.SCHOOL -> VisibilityButton(s.showSchoolCard) { v -> AppSettings.update { it.copy(showSchoolCard = v) } }
                key == HomeLayout.QUICK -> VisibilityButton(s.showQuickActions) { v -> AppSettings.update { it.copy(showQuickActions = v) } }
                sec != null -> {
                    val wide = sec.name in s.wideSections
                    IconButton(onClick = { AppSettings.update { it.copy(wideSections = if (wide) it.wideSections - sec.name else it.wideSections + sec.name) } }) {
                        Icon(if (wide) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, t(if (wide) R.string.he_wide else R.string.he_small))
                    }
                    VisibilityButton(sec.name !in s.hiddenSections) { v -> AppSettings.update { it.copy(hiddenSections = if (v) it.hiddenSections - sec.name else it.hiddenSections + sec.name) } }
                }
                fav != null -> IconButton(onClick = { HomeLayout.toggleFavorite(fav.id) }) { Icon(Icons.Rounded.Close, t(R.string.sx_unpin)) }
            }
        }
    }
}

@Composable
private fun VisibilityButton(shown: Boolean, onChange: (Boolean) -> Unit) {
    IconButton(onClick = { onChange(!shown) }) {
        Icon(if (shown) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, t(if (shown) R.string.he_hide else R.string.he_show),
            tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Choose pages/tools to pin: the search index without the sections themselves, with a filter. */
@Composable
private fun FavoritePicker(s: Settings, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Search.prepare() }
    val list = if (q.isBlank()) SearchIndex.entries.filter { it.available() && (it.target.page != null || it.target.highlight != null) }
    else Search.search(q).filterIsInstance<SearchResult.Feature>().map { it.entry }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.he_add_fav)) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text(t(R.string.sx_field)) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.height(380.dp).padding(top = 8.dp)) {
                    itemsIndexed(list, key = { _, e -> e.id }) { _, e ->
                        val pinned = e.id in s.favorites
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(t(e.titleRes), style = MaterialTheme.typography.bodyMedium)
                                Text(e.place(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { HomeLayout.toggleFavorite(e.id) }) {
                                Icon(if (pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(R.string.he_done)) } },
    )
}
