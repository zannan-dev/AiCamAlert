package com.example.aicamalert

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.aicamalert.ui.components.HeaderSection
import com.example.aicamalert.ui.components.SearchAndFilters
import com.example.aicamalert.ui.theme.AiCamAlertTheme
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
        compose.onNodeWithText("AiCam Alert").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Camera alerts").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Off · browse cameras anytime").assertDoesNotExist()
        compose.onNodeWithText("On · includes background alerts").assertDoesNotExist()
    }

    @Test
    fun filtersAreHiddenUntilRequested() {
        compose.setContent {
            AiCamAlertTheme(darkTheme = false) {
                SearchAndFilters("", {}, "All Districts", {}, listOf("All Districts"),
                    emptyMap(), "All", {})
            }
        }
        compose.onNodeWithText("Distance Filter").assertDoesNotExist()
        compose.onNodeWithText("Nearest first").assertDoesNotExist()
        compose.onNodeWithText("District order").assertDoesNotExist()
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithText("Distance Filter").assertIsDisplayed()
    }
}
