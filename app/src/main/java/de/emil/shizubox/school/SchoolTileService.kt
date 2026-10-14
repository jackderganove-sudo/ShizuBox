package de.emil.shizubox.school

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.emil.shizubox.MainActivity
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick settings tile: one tap turns the school mode on. */
class SchoolTileService : TileService() {

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        listening = scope.launch {
            SchoolMode.state.collect { updateTile(it.active) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
    }

    override fun onClick() {
        if (SchoolMode.state.value.active) return
        if (!ShizukuManager.isReady) {
            // Open the app so the user sees what is missing.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return
        }
        qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() }
        SchoolMode.scope.launch { SchoolMode.activate() }
    }

    private fun updateTile(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (active) "An" else "Aus"
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
