package com.example.aicamalert.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = Color(0xFFB0CCD0),
    tertiary = Color(0xFFE4C38C),
    background = DarkBackground,
    surface = SurfaceColor,
    onPrimary = Color.Black,
    onSecondary = Color(0xFF193338),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = AccentCyan,
    secondaryContainer = Color(0xFF002B30),
    onSecondaryContainer = AccentCyan,
    surfaceContainerLowest = Color(0xFF0D1519),
    surfaceContainerLow = Color(0xFF162328),
    surfaceContainer = Color(0xFF1C2A30),
    surfaceContainerHigh = Color(0xFF25343A),
    surfaceContainerHighest = Color(0xFF304047),
    outline = Color(0xFF84999F),
    outlineVariant = Color(0xFF3C5057),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF14262A),
    onSurface = Color(0xFF14262A),
    surfaceVariant = Color(0xFFE8F1F3),
    onSurfaceVariant = Color(0xFF465F65),
    primaryContainer = Color(0xFFC9F1F7),
    onPrimaryContainer = Color(0xFF004B58),
    secondaryContainer = Color(0xFFD9EBEF),
    onSecondaryContainer = Color(0xFF174C56),
    outline = Color(0xFF71888E),
    outlineVariant = Color(0xFFCAD8DC),
    tertiaryContainer = Color(0xFFFFE8BE),
    onTertiaryContainer = Color(0xFF553D0B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F6),
    surfaceContainer = Color(0xFFEAF1F3),
    surfaceContainerHigh = Color(0xFFE3ECEF),
    surfaceContainerHighest = Color(0xFFDBE6E9),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun AiCamAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
