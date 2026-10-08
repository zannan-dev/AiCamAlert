package com.example.aicamalert

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.example.aicamalert.ui.components.FloatingViewNavigation
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class FloatingViewNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun draggingPreviewsUntilReleaseAndCanSwitchBack() {
        var listView by mutableStateOf(false)
        compose.setContent {
            AiCamAlertTheme {
                FloatingViewNavigation(listView, { listView = it })
            }
        }
        val map = compose.onNodeWithText("Map")
        val list = compose.onNodeWithText("List")
        val listCenter = list.fetchSemanticsNode().boundsInRoot.center
        val mapBounds = map.fetchSemanticsNode().boundsInRoot
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        map.performTouchInput {
            down(center)
            moveTo(listCenter - mapBounds.topLeft)
        }
        compose.runOnIdle { assertFalse(listView) }
        map.assertIsSelected()
        map.performTouchInput { up() }
        list.assertIsSelected()
        list.performTouchInput {
            down(center)
            moveTo(mapBounds.center - listBounds.topLeft)
            up()
        }
        map.assertIsSelected()
    }

    @Test
    fun cancelledDragKeepsTheSelectedView() {
        compose.setContent {
            var listView by remember { mutableStateOf(false) }
            AiCamAlertTheme {
                FloatingViewNavigation(listView, { listView = it })
            }
        }
        val map = compose.onNodeWithText("Map")
        val target = compose.onNodeWithText("List").fetchSemanticsNode().boundsInRoot.center
        val origin = map.fetchSemanticsNode().boundsInRoot.topLeft
        map.performTouchInput {
            down(center)
            moveTo(target - origin)
            cancel()
        }
        map.assertIsSelected()
    }

    @Test
    fun tapsAndSemanticClicksWorkInRtl() {
        compose.setContent {
            var listView by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AiCamAlertTheme {
                    FloatingViewNavigation(listView, { listView = it })
                }
            }
        }
        compose.onNodeWithText("List").performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithText("Map").performClick().assertIsSelected()
    }
}
