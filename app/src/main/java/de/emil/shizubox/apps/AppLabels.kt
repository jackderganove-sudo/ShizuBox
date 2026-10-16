package de.emil.shizubox.apps

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import de.emil.shizubox.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App names for lists. `loadLabel` reads the APK's resources (disk) – in a long list on the UI
 * thread that stutters, so names are loaded in the background and cached.
 */
object AppLabels {
    private val cache = LruCache<String, String>(800)

    fun cached(pkg: String): String? = cache.get(pkg)

    suspend fun load(pkg: String): String = cache.get(pkg) ?: withContext(Dispatchers.IO) {
        val pm = Str.context.packageManager
        (runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrNull() ?: pkg).also { cache.put(pkg, it) }
    }
}

/** The app's name; shows the package name for the first frame if it isn't cached yet. */
@Composable
fun rememberAppLabel(pkg: String): String {
    val label by produceState(AppLabels.cached(pkg) ?: pkg, pkg) { value = AppLabels.load(pkg) }
    return label
}
