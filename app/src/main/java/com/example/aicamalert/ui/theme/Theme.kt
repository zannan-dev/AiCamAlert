package com.example.aicamalert.ui.theme

import android.app.Activity
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
    secondary = AccentCyanDim,
    tertiary = Pink80,
    background = DarkBackground,
    surface = SurfaceColor,
    onPrimary = Color.Black,
    onSecondary = AccentCyan,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = AccentCyan,
    secondaryContainer = Color(0xFF002B30),
    onSecondaryContainer = AccentCyan
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
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE0E3E3),
    onSurfaceVariant = Color(0xFF404848),
    secondaryContainer = LightPrimary.copy(alpha = 0.1f),
    onSecondaryContainer = LightPrimary
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