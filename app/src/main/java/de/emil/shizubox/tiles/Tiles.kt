package de.emil.shizubox.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.system.Tweaks
import de.emil.shizubox.t
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What a tile shows: on/off plus a short subtitle. */
data class TileInfo(val active: Boolean, val subtitle: String)

/**
 * Base for quick settings tiles that run shell commands through Shizuku.
 * Without Shizuku a tap opens the app instead.
 */
abstract class ShellTile : TileService() {
    private val scope = MainScope()

    abstract suspend fun read(): TileInfo
    abstract suspend fun toggle(current: TileInfo)

    override fun onStartListening() {
        scope.launch { refresh() }
    }

    override fun onClick() {
        if (!ShizukuManager.isReady) {
            openApp()
            return
        }
        scope.launch {
            runCatching { toggle(read()) }
            refresh()
        }
    }

    private suspend fun refresh() {
        // Right after the process starts, the shell service needs a moment to bind.
        val ready = withTimeoutOrNull(2500) { ShizukuManager.status.first { it == ShizukuStatus.Ready } } != null
        val info = if (ready) runCatching { read() }.getOrNull() else null
        qsTile?.apply {
            if (info == null) {
                state = Tile.STATE_INACTIVE
                subtitle = t(R.string.shizuku)
            } else {
                state = if (info.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                subtitle = info.subtitle
            }
            updateTile()
        }
    }

    private fun openApp() {
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private fun onOff(active: Boolean) = TileInfo(active, if (active) t(R.string.on) else t(R.string.off))

class GamingTile : ShellTile() {
    override suspend fun read() = onOff(Gaming.active.value)
    override suspend fun toggle(current: TileInfo) = if (current.active) Gaming.disable() else Gaming.enable()
}

/** Animations off ↔ the last speed that was used. */
class AnimationsTile : ShellTile() {
    private val prefs get() = createDeviceProtectedStorageContext().getSharedPreferences("tiles", Context.MODE_PRIVATE)

    override suspend fun read(): TileInfo {
        val scale = Customization.animationScale()
        return TileInfo(scale > 0f, if (scale > 0f) "${scale.toString().removeSuffix(".0")}×" else t(R.string.off))
    }

    override suspend fun toggle(current: TileInfo) {
        if (current.active) {
            prefs.edit().putFloat("last_anim", Customization.animationScale()).apply()
            Customization.setAnimationScale(0f)
        } else {
            Customization.setAnimationScale(prefs.getFloat("last_anim", 1f).takeIf { it > 0f } ?: 1f)
        }
    }
}

/** Cycles adaptive → 60 Hz → always max. */
class RefreshTile : ShellTile() {
    override suspend fun read(): TileInfo {
        val mode = Customization.refreshMode()
        return TileInfo(mode != RefreshMode.Adaptive, mode.label)
    }

    override suspend fun toggle(current: TileInfo) {
        val entries = RefreshMode.entries
        Customization.setRefresh(entries[(entries.indexOf(Customization.refreshMode()) + 1) % entries.size])
    }
}

/** Screen stays on while charging (developer option). */
class StayAwakeTile : ShellTile() {
    override suspend fun read() = onOff(Tweaks.stayAwake())
    override suspend fun toggle(current: TileInfo) { Tweaks.setStayAwake(!current.active) }
}
