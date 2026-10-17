@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.home

import de.emil.shizubox.R
import de.emil.shizubox.t
import de.emil.shizubox.tp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.pulse
import de.emil.shizubox.ui.motion.reduceMotion
import de.emil.shizubox.ui.motion.sharedSection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.school.SchoolState
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.ui.school.autoEndLabel
import de.emil.shizubox.ui.glass.GlassCard

enum class Section(private val titleRes: Int, private val subtitleRes: Int, val icon: ImageVector) {
    School(R.string.sec_school, R.string.sec_school_sub, Icons.Rounded.School),
    Customization(R.string.sec_custom, R.string.sec_custom_sub, Icons.Rounded.Palette),
    Automations(R.string.sec_auto, R.string.sec_auto_sub, Icons.Rounded.AutoMode),
    Apps(R.string.sec_apps, R.string.sec_apps_sub, Icons.Rounded.Apps),
    Updates(R.string.sec_updates, R.string.sec_updates_sub, Icons.Rounded.SystemUpdate),
    System(R.string.sec_system, R.string.sec_system_sub, Icons.Rounded.Terminal),
    Downloader(R.string.sec_downloader, R.string.sec_downloader_sub, Icons.Rounded.Download),
    Cleaner(R.string.sec_cleaner, R.string.sec_cleaner_sub, Icons.Rounded.CleaningServices),
    ;

    // Resolved on every access, so a language change applies immediately.
    val title get() = t(titleRes)
    val subtitle get() = t(subtitleRes)
}

/** Start states for debug screenshots (search open with a query, edit mode). */
object HomeDebug {
    var query: String? = null
    var editing = false
}

