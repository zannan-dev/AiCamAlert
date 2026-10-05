package com.example.aicamalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Neutral surfaces and text, with blue reserved for primary controls and selection. */
private fun appColorScheme(darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (darkTheme) PureBlack else PureWhite
    val foreground = if (darkTheme) Color(0xFFF5F5F5) else Color(0xFF161616)
    val secondaryText = if (darkTheme) Color(0xFFB8B8B8) else Color(0xFF616161)
    val defaults = if (darkTheme) darkColorScheme() else lightColorScheme()
    return defaults.copy(
        primary = AccentBlue,
        onPrimary = PureWhite,
        secondary = secondaryText,
        onSecondary = base,
        tertiary = secondaryText,
        onTertiary = base,
        background = base,
        onBackground = foreground,
        surface = if (darkTheme) Color(0xFF0D0D0D) else PureWhite,
        onSurface = foreground,
        surfaceVariant = if (darkTheme) Color(0xFF1A1A1A) else Color(0xFFF1F1F1),
        onSurfaceVariant = secondaryText,
        primaryContainer = if (darkTheme) Color(0xFF122747) else Color(0xFFDCEAFF),
        onPrimaryContainer = if (darkTheme) Color(0xFFADCFFF) else AccentBlue,
        secondaryContainer = if (darkTheme) Color(0xFF242424) else Color(0xFFEEEEEE),
        onSecondaryContainer = foreground,
        tertiaryContainer = if (darkTheme) Color(0xFF242424) else Color(0xFFEEEEEE),
        onTertiaryContainer = foreground,
        surfaceContainerLowest = base,
        surfaceContainerLow = if (darkTheme) Color(0xFF101010) else Color(0xFFFAFAFA),
        surfaceContainer = if (darkTheme) Color(0xFF171717) else Color(0xFFF5F5F5),
        surfaceContainerHigh = if (darkTheme) Color(0xFF222222) else Color(0xFFF1F1F1),
        surfaceContainerHighest = if (darkTheme) Color(0xFF2C2C2C) else Color(0xFFE8E8E8),
        surfaceBright = if (darkTheme) Color(0xFF2C2C2C) else PureWhite,
        surfaceDim = if (darkTheme) PureBlack else Color(0xFFE8E8E8),
        outline = if (darkTheme) Color(0xFF808080) else Color(0xFF757575),
        outlineVariant = if (darkTheme) Color(0xFF373737) else Color(0xFFDDDDDD),
        inverseSurface = if (darkTheme) Color(0xFFF5F5F5) else Color(0xFF222222),
        inverseOnSurface = if (darkTheme) Color(0xFF161616) else Color(0xFFF5F5F5),
        inversePrimary = if (darkTheme) AccentBlue else Color(0xFFADCFFF),
        surfaceTint = base,
    )
}

private val DarkColorScheme = appColorScheme(darkTheme = true)
private val LightColorScheme = appColorScheme(darkTheme = false)

@Composable
fun AiCamAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
