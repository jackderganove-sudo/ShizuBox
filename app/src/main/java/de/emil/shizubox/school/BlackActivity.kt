package de.emil.shizubox.school

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import java.lang.ref.WeakReference

/**
 * Pitch black, full-screen, shown above the lock screen. Hides the system bars and swallows
 * every touch and the back gesture. Brightness is forced to the minimum.
 */
class BlackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        setShowWhenLocked(true)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.setDecorFitsSystemWindows(false)
        setContentView(
            View(this).apply {
                setBackgroundColor(Color.BLACK)
                setOnTouchListener { _, _ -> true }
            },
        )
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY) { }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private var current: WeakReference<BlackActivity>? = null

        fun show(context: Context) {
            context.startActivity(
                Intent(context, BlackActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
            )
        }

        fun dismiss() {
            current?.get()?.finishAndRemoveTask()
            current = null
        }
    }
}
