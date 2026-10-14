package de.emil.shizubox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
)

/** Material You colors derived from the wallpaper (minSdk 31, so always available). */
@Composable
fun ShizuBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
