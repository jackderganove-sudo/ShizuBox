package de.emil.shizubox.ui.downloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Minimal thumbnail loader: memory cache, downsampled decoding, fade-in. */
private object Thumbs {
    val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(url: String, maxWidth: Int): Bitmap? {
        cache.get(url)?.let { return it }
        val bytes = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 15_000
                readTimeout = 20_000
                inputStream.use { it.readBytes() }
            }
        }.getOrNull() ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        bmp?.let { cache.put(url, it) }
        return bmp
    }
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, maxWidth: Int = 720) {
    val bmp by produceState<Bitmap?>(initialValue = url?.let { Thumbs.cache.get(it) }, url) {
        if (value == null && url != null) value = withContext(Dispatchers.IO) { Thumbs.load(url, maxWidth) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        AnimatedVisibility(bmp != null, enter = fadeIn()) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
}
