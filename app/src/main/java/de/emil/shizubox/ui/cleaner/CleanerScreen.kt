@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.cleaner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.R
import de.emil.shizubox.cleaner.StorageAccess
import de.emil.shizubox.cleaner.Trash
import de.emil.shizubox.t
import de.emil.shizubox.tp
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.motion.RollingText
import de.emil.shizubox.ui.motion.animatedProgress
import de.emil.shizubox.ui.motion.openClose
import kotlinx.coroutines.flow.MutableStateFlow

/** Sub pages of the cleaner; set from outside (notification, debug) through [CleanerNav]. */
object CleanerPage {
    const val TRASH = "trash"
    const val ANALYSIS = "analysis"
    const val SYSTEM = "system"
    const val CORPSES = "corpses"
    const val APPCACHE = "appcache"
    const val DUPES = "dupes"
    const val SETTINGS = "settings"
}

object CleanerNav {
    val page = MutableStateFlow<String?>(null)
    /** First tab of a page with tabs (debug screenshots). */
    var tab = 0
}

@Composable
fun CleanerScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val page by CleanerNav.page.collectAsStateWithLifecycle()
    BackHandler(page != null) { CleanerNav.page.value = null }
    LaunchedEffect(shizukuReady) { if (shizukuReady) StorageAccess.ensure() }
    AnimatedContent(page, transitionSpec = { openClose(opening = targetState != null) }, label = "cleaner") { p ->
        val close = { CleanerNav.page.value = null }
        when (p) {
            CleanerPage.TRASH -> TrashScreen(shizukuReady, close)
            CleanerPage.ANALYSIS -> AnalysisScreen(shizukuReady, close)
            CleanerPage.SYSTEM -> SystemCleanerScreen(shizukuReady, close)
            CleanerPage.CORPSES -> CorpseScreen(shizukuReady, close)
            CleanerPage.APPCACHE -> AppCleanerScreen(shizukuReady, close)
            CleanerPage.DUPES -> DuplicatesScreen(shizukuReady, close)
            CleanerPage.SETTINGS -> CleanerSettingsScreen(close)
            else -> CleanerHome(shizukuReady, onBack)
        }
    }
}

