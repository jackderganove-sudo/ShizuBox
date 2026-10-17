package de.emil.shizubox.settings

import de.emil.shizubox.t
import de.emil.shizubox.R
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(private val labelRes: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark);
    val label get() = t(labelRes)
}

enum class ColorSource { Wallpaper, Accent }

enum class Palette(private val labelRes: Int) {
    TonalSpot(R.string.pal_calm), Vibrant(R.string.pal_vibrant), Expressive(R.string.pal_expressive),
    Neutral(R.string.pal_neutral), Monochrome(R.string.pal_mono);
    val label get() = t(labelRes)
}

/** How apps get installed. */
enum class InstallMethod(private val labelRes: Int, private val hintRes: Int) {
    Auto(R.string.im_auto, R.string.im_auto_sub),
    Shizuku(R.string.im_shizuku, R.string.im_shizuku_sub),
    System(R.string.im_system, R.string.im_system_sub),
    ;
    val label get() = t(labelRes)
    val hint get() = t(hintRes)
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
    /** Section opened at app start ("" = home screen). */
    val startSection: String = "",
    val showSchoolCard: Boolean = true,
    val showQuickActions: Boolean = true,
    /** Section names hidden from the home screen. */
    val hiddenSections: Set<String> = emptySet(),
    /** Home screen order of keys ("shizuku", "school", "quick", "sec:<Section>", "fav:<search id>"); empty = default. */
    val homeOrder: List<String> = emptyList(),
    /** Pinned sub pages/tools (search index ids). */
    val favorites: List<String> = emptyList(),
    /** Section tiles shown full width instead of two per row. */
    val wideSections: Set<String> = emptySet(),
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
            startSection = prefs.getString("start_section", d.startSection).orEmpty(),
            showSchoolCard = prefs.getBoolean("show_school_card", d.showSchoolCard),
            showQuickActions = prefs.getBoolean("show_quick_actions", d.showQuickActions),
            hiddenSections = prefs.getString("hidden_sections", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            homeOrder = prefs.getString("home_order", "").orEmpty().split(',').filter { it.isNotBlank() },
            favorites = prefs.getString("favorites", "").orEmpty().split(',').filter { it.isNotBlank() },
            wideSections = prefs.getString("wide_sections", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
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
            .putString("start_section", s.startSection)
            .putBoolean("show_school_card", s.showSchoolCard)
            .putBoolean("show_quick_actions", s.showQuickActions)
            .putString("hidden_sections", s.hiddenSections.joinToString(","))
            .putString("home_order", s.homeOrder.joinToString(","))
            .putString("favorites", s.favorites.joinToString(","))
            .putString("wide_sections", s.wideSections.joinToString(","))
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
