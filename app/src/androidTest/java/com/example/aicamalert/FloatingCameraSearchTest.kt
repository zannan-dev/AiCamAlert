package com.example.aicamalert

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.aicamalert.ui.components.FloatingCameraSearch
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import org.junit.Rule
import org.junit.Test

class FloatingCameraSearchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun floatingSearchKeepsQueryAndFiltersInLightTheme() = checkSearch(false)
    @Test fun floatingSearchKeepsQueryAndFiltersInDarkTheme() = checkSearch(true)

    private fun checkSearch(darkTheme: Boolean) {
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }
            var district by remember { mutableStateOf("All Districts") }
            AiCamAlertTheme(darkTheme) {
                Box(Modifier.fillMaxSize().imePadding().padding(bottom = 80.dp),
                    contentAlignment = Alignment.BottomCenter) {
                    FloatingCameraSearch(expanded, { expanded = it }, query, { query = it },
                        district, { district = it }, listOf("All Districts", "Kozhikode"),
                        mapOf("Kozhikode" to 3), "All", {}, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        compose.onNodeWithContentDescription("Search cameras or districts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search cameras").performClick()
        compose.onNodeWithContentDescription("Search cameras or districts").assertIsFocused()
            .performTextInput("Perumanna")
        compose.onNodeWithContentDescription("Search cameras or districts").performImeAction()
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithText("All Districts").performClick()
        compose.onNodeWithText("Kozhikode").performClick()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithContentDescription("Search cameras or districts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search cameras, filters active").performClick()
        compose.onNodeWithContentDescription("Search cameras or districts").assertTextContains("Perumanna")
        compose.onNodeWithText("Kozhikode").assertIsDisplayed()
        compose.onNodeWithText("Distance Filter").assertIsDisplayed()
    }
}
