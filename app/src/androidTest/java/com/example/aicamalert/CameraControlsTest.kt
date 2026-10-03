package com.example.aicamalert

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.aicamalert.ui.components.HeaderSection
import com.example.aicamalert.ui.components.SearchAndFilters
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import com.example.aicamalert.ui.theme.AppThemeMode
import com.example.aicamalert.ui.components.ThemeSettings
import com.example.aicamalert.ui.theme.rememberAppThemeMode
import com.example.aicamalert.ui.theme.saveAppThemeMode
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun alertSwitchShowsItsStateInLightTheme() {
        compose.setContent {
            var enabled by remember { mutableStateOf(false) }
            AiCamAlertTheme(darkTheme = false) {
                HeaderSection(enabled, { enabled = it })
            }
        }
        compose.onNodeWithContentDescription("Use dark theme").assertDoesNotExist()
        compose.onNodeWithContentDescription("Use light theme").assertDoesNotExist()
        compose.onNodeWithText("Off · browse cameras anytime").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("On · includes background alerts").assertIsDisplayed()
    }

    @Test
    fun filtersAreHiddenUntilRequested() {
        compose.setContent {
            AiCamAlertTheme(darkTheme = false) {
                SearchAndFilters("", {}, "All Districts", {}, listOf("All Districts"),
                    emptyMap(), "All", {}, true, {})
            }
        }
        compose.onNodeWithText("Distance Filter").assertDoesNotExist()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Distance Filter").assertIsDisplayed()
    }
    @Test
    fun settingsThemeChoicesUpdateAndPersistSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("aicam_prefs", android.content.Context.MODE_PRIVATE)
        val previous = preferences.getString(AppThemeMode.PREFERENCE_KEY, null)
        saveAppThemeMode(context, AppThemeMode.SYSTEM)
        try {
            compose.setContent {
                val mode by rememberAppThemeMode()
                AiCamAlertTheme(darkTheme = mode.isDark(false)) {
                    ThemeSettings(mode) { saveAppThemeMode(context, it) }
                }
            }
            compose.onNodeWithText("System (follow phone)").assertIsSelected()
            compose.onNodeWithText("Dark").performClick().assertIsSelected()
            assertEquals("DARK", preferences.getString(AppThemeMode.PREFERENCE_KEY, null))
            compose.onNodeWithText("Light").performClick().assertIsSelected()
            assertEquals("LIGHT", preferences.getString(AppThemeMode.PREFERENCE_KEY, null))
            compose.onNodeWithText("System (follow phone)").performClick().assertIsSelected()
            assertEquals("SYSTEM", preferences.getString(AppThemeMode.PREFERENCE_KEY, null))
        } finally {
            preferences.edit().putString(AppThemeMode.PREFERENCE_KEY, previous).apply()
        }
    }

}
