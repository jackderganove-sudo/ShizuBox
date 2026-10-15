package de.emil.shizubox.ui.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Sub pages of the System section. */
object SystemPage {
    const val LOGCAT = "logcat"
    const val DEVICE = "device"
    const val PROCESSES = "processes"
    const val TERMINAL = "terminal"
    const val SCREEN_TIME = "screentime"
}

private data class TermEntry(val cmd: String, val code: Int?, val output: String)

/** Commands and snippets, kept in device-protected storage. */
private object TerminalStore {
    private val DEFAULT_SNIPPETS = listOf(
        "id",
        "getprop ro.build.version.release",
        "dumpsys battery",
        "pm list packages -3",
        "df -h /data",
        "top -b -n 1 -m 15",
        "settings list global",
    )

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences("terminal", Context.MODE_PRIVATE)

    private fun list(context: Context, key: String, default: List<String>): List<String> {
        val raw = prefs(context).getString(key, null) ?: return default
        return runCatching { JSONArray(raw).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(default)
    }

    private fun save(context: Context, key: String, list: List<String>) =
        prefs(context).edit().putString(key, JSONArray(list).toString()).apply()

    fun snippets(context: Context) = list(context, "snippets", DEFAULT_SNIPPETS)
    fun saveSnippets(context: Context, l: List<String>) = save(context, "snippets", l)
    fun history(context: Context) = list(context, "history", emptyList())
    fun saveHistory(context: Context, l: List<String>) = save(context, "history", l.takeLast(50))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cmd by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<TermEntry>() }
    val snippets = remember { mutableStateListOf<String>().apply { addAll(TerminalStore.snippets(context)) } }
    val history = remember { mutableStateListOf<String>().apply { addAll(TerminalStore.history(context)) } }
    var showHistory by remember { mutableStateOf(false) }
    var deleteSnippet by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex) }

    fun run() {
        val c = cmd.trim()
        if (c.isEmpty() || running) return
        running = true
        history.remove(c)
        history.add(c)
        TerminalStore.saveHistory(context, history)
        cmd = ""
        scope.launch {
            val e = try {
                val r = ShizukuManager.exec(c, timeoutMs = 60_000)
                TermEntry(c, r.code, listOf(r.out, r.err).filter { it.isNotEmpty() }.joinToString("\n"))
            } catch (e: Exception) {
                TermEntry(c, null, e.message ?: t(R.string.shizuku_not_conn))
            }
            entries.add(e)
            running = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) }
            Text(t(R.string.term_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { showHistory = true }, enabled = history.isNotEmpty()) { Icon(Icons.Rounded.History, t(R.string.term_history)) }
            IconButton(onClick = {
                val text = entries.joinToString("\n\n") { "$ ${it.cmd}\n${it.output}" }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("terminal", text))
            }, enabled = entries.isNotEmpty()) { Icon(Icons.Rounded.ContentCopy, t(R.string.copy)) }
            IconButton(onClick = { entries.clear() }, enabled = entries.isNotEmpty()) { Icon(Icons.Rounded.DeleteSweep, t(R.string.term_clear)) }
        }
        if (!shizukuReady) {
            GlassCard(Modifier.fillMaxWidth()) { Text(t(R.string.shizuku_must)) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        t(R.string.term_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(entries) { e ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row {
                        Text(
                            "$ ${e.cmd}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (e.code != null && e.code != 0) {
                            Text("exit ${e.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (e.output.isNotEmpty()) {
                        SelectionContainer {
                            Text(
                                e.output.take(MAX_OUTPUT) + if (e.output.length > MAX_OUTPUT) "\n…" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            if (running) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snippets) { s ->
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .combinedClickable(onClick = { cmd = s }, onLongClick = { deleteSnippet = s }),
                ) {
                    Text(
                        s,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                singleLine = true,
                placeholder = { Text(t(R.string.term_placeholder)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { run() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val c = cmd.trim()
                if (c.isNotEmpty() && c !in snippets) {
                    snippets.add(0, c)
                    TerminalStore.saveSnippets(context, snippets)
                }
            }, enabled = cmd.isNotBlank()) { Icon(Icons.Rounded.BookmarkAdd, t(R.string.term_save_snippet)) }
            Spacer(Modifier.width(4.dp))
            FilledIconButton(onClick = ::run, enabled = shizukuReady && !running) { Icon(Icons.AutoMirrored.Rounded.Send, t(R.string.run)) }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(t(R.string.term_history)) },
            text = {
                LazyColumn {
                    items(history.reversed()) { h ->
                        AssistChip(onClick = { cmd = h; showHistory = false }, label = { Text(h, fontFamily = FontFamily.Monospace) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text(t(R.string.close)) } },
            dismissButton = {
                TextButton(onClick = { history.clear(); TerminalStore.saveHistory(context, history); showHistory = false }) { Text(t(R.string.term_clear)) }
            },
        )
    }
    deleteSnippet?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteSnippet = null },
            title = { Text(t(R.string.term_delete_snippet)) },
            text = { Text(s, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = {
                    snippets.remove(s)
                    TerminalStore.saveSnippets(context, snippets)
                    deleteSnippet = null
                }) { Text(t(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteSnippet = null }) { Text(t(R.string.cancel)) } },
        )
    }
}

private const val MAX_OUTPUT = 20_000
