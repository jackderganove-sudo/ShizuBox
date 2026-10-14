package de.emil.shizubox.ui.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.emil.shizubox.system.LogLine
import de.emil.shizubox.system.Logcat
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LogcatScreen(shizukuReady: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var level by rememberSaveable { mutableStateOf('I') }
    var pkg by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var live by rememberSaveable { mutableStateOf(true) }
    var lines by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(level, pkg, live, refreshKey, shizukuReady) {
        if (!shizukuReady) return@LaunchedEffect
        do {
            lines = runCatching { Logcat.read(level, pkg.ifBlank { null }) }.getOrDefault(emptyList())
            if (live) delay(2000)
        } while (live)
    }
    val shown = remember(lines, search) {
        if (search.isBlank()) lines else lines.filter { it.text.contains(search, true) }
    }
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            Text("Logcat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("logcat", shown.joinToString("\n") { it.text }))
                Toast.makeText(context, "${shown.size} Zeilen kopiert", Toast.LENGTH_SHORT).show()
            }) { Text("Kopieren") }
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Rounded.Refresh, "Aktualisieren") }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            items(shown) { line ->
                Text(
                    line.text,
                    color = levelColor(line.level),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 1.dp),
                )
            }
        }
        // Controls at the bottom: reachable with the thumb.
        GlassCard(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = 12.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Logcat.LEVELS.forEach { l ->
                    FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l.toString()) })
                }
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = live, onClick = { live = !live }, label = { Text("Live") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim() },
                    placeholder = { Text("Paket") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Suchen") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun levelColor(level: Char): Color = when (level) {
    'E', 'F' -> MaterialTheme.colorScheme.error
    'W' -> Color(0xFFE0A030)
    'D', 'V' -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}