@Composable
fun HomeScreen(
    shizukuStatus: ShizukuStatus,
    serverMode: String?,
    school: SchoolState,
    busy: Boolean,
    updateCount: Int,
    onShizukuAction: () -> Unit,
    onActivateSchool: () -> Unit,
    onOpenSection: (Section) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings by de.emil.shizubox.settings.AppSettings.state.collectAsStateWithLifecycle()
    var searching by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(HomeDebug.query != null) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(HomeDebug.query.orEmpty()) }
    var editing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(HomeDebug.editing) }
    val recent by de.emil.shizubox.search.Search.recent.collectAsStateWithLifecycle()
    var indexReady by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(searching) { if (searching) { de.emil.shizubox.search.Search.prepare(); indexReady = true } }
    // Results while typing; the index is small and prepared in the background.
    val results = androidx.compose.runtime.remember(query, searching, indexReady) { if (searching && indexReady) de.emil.shizubox.search.Search.search(query) else emptyList() }
    androidx.activity.compose.BackHandler(searching) { searching = false; query = "" }
    androidx.activity.compose.BackHandler(editing) { editing = false }
    if (editing) {
        HomeEditor(onDone = { editing = false })
        return
    }
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Big empty header area keeps everything interactive in the lower, thumb-friendly part.
        item(key = "header") {
            Row(Modifier.fillMaxWidth().animateContentSize(Motion.bouncy()), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(t(R.string.sx_field)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
                    androidx.compose.material3.IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.Rounded.Close, t(R.string.close))
                    }
                } else {
                    androidx.compose.material3.IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, t(R.string.sx_search)) }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, t(R.string.he_title)) }
                    androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Settings, t(R.string.settings))
                    }
                }
            }
            if (!searching) Column(Modifier.padding(start = 8.dp, top = 48.dp, bottom = 20.dp)) {
                Text("ShizuBox", style = MaterialTheme.typography.displaySmall)
                Text(
                    t(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (searching) {
            searchResults(query, results, recent, settings)
            return@LazyColumn
        }
        // The user's order; tiles of half width (small sections, favourites) are paired up.
        val half = ArrayList<Pair<String, @Composable (Modifier) -> Unit>>()
        fun flush() {
            if (half.isEmpty()) return
            val pairs = half.chunked(2)
            half.clear()
            pairs.forEach { pair ->
                item(key = "row-" + pair.joinToString { it.first }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.offset, fadeOutSpec = null)) {
                        pair.forEach { (_, content) -> content(Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        for (key in HomeLayout.order(settings)) {
            when {
                key == HomeLayout.SHIZUKU -> { flush(); item(key = key) { ShizukuCard(shizukuStatus, serverMode, onShizukuAction) } }
                key == HomeLayout.SCHOOL -> if (settings.showSchoolCard && Section.School.name !in settings.hiddenSections) {
                    flush()
                    item(key = key) {
                        SchoolCard(
                            school = school,
                            canActivate = shizukuStatus == ShizukuStatus.Ready && !busy && !school.pendingRestore,
                            busy = busy,
                            onActivate = onActivateSchool,
                            onClick = { onOpenSection(Section.School) },
                        )
                    }
                }
                key == HomeLayout.QUICK -> if (settings.showQuickActions && shizukuStatus == ShizukuStatus.Ready) { flush(); item(key = key) { QuickActionsCard() } }
                key.startsWith("fav:") -> HomeLayout.favorite(key.removePrefix("fav:"))?.let { e ->
                    val tile: @Composable (Modifier) -> Unit = { m -> FavoriteTile(e, m) }
                    half += key to tile
                }
                key.startsWith("sec:") -> {
                    val sec = Section.entries.firstOrNull { it.name == key.removePrefix("sec:") } ?: continue
                    // School mode gets a tile only when its big card is turned off.
                    if (sec.name in settings.hiddenSections || (sec == Section.School && settings.showSchoolCard)) continue
                    val sub = if (sec == Section.Updates && updateCount > 0) tp(R.plurals.updates_available, updateCount, updateCount) else null
                    if (sec.name in settings.wideSections) {
                        flush()
                        item(key = key) { SectionRow(sec, sub) { onOpenSection(sec) } }
                    } else {
                        val tile: @Composable (Modifier) -> Unit = { m -> SectionTile(sec, m, subtitle = sub) { onOpenSection(sec) } }
                        half += key to tile
                    }
                }
            }
        }
        flush()
    }
}

@Composable
private fun ShizukuCard(status: ShizukuStatus, serverMode: String?, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (title, detail, action) = when (status) {
        ShizukuStatus.Ready -> Triple(t(R.string.shz_connected), t(R.string.shz_mode, serverMode ?: "ADB"), null)
        ShizukuStatus.Connecting -> Triple(t(R.string.shz_connecting), t(R.string.shz_connecting_sub), null)
        ShizukuStatus.NoPermission -> Triple(t(R.string.shz_need_access), t(R.string.shz_need_access_sub), t(R.string.allow))
        ShizukuStatus.PermissionDenied -> Triple(t(R.string.shz_denied), t(R.string.shz_denied_sub), t(R.string.open_shizuku))
        ShizukuStatus.NotRunning -> Triple(t(R.string.shz_not_running), t(R.string.shz_works_without), t(R.string.start))
        ShizukuStatus.NotInstalled -> Triple(t(R.string.shz_missing), t(R.string.shz_works_without), t(R.string.install))
    }
    val dot = when (status) {
        ShizukuStatus.Ready -> Color(0xFF4CD964)
        ShizukuStatus.Connecting -> colors.tertiary
        else -> colors.error
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Connected: a soft halo breathes around the dot.
            val dotColor by animateColorAsState(dot, Motion.quick(), label = "dot")
            // Read only in the graphicsLayer lambda and 20 fps: it runs as long as the home screen
            // is open, so it must not recompose the card every frame.
            val halo = de.emil.shizubox.ui.motion.throttledPulse(min = 0f, periodMs = 2200, enabled = status == ShizukuStatus.Ready, fps = 20)
            Box(Modifier.padding(start = 4.dp, end = 16.dp).size(12.dp), contentAlignment = Alignment.Center) {
                if (status == ShizukuStatus.Ready) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .graphicsLayer { val h = halo.value; val sc = 1f + (1f - h) * 1.3f; scaleX = sc; scaleY = sc; alpha = h * 0.6f }
                            .background(dotColor, CircleShape),
                    )
                }
                Box(Modifier.size(12.dp).background(dotColor, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun SchoolCard(
    school: SchoolState,
    canActivate: Boolean,
    busy: Boolean,
    onActivate: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // While activating: the card morphs rounder, the icon pulses and a color wave flows over it.
    val corner by animateDpAsState(if (busy) 56.dp else 32.dp, Motion.bouncy(), label = "schoolCorner")
    val iconPulse = pulse(min = 0.82f, periodMs = 900, enabled = busy)
    val wave = if (busy && !reduceMotion()) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            -0.5f, 1.5f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "wave",
        ).value
    } else null
    GlassCard(
        Modifier
            .fillMaxWidth()
            .sharedSection("section-${Section.School.name}")
            .drawWithContent {
                drawContent()
                if (wave != null) {
                    val x = size.width * wave
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, colors.primary.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(x - size.width * 0.35f, 0f),
                            end = Offset(x + size.width * 0.35f, size.height),
                        ),
                    )
                }
            },
        shape = RoundedCornerShape(corner),
        tint = colors.primaryContainer,
        onClick = onClick,
        contentPadding = 24.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.graphicsLayer { scaleX = iconPulse; scaleY = iconPulse }) {
                IconBubble(Section.School.icon, colors.primary, colors.onPrimary, 56)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Section.School.title, style = MaterialTheme.typography.headlineSmall, color = colors.onPrimaryContainer)
                Text(
                    when {
                        school.pendingRestore -> t(R.string.school_restore_pending)
                        school.autoEndMinutes > 0 -> t(R.string.school_off_ends, autoEndLabel(school.autoEndMinutes))
                        else -> t(R.string.off)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            Section.School.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(shapes = ButtonDefaults.shapes(), 
            onClick = onActivate,
            enabled = canActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = busy,
                transitionSpec = { (slideInVertically(Motion.offset) { it } + fadeIn()) togetherWith (slideOutVertically(Motion.offset) { -it } + fadeOut()) },
                label = "activateText",
            ) { b -> Text(if (b) t(R.string.activating) else t(R.string.activate), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun SectionTile(section: Section, modifier: Modifier, subtitle: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(modifier.sharedSection("section-${section.name}").height(148.dp), onClick = onClick) {
        IconBubble(section.icon, colors.secondaryContainer, colors.onSecondaryContainer, 44)
        Spacer(Modifier.weight(1f))
        Text(section.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle ?: section.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (subtitle != null) colors.primary else colors.onSurfaceVariant,
            // Always two lines, so tile titles side by side sit at the same height.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionRow(section: Section, subtitle: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth().sharedSection("section-${section.name}"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(section.icon, colors.tertiaryContainer, colors.onTertiaryContainer, 44)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle ?: section.subtitle, style = MaterialTheme.typography.bodySmall, color = if (subtitle != null) colors.primary else colors.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun IconBubble(icon: ImageVector, container: Color, content: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}
