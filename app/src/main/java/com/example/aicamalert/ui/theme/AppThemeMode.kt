package com.example.aicamalert.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** The saved appearance preference, shared by the main screen and camera alerts. */
enum class AppThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        const val PREFERENCE_KEY = "theme_mode"
        fun fromPreference(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

@Composable
fun rememberAppThemeMode(): State<AppThemeMode> {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
    }
    val mode = remember(preferences) {
        mutableStateOf(AppThemeMode.fromPreference(preferences.getString(AppThemeMode.PREFERENCE_KEY, null)))
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == AppThemeMode.PREFERENCE_KEY) {
                mode.value = AppThemeMode.fromPreference(prefs.getString(key, null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}

fun saveAppThemeMode(context: Context, mode: AppThemeMode) {
    context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE).edit()
        .putString(AppThemeMode.PREFERENCE_KEY, mode.name).apply()
}
