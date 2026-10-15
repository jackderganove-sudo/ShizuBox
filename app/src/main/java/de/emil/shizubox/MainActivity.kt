package de.emil.shizubox

import android.content.Intent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import de.emil.shizubox.ui.motion.LocalNavScope
import de.emil.shizubox.ui.motion.LocalSharedScope
import de.emil.shizubox.ui.motion.Motion
import de.emil.shizubox.ui.motion.openClose
import de.emil.shizubox.ui.motion.sharedSection
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import de.emil.shizubox.settings.AppSettings
import de.emil.shizubox.ui.theme.LocalDarkTheme
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
import de.emil.shizubox.ui.system.DeviceInfoScreen
import de.emil.shizubox.ui.system.ProcessesScreen
import de.emil.shizubox.ui.system.SystemPage
import de.emil.shizubox.ui.system.TerminalScreen
import de.emil.shizubox.ui.settings.SettingsScreen
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
import de.emil.shizubox.ui.theme.ShizuBoxTheme

@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {

    private val windowBlur = mutableStateOf(false)
    private val blurListener = java.util.function.Consumer<Boolean> { enabled ->
        windowBlur.value = enabled
        applyWindowBlur()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Blur radius follows the glass settings live.
        lifecycleScope.launch { AppSettings.state.collect { applyWindowBlur() } }

        setContent {
            ShizuBoxTheme {
                // Status bar icons must follow the app's own light/dark choice, not only the system's.
                val dark = LocalDarkTheme.current
                LaunchedEffect(dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    )
                }
                val status by ShizukuManager.status.collectAsStateWithLifecycle()
                val school by SchoolMode.state.collectAsStateWithLifecycle()
                var section by rememberSaveable {
                    val name = intent.getStringExtra(EXTRA_SECTION) ?: AppSettings.current.startSection
                    mutableStateOf(Section.entries.firstOrNull { it.name == name })
                }
                val trackedApps by Updates.apps.collectAsStateWithLifecycle()
                val updateCount = remember(trackedApps) { Updates.updateCount() }
                var systemPage by rememberSaveable { mutableStateOf<String?>(null) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var installUri by remember { mutableStateOf<android.net.Uri?>(null) }
                var busy by remember { mutableStateOf(false) }
                val activate: () -> Unit = {
                    busy = true
                    lifecycleScope.launch {
                        SchoolMode.activate().onFailure {
                            Toast.makeText(this@MainActivity, it.message ?: t(R.string.error), Toast.LENGTH_LONG).show()
                        }
                        busy = false
                    }
                }
                BackHandler(enabled = settingsOpen) { settingsOpen = false }
                BackHandler(enabled = section != null && systemPage == null && !settingsOpen) { section = null }
                BackHandler(enabled = systemPage != null) { systemPage = null }
                BackHandler(enabled = installUri != null) { installUri = null }

                GlassBackground(windowBlurActive = windowBlur.value) {
                    SharedTransitionLayout {
                        CompositionLocalProvider(LocalSharedScope provides this) {
                            AnimatedContent(
                                targetState = if (settingsOpen) SETTINGS else section,
                                transitionSpec = {
                                    if (targetState == SETTINGS || initialState == SETTINGS) openClose(opening = targetState == SETTINGS)
                                    // Sections grow out of their home card (shared bounds), so the rest only fades.
                                    else fadeIn(Motion.quick()) togetherWith fadeOut(tween(150))
                                },
                                label = "screen",
                            ) { current ->
                                CompositionLocalProvider(LocalNavScope provides this) {
                                    Screen(current, status, school, busy, updateCount, activate, systemPage, installUri,
                                        onSection = { section = it }, onSettings = { settingsOpen = it },
                                        onSystemPage = { systemPage = it }, onInstallUri = { installUri = it })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Screen(
        current: Any?,
        status: ShizukuStatus,
        school: de.emil.shizubox.school.SchoolState,
        busy: Boolean,
        updateCount: Int,
        activate: () -> Unit,
        systemPage: String?,
        installUri: Uri?,
        onSection: (Section?) -> Unit,
        onSettings: (Boolean) -> Unit,
        onSystemPage: (String?) -> Unit,
        onInstallUri: (Uri?) -> Unit,
    ) {
        val ready = status == ShizukuStatus.Ready
        val back = { onSection(null) }
        when (current) {
            SETTINGS -> SettingsScreen(
                onBack = { onSettings(false) },
                onOpenUpdates = { onSettings(false); onSection(Section.Updates) },
                onOpenSchool = { onSettings(false); onSection(Section.School) },
            )
            null -> HomeScreen(
                shizukuStatus = status,
                serverMode = ShizukuManager.serverMode,
                school = school,
                busy = busy,
                updateCount = updateCount,
                onShizukuAction = { onShizukuAction(status) },
                onActivateSchool = activate,
                onOpenSettings = { onSettings(true) },
                onOpenSection = onSection,
            )
            is Section -> Box(Modifier.fillMaxSize().sharedSection("section-${current.name}")) {
                when (current) {
                    Section.School -> SchoolScreen(
                        state = school,
                        shizukuReady = ready,
                        busy = busy,
                        onActivate = activate,
                        onAutoEndChange = { lifecycleScope.launch { SchoolMode.setAutoEndMinutes(it) } },
                        onWaitForShizukuChange = { lifecycleScope.launch { SchoolMode.setWaitForShizukuAfterReboot(it) } },
                        onBack = back,
                    )
                    Section.Updates -> UpdatesScreen(ready, onBack = back)
                    Section.Apps -> AppsScreen(shizukuReady = ready, onBack = back)
                    Section.Customization -> CustomizationScreen(shizukuReady = ready, onBack = back)
                    Section.System -> AnimatedContent(
                        targetState = installUri ?: systemPage,
                        transitionSpec = { openClose(opening = targetState != null) },
                        label = "systemPage",
                    ) { sub ->
                        val close = { onSystemPage(null) }
                        when (sub) {
                            is Uri -> UniversalInstallerScreen(sub, ready, onClose = { onInstallUri(null) })
                            SystemPage.LOGCAT -> LogcatScreen(ready, onBack = close)
                            SystemPage.DEVICE -> DeviceInfoScreen(onBack = close)
                            SystemPage.PROCESSES -> ProcessesScreen(ready, onBack = close)
                            SystemPage.TERMINAL -> TerminalScreen(ready, onBack = close)
                            SystemPage.SCREEN_TIME -> de.emil.shizubox.ui.system.ScreenTimeScreen(ready, onBack = close)
                            else -> SystemScreen(
                                shizukuReady = ready,
                                onOpenPage = onSystemPage,
                                onOpenUniversalInstaller = onInstallUri,
                                onBack = back,
                            )
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

    private fun applyWindowBlur() {
        val s = AppSettings.current
        val on = windowBlur.value && s.glass
        window.setBackgroundBlurRadius(if (on) WINDOW_BLUR_RADIUS * s.blurStrength / 100 else 0)
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
        private const val SETTINGS = "settings"
        private var checkedThisProcess = false
    }
}
