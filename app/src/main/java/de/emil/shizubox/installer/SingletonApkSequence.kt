/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: no Koin/Room/telemetry, German UI texts, Shizuku-only backend.
 */
package de.emil.shizubox.installer

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import androidx.core.content.FileProvider
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.CloseableSequence
import java.io.File
import kotlin.concurrent.Volatile

/** A split "package" consisting of a single plain APK. */
class SingletonApkSequence(private val uri: Uri, context: Context) : CloseableSequence<Apk> {

    @Volatile
    override var isClosed: Boolean = false
        private set

    private val appContext = context.applicationContext
    private val cancellationSignal = CancellationSignal()

    override fun iterator(): Iterator<Apk> = object : Iterator<Apk> {
        // Ackpine rejects files whose real path does not end in ".apk" (e.g. some FileProvider
        // shares). Fall back to a ".apk"-named cache copy in that case.
        private val apk = Apk.fromUri(uri, appContext, cancellationSignal) ?: parseViaCachedCopy()
        private var yielded = false

        override fun hasNext() = apk != null && !yielded

        override fun next(): Apk {
            if (!hasNext()) throw NoSuchElementException()
            yielded = true
            return apk!!
        }
    }

    private fun parseViaCachedCopy(): Apk? {
        val cached = try {
            val dir = File(appContext.cacheDir, "apk_parse_fallback").apply { mkdirs() }
            val file = File(dir, "src_${System.currentTimeMillis()}.apk")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return null
            file
        } catch (_: Throwable) {
            return null
        }
        return try {
            val cachedUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", cached)
            Apk.fromUri(cachedUri, appContext, cancellationSignal)
        } catch (_: Throwable) {
            null
        }
    }

    override fun close() {
        isClosed = true
        cancellationSignal.cancel()
    }
}
