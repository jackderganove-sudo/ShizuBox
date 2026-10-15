package de.emil.shizubox.tiles

import de.emil.shizubox.R
import de.emil.shizubox.Str
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.system.Tweaks
import de.emil.shizubox.t

enum class QuickAction(private val labelRes: Int, private val shortRes: Int) {
    Gaming(R.string.gaming, R.string.qa_gaming),
    Animations(R.string.tile_animations, R.string.tile_animations),
    Refresh(R.string.cust_refresh, R.string.qa_refresh),
    StayAwake(R.string.tile_stay_awake, R.string.qa_awake),
    ;
    val label get() = t(labelRes)
    /** For the narrow buttons on the home screen. */
    val shortLabel get() = t(shortRes)
}

/** One-tap switches shared by the quick settings tiles and the home screen. */
object QuickActions {
    private val prefs get() = Str.context.createDeviceProtectedStorageContext().getSharedPreferences("tiles", android.content.Context.MODE_PRIVATE)

    private fun onOff(active: Boolean) = TileInfo(active, if (active) t(R.string.on) else t(R.string.off))

    suspend fun read(a: QuickAction): TileInfo = when (a) {
        QuickAction.Gaming -> onOff(Gaming.active.value)
        QuickAction.Animations -> {
            val scale = Customization.animationScale()
            TileInfo(scale > 0f, if (scale > 0f) "${scale.toString().removeSuffix(".0")}×" else t(R.string.off))
        }
        QuickAction.Refresh -> Customization.refreshMode().let { TileInfo(it != RefreshMode.Adaptive, it.label) }
        QuickAction.StayAwake -> onOff(Tweaks.stayAwake())
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
            QuickAction.StayAwake -> Tweaks.setStayAwake(!current.active)
        }
    }
}
