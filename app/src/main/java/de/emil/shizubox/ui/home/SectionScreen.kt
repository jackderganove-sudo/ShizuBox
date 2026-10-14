package de.emil.shizubox.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuUnavailableException
import de.emil.shizubox.ui.glass.GlassCard
import kotlinx.coroutines.launch

/** Placeholder for sections that get filled in later phases. */
@Composable
fun SectionScreen(section: Section, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
            }
            Column(Modifier.padding(start = 8.dp, top = 48.dp, bottom = 20.dp)) {
                Text(section.title, style = MaterialTheme.typography.displaySmall)
                Text(section.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Kommt bald", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (section) {
                        Section.School -> "Wird in Phase 2 gebaut."
                        Section.Customization -> "Wird in Phase 3 gebaut."
                        else -> "Wird in Phase 4 gebaut."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (section == Section.System) item { ShellConsole() }
    }
}

/** Small test console: runs a command through Shizuku and shows the output. */
@Composable
private fun ShellConsole() {
    val scope = rememberCoroutineScope()
    var cmd by rememberSaveable { mutableStateOf("id") }
    var output by remember { mutableStateOf("") }
    val run = {
        scope.launch {
            output = try {
                val r = ShizukuManager.exec(cmd, timeoutMs = 10_000)
                buildString {
                    append("exit ${r.code}\n")
                    if (r.out.isNotEmpty()) append(r.out).append('\n')
                    if (r.err.isNotEmpty()) append(r.err)
                }.trimEnd()
            } catch (e: ShizukuUnavailableException) {
                e.message.orEmpty()
            }
        }
        Unit
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Shell-Test", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { run() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = run) { Icon(Icons.AutoMirrored.Rounded.Send, "Ausführen") }
        }
        if (output.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(output, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}
