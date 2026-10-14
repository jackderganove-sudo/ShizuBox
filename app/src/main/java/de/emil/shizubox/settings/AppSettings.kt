package de.emil.shizubox.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { System("System"), Light("Hell"), Dark("Dunkel") }

enum class ColorSource { Wallpaper, Accent }

enum class Palette(val label: String) { TonalSpot("Ruhig"), Vibrant("Kräftig"), Expressive("Expressiv"), Neutral("Neutral"), Monochrome("Monochrom") }

/** How apps get installed. */
enum class InstallMethod(val label: String, val hint: String) {
    Auto("Automatisch", "Still über Shizuku, sonst System-Dialog"),
    Shizuku("Nur Shizuku", "Immer still, braucht Shizuku"),
    System("System-Dialog", "Ohne Shizuku, jede Installation bestätigen"),
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Wallpaper,
    /** ARGB accent color when [colorSource] is [ColorSource.Accent]. */
    val accent: Long = 0xFF00897BL,
    val palette: Palette = Palette.TonalSpot,
    val amoled: Boolean = false,
    /** Frosted glass (window blur + card blur). Off = plain tinted cards, saves battery. */
    val glass: Boolean = true,
    /** Blur strength in percent (25–150). */
    val blurStrength: Int = 100,
    val animatedBackground: Boolean = true,
    val installMethod: InstallMethod = InstallMethod.Auto,
    val defaultDowngrade: Boolean = false,
    val defaultGrantAll: Boolean = false,
    val defaultPlayStoreSource: Boolean = false,
    /** Hash lookup on VirusTotal as soon as the installer opens. */
    val vtAutoScan: Boolean = true,
    /** Refuse installs whose signature differs from the installed app. */
    val blockSignatureMismatch: Boolean = true,
)

/** App-wide settings in device-protected storage (also readable before the first unlock). */
object AppSettings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current get() = _state.value

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val d = Settings()
        _state.value = Settings(
            themeMode = enumOr(prefs.getString("theme", null), d.themeMode),
            colorSource = enumOr(prefs.getString("color_source", null), d.colorSource),
            accent = prefs.getLong("accent", d.accent),
            palette = enumOr(prefs.getString("palette", null), d.palette),
            amoled = prefs.getBoolean("amoled", d.amoled),
            glass = prefs.getBoolean("glass", d.glass),
            blurStrength = prefs.getInt("blur", d.blurStrength),
            animatedBackground = prefs.getBoolean("animated_bg", d.animatedBackground),
            installMethod = enumOr(prefs.getString("install_method", null), d.installMethod),
            defaultDowngrade = prefs.getBoolean("def_downgrade", d.defaultDowngrade),
            defaultGrantAll = prefs.getBoolean("def_grant_all", d.defaultGrantAll),
            defaultPlayStoreSource = prefs.getBoolean("def_play", d.defaultPlayStoreSource),
            vtAutoScan = prefs.getBoolean("vt_auto", d.vtAutoScan),
            blockSignatureMismatch = prefs.getBoolean("block_sig", d.blockSignatureMismatch),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putString("theme", s.themeMode.name)
            .putString("color_source", s.colorSource.name)
            .putLong("accent", s.accent)
            .putString("palette", s.palette.name)
            .putBoolean("amoled", s.amoled)
            .putBoolean("glass", s.glass)
            .putInt("blur", s.blurStrength)
            .putBoolean("animated_bg", s.animatedBackground)
            .putString("install_method", s.installMethod.name)
            .putBoolean("def_downgrade", s.defaultDowngrade)
            .putBoolean("def_grant_all", s.defaultGrantAll)
            .putBoolean("def_play", s.defaultPlayStoreSource)
            .putBoolean("vt_auto", s.vtAutoScan)
            .putBoolean("block_sig", s.blockSignatureMismatch)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
