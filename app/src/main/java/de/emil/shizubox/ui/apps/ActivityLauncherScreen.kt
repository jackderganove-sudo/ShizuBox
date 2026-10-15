package de.emil.shizubox.ui.apps

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.apps.ActivityEntry
import de.emil.shizubox.apps.ActivityLauncher
import de.emil.shizubox.apps.AppActivities
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.EmptyState
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.enterStagger
import de.emil.shizubox.ui.motion.popScale
import de.emil.shizubox.ui.motion.rememberListStart
import kotlinx.coroutines.launch

/** Cross-app Activity Launcher: all apps with their activities, search, filters, favorites. */
@Composable
fun ActivityLauncherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by ActivityLauncher.apps.collectAsStateWithLifecycle()
    val loading by ActivityLauncher.loading.collectAsStateWithLifecycle()
    val favorites by ActivityLauncher.favorites.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var withSystem by rememberSaveable { mutableStateOf(true) }
    var exportedOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { ActivityLauncher.load() }
    val listStart = rememberListStart(apps != null)

    val q = query.trim()
    // Per app: the activities that pass the filters; with a query, apps also match by activity name.
    val shown = remember(apps, q, withSystem, exportedOnly) {
        apps.orEmpty().filter { withSystem || !it.isSystem }.mapNotNull { app ->
            val acts = app.activities.filter { !exportedOnly || it.exported }
            if (acts.isEmpty()) return@mapNotNull null
            if (q.isEmpty()) return@mapNotNull app to acts
            val appHit = app.label.contains(q, true) || app.pkg.contains(q, true)
            val actHits = acts.filter { it.name.contains(q, true) || it.label?.contains(q, true) == true }
            when {
                appHit -> app to acts
                actHits.isNotEmpty() -> app to actHits
                else -> null
            }
        }
    }
    val favoriteRows = remember(apps, favorites) {
        val byPkg = apps.orEmpty().associateBy { it.pkg }
        favorites.mapNotNull { key ->
            val pkg = key.substringBefore('/')
            val name = key.substringAfter('/')
            val app = byPkg[pkg] ?: return@mapNotNull null
            app to (app.activities.firstOrNull { it.name == name } ?: return@mapNotNull null)
        }.sortedBy { it.first.label.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
                Text(t(R.string.al_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { ActivityLauncher.load(force = true) }, enabled = !loading) { Icon(Icons.Rounded.Refresh, t(R.string.refresh)) }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(t(R.string.al_search)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilterChip(selected = withSystem, onClick = { withSystem = !withSystem }, label = { Text(t(R.string.al_system)) })
                FilterChip(selected = exportedOnly, onClick = { exportedOnly = !exportedOnly }, label = { Text(t(R.string.al_exported_only)) })
            }
            Text(t(R.string.al_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (favoriteRows.isNotEmpty() && q.isEmpty()) {
            item(key = "favorites") {
                GlassCard(Modifier.fillMaxWidth().animateItem(placementSpec = Motion.offset), tint = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(t(R.string.al_favorites), style = MaterialTheme.typography.titleMedium)
                    favoriteRows.forEach { (app, entry) -> ActivityRow(app.pkg, app.label, entry, showApp = true) }
                }
            }
        }
        if (apps == null || (loading && apps.isNullOrEmpty())) {
            item { Text(t(R.string.al_loading), modifier = Modifier.padding(8.dp)) }
        } else if (shown.isEmpty()) {
            item { EmptyState(Icons.Rounded.Widgets, t(R.string.al_nothing)) }
        }
        itemsIndexed(shown, key = { _, it -> it.first.pkg }) { index, (app, acts) ->
            // While searching by activity name, matching apps open by themselves.
            val open = expanded == app.pkg || (q.isNotEmpty() && !app.label.contains(q, true) && !app.pkg.contains(q, true))
            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset).enterStagger(index, listStart)) {
                AppActivitiesCard(app, acts, open, onToggle = { expanded = if (expanded == app.pkg) null else app.pkg }, onCopy = {
                    copy(context, app.pkg)
                    Toast.makeText(context, t(R.string.al_copied_pkg), Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}

@Composable
private fun AppActivitiesCard(app: AppActivities, acts: List<ActivityEntry>, open: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = onToggle, onLongClick = onCopy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(app.pkg, app.versionName?.let { "v$it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t(R.string.al_count, app.exportedCount, app.activities.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (open) acts.forEach { ActivityRow(app.pkg, app.label, it) }
    }
}

/**
 * One activity with star, start and shortcut buttons; long press copies its name.
 * Used by the Activity Launcher and the app details page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityRow(pkg: String, appLabel: String, entry: ActivityEntry, showApp: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites by ActivityLauncher.favorites.collectAsStateWithLifecycle()
    val fav = ActivityLauncher.key(pkg, entry.name) in favorites
    val short = entry.name.removePrefix(pkg).ifEmpty { entry.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (entry.exported) scope.launch {
                        ActivityLauncher.launch(context, pkg, entry.name)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    } else {
                        Toast.makeText(context, t(R.string.al_not_exported), Toast.LENGTH_LONG).show()
                    }
                },
                onLongClick = {
                    copy(context, entry.name)
                    Toast.makeText(context, t(R.string.al_copied_act), Toast.LENGTH_SHORT).show()
                },
            ),
    ) {
        if (showApp) {
            AppIcon(pkg, 28)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            (entry.label ?: if (showApp) appLabel else null)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                short,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (entry.exported) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.exported) {
                Text(t(R.string.al_not_startable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
            }
        }
        val pop = popScale(fav)
        IconButton(onClick = { ActivityLauncher.toggleFavorite(pkg, entry.name) }) {
            Icon(
                if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                t(R.string.al_favorite),
                tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop },
            )
        }
        if (entry.exported) {
            IconButton(onClick = {
                scope.launch { ActivityLauncher.launch(context, pkg, entry.name)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
            }) { Icon(Icons.Rounded.PlayArrow, t(R.string.open)) }
            IconButton(onClick = { ActivityLauncher.pinShortcut(context, pkg, entry.name, entry.label ?: appLabel) }) {
                Icon(Icons.Rounded.AddToHomeScreen, t(R.string.act_shortcut))
            }
        }
    }
}

private fun copy(context: android.content.Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ShizuBox", text))
}
