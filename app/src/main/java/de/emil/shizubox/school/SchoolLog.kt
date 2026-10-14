package de.emil.shizubox.school

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent log of school-mode events in device-protected storage, so the history
 * survives reboots (logcat is too small and gets flooded right after boot).
 * Read it with: adb shell run-as de.emil.shizubox cat /data/user_de/0/de.emil.shizubox/files/school.log
 */
object SchoolLog {
    private const val MAX_BYTES = 64 * 1024
    private var file: File? = null
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun init(context: Context) {
        file = File(context.createDeviceProtectedStorageContext().filesDir, "school.log")
    }

    @Synchronized
    fun log(tag: String, message: String, error: Throwable? = null) {
        if (error != null) Log.e(tag, message, error) else Log.i(tag, message)
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            f.appendText("${format.format(Date())} $tag: $message${error?.let { " ($it)" } ?: ""}\n")
        } catch (_: Exception) {
        }
    }
}
