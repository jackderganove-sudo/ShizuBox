package de.emil.shizubox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import de.emil.shizubox.settings.AppSettings
import de.emil.shizubox.settings.ColorSource
import de.emil.shizubox.settings.Palette
import de.emil.shizubox.settings.ThemeMode

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
)

/** Whether the current theme is dark (respects the app's own light/dark setting). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Material You colors from the wallpaper, or a palette generated from a chosen accent color
 * (MaterialKolor). Light/dark and AMOLED black follow the app settings.
 */
@Composable
fun ShizuBoxTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val s by AppSettings.state.collectAsStateWithLifecycle()
    val dark = when (s.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = remember(s, dark) {
        val base = when (s.colorSource) {
            ColorSource.Wallpaper -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            ColorSource.Accent -> dynamicColorScheme(
                seedColor = Color(s.accent.toULong().toLong()),
                isDark = dark,
                isAmoled = s.amoled,
                style = when (s.palette) {
                    Palette.TonalSpot -> PaletteStyle.TonalSpot
                    Palette.Vibrant -> PaletteStyle.Vibrant
                    Palette.Expressive -> PaletteStyle.Expressive
                    Palette.Neutral -> PaletteStyle.Neutral
                    Palette.Monochrome -> PaletteStyle.Monochrome
                },
            )
        }
        if (dark && s.amoled) {
            base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
        } else base
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}
