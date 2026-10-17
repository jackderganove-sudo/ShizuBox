package de.emil.shizubox.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * For screens with private history (clipboard, notifications), Daniel's review 0.11.1:
 * no screenshots and no preview in Recents (FLAG_SECURE while the screen is shown), and with
 * [lock] the screen locks again as soon as ShizuBox leaves the screen (Home, other app, display
 * off) – before, `remember` kept it unlocked until the activity was destroyed.
 * Returns the "unlocked" state.
 */
@Composable
fun privateScreen(lock: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val unlocked = remember { mutableStateOf(!lock) }
    // The device-credential screen also stops the activity; its success callback arrives afterwards.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (lock) unlocked.value = false }
    return unlocked
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
