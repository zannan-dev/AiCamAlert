package com.example.aicamalert

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.aicamalert.data.model.CameraItem
import com.example.aicamalert.ui.components.MapCameraDetails
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapCameraDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailsShowLiveDistanceAndActionsInLightTheme() = checkDetails(false)
    @Test fun detailsShowLiveDistanceAndActionsInDarkTheme() = checkDetails(true)

    private fun checkDetails(darkTheme: Boolean) {
        val camera = CameraItem("Perumanna junction – Kozhikode bypass camera", "Kozhikode",
            "354 m", 11.24, 75.8, 354.0)
        val gpsActive = mutableStateOf(true)
        var centered = false
        var directions = false
        var dismissed = false
        compose.setContent {
            AiCamAlertTheme(darkTheme) {
                MapCameraDetails(camera, gpsActive.value, { dismissed = true },
                    { centered = true }, { directions = true })
            }
        }
        compose.onNodeWithText(camera.name).assertIsDisplayed()
        compose.onNodeWithText("Kozhikode").assertIsDisplayed()
        compose.onNodeWithText("354 m").assertIsDisplayed()
        compose.onNodeWithContentDescription("Center camera on map").performClick()
        compose.onNodeWithText("Directions").performClick()
        compose.onNodeWithContentDescription("Close camera details").performClick()
        compose.runOnIdle {
            assertTrue(centered && directions && dismissed)
            gpsActive.value = false
        }
        compose.onNodeWithText("354 m").assertDoesNotExist()
        compose.onNodeWithText("Location needed to show distance").assertIsDisplayed()
    }
}