@Composable
private fun CleanerHome(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val trash by Trash.items.collectAsStateWithLifecycle()
    val running by de.emil.shizubox.cleaner.CleanerJobs.running.collectAsStateWithLifecycle()
    // StatFs touches the disk: never on the main thread.
    val numbers by androidx.compose.runtime.produceState(de.emil.shizubox.cleaner.StorageNumbers(0, 0), trash.size, running.isEmpty()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { StorageAccess.numbers() }
    }
    fun open(p: String) { CleanerNav.page.value = p }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeader(t(R.string.sec_cleaner), onBack) {
                IconButton(onClick = { open(CleanerPage.SETTINGS) }) { Icon(Icons.Rounded.Settings, t(R.string.settings)) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        RollingText(size(context, numbers.used), MaterialTheme.typography.headlineMedium)
                        Text(t(R.string.cl_used), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        RollingText(size(context, numbers.free), MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(t(R.string.cl_free), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val progress = animatedProgress(if (numbers.total > 0) numbers.used.toFloat() / numbers.total else 0f)
                LinearWavyProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(t(R.string.cl_of_total, size(context, numbers.total)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (!shizukuReady) Text(t(R.string.shizuku_must), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            val sys by de.emil.shizubox.cleaner.SystemCleaner.result.collectAsStateWithLifecycle()
            val corpses by de.emil.shizubox.cleaner.CorpseFinder.result.collectAsStateWithLifecycle()
            val caches by de.emil.shizubox.cleaner.AppCleaner.result.collectAsStateWithLifecycle()
            val dupes by de.emil.shizubox.cleaner.Duplicates.result.collectAsStateWithLifecycle()
            val anyRunning = running.isNotEmpty()
            Button(
                shapes = ButtonDefaults.shapes(), enabled = shizukuReady && !anyRunning, onClick = { scanAll(context) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.padding(end = 8.dp))
                Text(t(if (anyRunning) R.string.cl_scanning else R.string.cl_scan_all), style = MaterialTheme.typography.titleMedium)
            }
            val found = listOfNotNull(
                sys?.values?.flatten()?.sumOf { it.size }, corpses?.values?.flatten()?.sumOf { it.size },
                caches?.filter { de.emil.shizubox.cleaner.AppCleaner.blockReason(it) == null }?.sumOf { it.ext }, dupes?.sumOf { it.waste },
            )
            if (found.isNotEmpty() && !anyRunning) Text(
                t(R.string.cl_found_total, size(context, found.sum())),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 8.dp),
            )
        }
        item {
            val analysis by de.emil.shizubox.cleaner.StorageAnalysis.result.collectAsStateWithLifecycle()
            ToolCard(Icons.Rounded.PieChart, t(R.string.cl_analysis), t(R.string.sa_sub), running = de.emil.shizubox.cleaner.CleanTool.Analysis in running,
                found = analysis?.let { tp(R.plurals.sa_files, it.files, it.files) }) { open(CleanerPage.ANALYSIS) }
        }
        item {
            val sys by de.emil.shizubox.cleaner.SystemCleaner.result.collectAsStateWithLifecycle()
            ToolCard(Icons.Rounded.CleaningServices, t(R.string.cl_system), t(R.string.sc_sub), running = de.emil.shizubox.cleaner.CleanTool.System in running,
                found = sys?.values?.flatten()?.sumOf { it.size }?.let { size(context, it) }) { open(CleanerPage.SYSTEM) }
        }
        item {
            val corpses by de.emil.shizubox.cleaner.CorpseFinder.result.collectAsStateWithLifecycle()
            ToolCard(Icons.Rounded.FolderDelete, t(R.string.cl_corpses), t(R.string.cf_sub), running = de.emil.shizubox.cleaner.CleanTool.Corpses in running,
                found = corpses?.values?.flatten()?.let { l -> if (l.isEmpty()) "0" else size(context, l.sumOf { it.size }) }) { open(CleanerPage.CORPSES) }
        }
        item {
            val caches by de.emil.shizubox.cleaner.AppCleaner.result.collectAsStateWithLifecycle()
            ToolCard(Icons.Rounded.Apps, t(R.string.cl_appcache), t(R.string.cc_sub), running = de.emil.shizubox.cleaner.CleanTool.AppCache in running,
                found = caches?.let { l -> size(context, l.sumOf { it.total }) }) { open(CleanerPage.APPCACHE) }
        }
        item {
            val dupes by de.emil.shizubox.cleaner.Duplicates.result.collectAsStateWithLifecycle()
            ToolCard(Icons.Rounded.ContentCopy, t(R.string.cl_dupes), t(R.string.du_sub), running = de.emil.shizubox.cleaner.CleanTool.Duplicates in running,
                found = dupes?.let { l -> size(context, l.sumOf { it.waste }) }) { open(CleanerPage.DUPES) }
        }
        item {
            ToolCard(Icons.Rounded.Delete, t(R.string.cl_trash),
                if (trash.isEmpty()) t(R.string.cl_trash_sub) else tp(R.plurals.cl_trash_n, trash.size, trash.size) + " · " + size(context, trash.sumOf { it.size })) {
                open(CleanerPage.TRASH)
            }
        }
        item { Hint(t(R.string.cl_private_hint), Modifier.padding(top = 8.dp)) }
        item { Spacer(Modifier.padding(16.dp)) }
    }
}

/** "Scan everything": all tools one after the other (the job queue runs one at a time). */
fun scanAll(context: android.content.Context) {
    scanCorpses()
    scanSystem(context)
    scanAppCaches(context)
    scanDuplicates(de.emil.shizubox.cleaner.KeepRule.Camera)
    scanAnalysis(context)
}

@Composable
fun ToolCard(icon: ImageVector, title: String, subtitle: String, found: String? = null, running: Boolean = false, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 16.dp) {
        Row {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp, top = 2.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (running) LoadingIndicator(Modifier.size(32.dp))
            else if (found != null) RollingText(found, MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
