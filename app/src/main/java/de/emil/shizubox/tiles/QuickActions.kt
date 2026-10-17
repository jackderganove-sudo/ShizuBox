package de.emil.shizubox.tiles

import de.emil.shizubox.R
import de.emil.shizubox.Str
import de.emil.shizubox.cleaner.CleanTool
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.system.BoostService
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.system.VolumeBoost
import de.emil.shizubox.system.Tweaks
import de.emil.shizubox.t

enum class QuickAction(private val labelRes: Int, private val shortRes: Int) {
    Gaming(R.string.gaming, R.string.qa_gaming),
    Animations(R.string.tile_animations, R.string.tile_animations),
    Refresh(R.string.cust_refresh, R.string.qa_refresh),
    StayAwake(R.string.tile_stay_awake, R.string.qa_awake),
    Boost(R.string.vb_title, R.string.vb_tile),
    Cache(R.string.cc_title, R.string.cc_short),
    Clean(R.string.qa_clean, R.string.qa_clean_short),
    ;
    val label get() = t(labelRes)
    /** For the narrow buttons on the home screen. */
    val shortLabel get() = t(shortRes)
}

/** One-tap switches shared by the quick settings tiles and the home screen. */
object QuickActions {
    // Opened once (it used to be looked up on every read – a disk access on the main thread).
    private val prefs by lazy { Str.context.createDeviceProtectedStorageContext().getSharedPreferences("tiles", android.content.Context.MODE_PRIVATE) }

    private fun onOff(active: Boolean) = TileInfo(active, if (active) t(R.string.on) else t(R.string.off))

    suspend fun read(a: QuickAction): TileInfo = when (a) {
        QuickAction.Gaming -> onOff(Gaming.active.value)
        QuickAction.Animations -> {
            val scale = Customization.animationScale()
            TileInfo(scale > 0f, if (scale > 0f) "${scale.toString().removeSuffix(".0")}×" else t(R.string.off))
        }
        QuickAction.Refresh -> Customization.refreshMode().let { TileInfo(it != RefreshMode.Adaptive, it.label) }
        QuickAction.StayAwake -> onOff(Tweaks.caffeine())
        QuickAction.Cache -> TileInfo(false, prefs.getLong("cache_freed", -1).let { if (it < 0) t(R.string.cc_tap) else android.text.format.Formatter.formatShortFileSize(Str.context, it) })
        QuickAction.Boost -> VolumeBoost.settings.value.let { TileInfo(it.enabled, if (it.enabled) "+${VolumeBoost.currentPercent(it)} %" else t(R.string.off)) }
        QuickAction.Clean -> TileInfo(false, prefs.getLong("clean_moved", -1).let { if (it < 0) t(R.string.cc_tap) else android.text.format.Formatter.formatShortFileSize(Str.context, it) })
    }

    suspend fun toggle(a: QuickAction, current: TileInfo) {
        when (a) {
            QuickAction.Gaming -> if (current.active) Gaming.disable() else Gaming.enable()
            QuickAction.Animations -> if (current.active) {
                prefs.edit().putFloat("last_anim", Customization.animationScale()).apply()
                Customization.setAnimationScale(0f)
            } else {
                Customization.setAnimationScale(prefs.getFloat("last_anim", 1f).takeIf { it > 0f } ?: 1f)
            }
            QuickAction.Refresh -> {
                val entries = RefreshMode.entries
                Customization.setRefresh(entries[(entries.indexOf(Customization.refreshMode()) + 1) % entries.size])
            }
            QuickAction.StayAwake -> Tweaks.setCaffeine(!current.active)
            // Both can take a while: they run app-wide in CleanerJobs (foreground service) – a tile's
            // own scope is cancelled as soon as the shade closes (Daniel's review, 0.11.1).
            QuickAction.Cache -> longJob(CleanTool.AppCache, a) {
                prefs.edit().putLong("cache_freed", de.emil.shizubox.system.CacheCleaner.clearAll(Str.context)).apply()
            }
            // The cleaner's automatic categories, into the trash (restorable).
            QuickAction.Clean -> longJob(CleanTool.Auto, a) {
                de.emil.shizubox.cleaner.AutoClean.run(Str.context).let { r ->
                    if (r.skipped == null) prefs.edit().putLong("clean_moved", r.bytes).apply()
                    de.emil.shizubox.cleaner.AutoClean.notify(Str.context, r)
                }
            }
            QuickAction.Boost -> {
                VolumeBoost.update { it.copy(enabled = !current.active) }
                BoostService.sync(Str.context)
            }
        }
        QuickWidgets.refreshAsync(Str.context)
    }

    private fun longJob(tool: CleanTool, a: QuickAction, block: suspend () -> Unit) {
        CleanerJobs.start(tool) { report ->
            report(a.label, null)
            block()
            QuickWidgets.refreshAsync(Str.context)
            val tile = when (a) {
                QuickAction.Cache -> CacheTile::class.java
                else -> CleanTile::class.java
            }
            runCatching { android.service.quicksettings.TileService.requestListeningState(Str.context, android.content.ComponentName(Str.context, tile)) }
        }
    }
}
