package de.emil.shizubox.debug

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.ui.glass.GlassBackground
import de.emil.shizubox.ui.theme.ShizuBoxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Renders a screen on a private virtual display (invisible, not affected by the lock screen)
 * and stores it as PNG in /data/local/tmp – lets the developer check layouts over adb while
 * the phone stays locked. Only reachable through the DUMP-protected [de.emil.shizubox.updates.DebugReceiver].
 */
object ScreenRenderer {
    private const val TAG = "ShizuBoxShot"

    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
        }

        init {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    fun render(context: Context, name: String, heightPx: Int, delayMs: Long, onDone: () -> Unit, content: @Composable () -> Unit) {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val reader = ImageReader.newInstance(width, heightPx, PixelFormat.RGBA_8888, 2)
        val dm = context.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "shizubox-shot", width, heightPx, metrics.densityDpi, reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        val owner = Owner()
        val presentation = Presentation(context, vd.display)
        val view = ComposeView(presentation.context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    ShizuBoxTheme { GlassBackground(windowBlurActive = false) { content() } }
                }
            }
        }
        presentation.setContentView(view)
        presentation.show()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val image = reader.acquireLatestImage()
                if (image == null) {
                    Log.e(TAG, "$name: no frame")
                } else {
                    val plane = image.planes[0]
                    val rowPixels = plane.rowStride / plane.pixelStride
                    val bmp = Bitmap.createBitmap(rowPixels, heightPx, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(plane.buffer)
                    image.close()
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, width, heightPx)
                    // PNG encoding and writing off the main thread (StrictMode found it there).
                    CoroutineScope(Dispatchers.IO).launch {
                        val file = File(context.cacheDir, "shot_$name.png")
                        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        val n = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                            ShizukuManager.writeFile(it, "/data/local/tmp/shot_$name.png")
                        }
                        Log.i(TAG, "$name: saved ($n bytes)")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "$name failed", e)
            } finally {
                presentation.dismiss()
                owner.destroy()
                vd.release()
                reader.close()
                onDone()
            }
        }, delayMs)
    }
}
