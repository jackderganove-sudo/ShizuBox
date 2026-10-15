package de.emil.shizubox.tiles

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
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

class GamingTile : ShellTile() {
    override suspend fun read() = QuickActions.read(QuickAction.Gaming)
    override suspend fun toggle(current: TileInfo) = QuickActions.toggle(QuickAction.Gaming, current)
}

/** Animations off ↔ the last speed that was used. */
class AnimationsTile : ShellTile() {
    override suspend fun read() = QuickActions.read(QuickAction.Animations)
    override suspend fun toggle(current: TileInfo) = QuickActions.toggle(QuickAction.Animations, current)
}

/** Cycles adaptive → 60 Hz → always max. */
class RefreshTile : ShellTile() {
    override suspend fun read() = QuickActions.read(QuickAction.Refresh)
    override suspend fun toggle(current: TileInfo) = QuickActions.toggle(QuickAction.Refresh, current)
}

/** Caffeine: the screen stays on (timeout "never") until switched off again. */
class StayAwakeTile : ShellTile() {
    override suspend fun read() = QuickActions.read(QuickAction.StayAwake)
    override suspend fun toggle(current: TileInfo) = QuickActions.toggle(QuickAction.StayAwake, current)
}

/** Volume boost on/off. Works without Shizuku (the effect is a normal app API). */
class BoostTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val on = !de.emil.shizubox.system.VolumeBoost.settings.value.enabled
        de.emil.shizubox.system.VolumeBoost.update { it.copy(enabled = on) }
        de.emil.shizubox.system.BoostService.sync(this)
        refresh()
    }

    private fun refresh() {
        val s = de.emil.shizubox.system.VolumeBoost.settings.value
        qsTile?.apply {
            state = if (s.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (s.enabled) "+${de.emil.shizubox.system.VolumeBoost.currentPercent(s)} %" else t(R.string.off)
            updateTile()
        }
    }
}
