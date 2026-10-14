package de.emil.shizubox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.ui.apps.AppsScreen
import de.emil.shizubox.ui.system.LogcatScreen
import de.emil.shizubox.ui.system.SystemScreen
import de.emil.shizubox.ui.system.UniversalInstallerScreen
import de.emil.shizubox.ui.updates.UpdatesScreen
import de.emil.shizubox.updates.Updates
import de.emil.shizubox.ui.customization.CustomizationScreen
import de.emil.shizubox.ui.school.SchoolScreen
import kotlinx.coroutines.launch
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.ui.glass.GlassBackground
import de.emil.shizubox.ui.home.HomeScreen
import de.emil.shizubox.ui.home.Section
import de.emil.shizubox.ui.home.SectionScreen
import de.emil.shizubox.ui.theme.ShizuBoxTheme

class MainActivity : ComponentActivity() {

    private val windowBlur = mutableStateOf(false)
    private val blurListener = java.util.function.Consumer<Boolean> { enabled ->
        windowBlur.value = enabled
        window.setBackgroundBlurRadius(if (enabled) WINDOW_BLUR_RADIUS else 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ShizuBoxTheme {
                val status by ShizukuManager.status.collectAsStateWithLifecycle()
                val school by SchoolMode.state.collectAsStateWithLifecycle()
                var section by rememberSaveable {
                    mutableStateOf(intent.getStringExtra(EXTRA_SECTION)?.let { name -> Section.entries.firstOrNull { it.name == name } })
                }
                val trackedApps by Updates.apps.collectAsStateWithLifecycle()
                val updateCount = remember(trackedApps) { Updates.updateCount() }
                var logcatOpen by rememberSaveable { mutableStateOf(false) }
                var installUri by remember { mutableStateOf<android.net.Uri?>(null) }
                var busy by remember { mutableStateOf(false) }
                val activate: () -> Unit = {
                    busy = true
                    lifecycleScope.launch {
                        SchoolMode.activate().onFailure {
                            Toast.makeText(this@MainActivity, it.message ?: "Fehler", Toast.LENGTH_LONG).show()
                        }
                        busy = false
                    }
                }
                BackHandler(enabled = section != null && !logcatOpen) { section = null }
                BackHandler(enabled = logcatOpen) { logcatOpen = false }
                BackHandler(enabled = installUri != null) { installUri = null }

                GlassBackground(windowBlurActive = windowBlur.value) {
                    AnimatedContent(
                        targetState = section,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                        label = "screen",
                    ) { current ->
                        if (current == null) {
                            HomeScreen(
                                shizukuStatus = status,
                                serverMode = ShizukuManager.serverMode,
                                school = school,
                                busy = busy,
                                updateCount = updateCount,
                                onShizukuAction = { onShizukuAction(status) },
                                onActivateSchool = activate,
                                onOpenSection = { section = it },
                            )
                        } else if (current == Section.School) {
                            SchoolScreen(
                                state = school,
                                shizukuReady = status == ShizukuStatus.Ready,
                                busy = busy,
                                onActivate = activate,
                                onAutoEndChange = { lifecycleScope.launch { SchoolMode.setAutoEndMinutes(it) } },
                                onWaitForShizukuChange = { lifecycleScope.launch { SchoolMode.setWaitForShizukuAfterReboot(it) } },
                                onBack = { section = null },
                            )
                        } else if (current == Section.Updates) {
                            UpdatesScreen(status == ShizukuStatus.Ready, onBack = { section = null })
                        } else if (current == Section.System) {
                            val uri = installUri
                            if (uri != null) {
                                UniversalInstallerScreen(uri, status == ShizukuStatus.Ready, onClose = { installUri = null })
                            } else if (logcatOpen) {
                                LogcatScreen(status == ShizukuStatus.Ready, onBack = { logcatOpen = false })
                            } else {
                                SystemScreen(
                                    shizukuReady = status == ShizukuStatus.Ready,
                                    onOpenLogcat = { logcatOpen = true },
                                    onOpenUniversalInstaller = { installUri = it },
                                    onBack = { section = null },
                                )
                            }
                        } else if (current == Section.Apps) {
                            AppsScreen(
                                shizukuReady = status == ShizukuStatus.Ready,
                                onBack = { section = null },
                            )
                        } else if (current == Section.Customization) {
                            CustomizationScreen(
                                shizukuReady = status == ShizukuStatus.Ready,
                                onBack = { section = null },
                            )
                        } else {
                            SectionScreen(current, onBack = { section = null })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!checkedThisProcess && Updates.settings.value.checkOnStart && Updates.apps.value.isNotEmpty()) {
            checkedThisProcess = true
            Updates.checkAllInBackground()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
    }

    override fun onDetachedFromWindow() {
        windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        super.onDetachedFromWindow()
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }

    private fun onShizukuAction(status: ShizukuStatus) {
        when (status) {
            ShizukuStatus.NoPermission -> ShizukuManager.requestPermission()
            ShizukuStatus.NotInstalled -> startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")),
            )
            else -> ShizukuManager.shizukuLaunchIntent()?.let(::startActivity)
        }
    }

    companion object {
        private const val WINDOW_BLUR_RADIUS = 90
        const val EXTRA_SECTION = "section"
        private var checkedThisProcess = false
    }
}
