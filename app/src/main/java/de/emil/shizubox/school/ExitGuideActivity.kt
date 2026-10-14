package de.emil.shizubox.school

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.ui.glass.GlassBackground
import de.emil.shizubox.ui.glass.GlassCard
import de.emil.shizubox.ui.theme.ShizuBoxTheme
import kotlinx.coroutines.delay

/**
 * Shown when the school mode was ended while Shizuku is not running (typically after a reboot).
 * Restoring happens automatically in [SchoolMode] once Shizuku is back.
 */
class ExitGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShizuBoxTheme {
                GlassBackground(windowBlurActive = false) {
                    val school by SchoolMode.state.collectAsStateWithLifecycle()
                    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()
                    val done = !school.pendingRestore && !school.active
                    LaunchedEffect(done) {
                        if (done) {
                            delay(1500)
                            finish()
                        }
                    }
                    ExitGuide(done, shizuku, onOpenShizuku = {
                        ShizukuManager.shizukuLaunchIntent()?.let(::startActivity)
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }
}

@Composable
private fun ExitGuide(done: Boolean, shizuku: ShizukuStatus, onOpenShizuku: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        Text(t(R.string.exit_done_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 8.dp))
        Text(
            if (done) t(R.string.exit_restored)
            else t(R.string.exit_not_restored),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        )
        if (done) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(t(R.string.done), style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Column
        }
        Step(1, t(R.string.exit_step1), t(R.string.exit_step1_sub))
        Step(2, t(R.string.exit_step2), t(R.string.exit_step2_sub))
        Step(3, t(R.string.exit_step3), t(R.string.exit_step3_sub))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shizuku == ShizukuStatus.Ready || shizuku == ShizukuStatus.Connecting) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp))
                    Text(t(R.string.restoring), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(t(R.string.waiting_shizuku), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = onOpenShizuku) { Text(t(R.string.open_shizuku)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Step(number: Int, title: String, text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("$number. $title", style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
