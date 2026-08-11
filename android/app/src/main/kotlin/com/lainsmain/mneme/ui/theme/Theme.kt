package com.lainsmain.mneme.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lainsmain.mneme.data.ThemePreference
import com.lainsmain.mneme.data.ColorPalette

private val MnemeDarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF522300),
    primaryContainer = Color(0xFF743600),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFBBCBAA),
    onSecondary = Color(0xFF27341F),
    secondaryContainer = Color(0xFF3D4B34),
    onSecondaryContainer = Color(0xFFD7E8C5),
    tertiary = Color(0xFF9CCFE5),
    background = Color(0xFF0E1013),
    surface = Color(0xFF0E1013),
    surfaceVariant = Color(0xFF41474B),
    surfaceContainer = Color(0xFF191C20),
    surfaceContainerHigh = Color(0xFF23272C),
)

private val MnemeLightColors = lightColorScheme(
    primary = Color(0xFF914B20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC8),
    onPrimaryContainer = Color(0xFF713600),
    secondary = Color(0xFF55634B),
    secondaryContainer = Color(0xFFD9E9C8),
    tertiary = Color(0xFF28627A),
    background = Color(0xFFFCF8F3),
    surface = Color(0xFFFCF8F3),
    surfaceContainer = Color(0xFFF1EDE8),
    surfaceContainerHigh = Color(0xFFEBE7E2),
)

@Composable
fun MnemeTheme(
    mode: ThemePreference = ThemePreference.Dark,
    palette: ColorPalette = ColorPalette.Ocean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> palette.darkColors()
        else -> palette.lightColors()
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MnemeTypography,
        shapes = MnemeShapes,
        content = content,
    )
}

private fun ColorPalette.darkColors() = when (this) {
    ColorPalette.Ocean -> darkScheme(Color(0xFF8BD5F5), Color(0xFF003545), Color(0xFF0D4B60), Color(0xFF0C0E11))
    ColorPalette.Forest -> darkScheme(Color(0xFFA8D5A2), Color(0xFF173819), Color(0xFF294C2B), Color(0xFF0C0E11))
    ColorPalette.Lavender -> darkScheme(Color(0xFFCDBDFF), Color(0xFF34265C), Color(0xFF493C72), Color(0xFF0C0E11))
    ColorPalette.Rose -> darkScheme(Color(0xFFFFB4C3), Color(0xFF57202D), Color(0xFF713645), Color(0xFF0C0E11))
    ColorPalette.Amber -> darkScheme(Color(0xFFFFC285), Color(0xFF4C2C06), Color(0xFF684214), Color(0xFF0C0E11))
    ColorPalette.Graphite -> darkScheme(Color(0xFFD1CAD6), Color(0xFF302E32), Color(0xFF48454C), Color(0xFF0C0E11))
}

private fun ColorPalette.lightColors() = when (this) {
    ColorPalette.Ocean -> lightScheme(Color(0xFF006780), Color(0xFFB9EAFF), Color(0xFFFAF9F6))
    ColorPalette.Forest -> lightScheme(Color(0xFF386A3B), Color(0xFFBAF0B5), Color(0xFFFAF9F6))
    ColorPalette.Lavender -> lightScheme(Color(0xFF67558F), Color(0xFFEADDFF), Color(0xFFFAF9F6))
    ColorPalette.Rose -> lightScheme(Color(0xFF98495D), Color(0xFFFFD9E1), Color(0xFFFAF9F6))
    ColorPalette.Amber -> lightScheme(Color(0xFF875300), Color(0xFFFFDDB5), Color(0xFFFAF9F6))
    ColorPalette.Graphite -> lightScheme(Color(0xFF5F5B62), Color(0xFFE7E1E9), Color(0xFFFAF9F6))
}

private fun darkScheme(accent: Color, onAccent: Color, container: Color, background: Color) = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = container,
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.88f),
    onSecondary = onAccent,
    secondaryContainer = container.copy(alpha = 0.82f),
    onSecondaryContainer = accent,
    tertiary = accent,
    background = background,
    surface = background,
    surfaceDim = Color(0xFF080A0C),
    surfaceBright = Color(0xFF25292E),
    surfaceContainerLowest = Color(0xFF080A0C),
    surfaceContainerLow = Color(0xFF101317),
    surfaceContainer = Color(0xFF15181D),
    surfaceContainerHigh = Color(0xFF1C2025),
    surfaceContainerHighest = Color(0xFF24282E),
    onBackground = Color(0xFFF0F1F3),
    onSurface = Color(0xFFF0F1F3),
    onSurfaceVariant = Color(0xFFADB2BA),
    outline = Color(0xFF777D86),
    outlineVariant = Color(0xFF343940),
)

private fun lightScheme(accent: Color, container: Color, background: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = container,
    onPrimaryContainer = Color(0xFF102028),
    secondary = accent,
    secondaryContainer = container.copy(alpha = 0.72f),
    tertiary = accent,
    background = background,
    surface = background,
    surfaceDim = Color(0xFFE2E1DD),
    surfaceBright = Color(0xFFFFFEFB),
    surfaceContainerLowest = Color(0xFFFFFEFB),
    surfaceContainerLow = Color(0xFFF5F4F1),
    surfaceContainer = Color(0xFFEFEEEA),
    surfaceContainerHigh = Color(0xFFE9E8E4),
    surfaceContainerHighest = Color(0xFFE3E2DE),
    onBackground = Color(0xFF1B1C1E),
    onSurface = Color(0xFF1B1C1E),
    onSurfaceVariant = Color(0xFF5E6268),
    outline = Color(0xFF777B81),
    outlineVariant = Color(0xFFC7C7C8),
)
