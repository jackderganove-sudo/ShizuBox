package de.emil.shizubox.audio

import android.content.Context
import android.content.SharedPreferences
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

data class Player(val uid: Int, val playing: Boolean, val usage: Int)

/**
 * Volume per app – real mixing, not a workaround: the shell user (Shizuku) holds
 * MODIFY_AUDIO_ROUTING, sees every player with its app and sets the player's volume
 * (`AudioPlaybackConfiguration.getPlayerProxy().setVolume`). Tested on Android 17: InterTune at
 * 50 % → its track went from −2.8 dB to −8.8 dB. New players start at full volume, so the Shizuku
 * service re-applies the values on every playback change; after a Shizuku restart ShizuBox sends
 * them again. Multiplies with the media volume and the volume boost.
 */
object AppVolume {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _levels = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Package → percent (0–100); apps without an entry play at 100 %. */
    val levels: StateFlow<Map<String, Int>> = _levels.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("app_volume", Context.MODE_PRIVATE)
        load()
        scope.launch { ShizukuManager.status.filter { it == ShizukuStatus.Ready }.collect { applyAll() } }
    }

    fun load() {
        _levels.value = prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    }

    private fun uid(pkg: String) = runCatching { appContext.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()

    suspend fun set(pkg: String, percent: Int) {
        val p = percent.coerceIn(0, 100)
        if (p >= 100) prefs.edit().remove(pkg).apply() else prefs.edit().putInt(pkg, p).apply()
        load()
        uid(pkg)?.let { ShizukuManager.setAppVolume(it, p / 100f) }
    }

    fun get(pkg: String) = _levels.value[pkg] ?: 100

    private suspend fun applyAll() {
        for ((pkg, p) in _levels.value) uid(pkg)?.let { ShizukuManager.setAppVolume(it, p / 100f) }
    }

    /** Players right now (all users' apps of this user only). */
    suspend fun players(): List<Player> = ShizukuManager.listPlayers().lines().mapNotNull { l ->
        val f = l.split('|')
        if (f.size < 4) null else Player(f[0].toIntOrNull() ?: return@mapNotNull null, f[2] == "2", f[3].toIntOrNull() ?: 0)
    }

    fun packageOf(uid: Int): String? = runCatching { appContext.packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
}
