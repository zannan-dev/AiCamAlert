package com.example.aicamalert.ui.scroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IosOverscrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope
    private lateinit var coordinates: LayoutCoordinates

    private fun setList(count: Int = 30) {
        compose.setContent {
            AiCamAlertTheme {
                state = rememberLazyListState()
                scope = rememberCoroutineScope()
                LazyColumn(
                    modifier = Modifier.size(200.dp)
                        .then(rememberScrollBounce())
                        .onGloballyPositioned { coordinates = it }
                        .testTag("list"),
                    state = state,
                    overscrollEffect = null,
                    flingBehavior = rememberSmoothFlingBehavior(),
                ) {
                    items(count) { Box(Modifier.height(80.dp)) }
                }
            }
        }
    }

    @Test
    fun topPullIsResistedAndReturnsWithoutChangingScrollPosition() {
        setList()
        val start = compose.runOnIdle { coordinates.positionInRoot().y }
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f), delayMillis = 100)
        }
        compose.runOnIdle {
            val movement = coordinates.positionInRoot().y - start
            assertTrue(movement > 0f && movement < 80f)
        }
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(start, coordinates.positionInRoot().y, 0.5f)
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun shortListAlsoBouncesAndSettles() {
        setList(count = 1)
        val start = compose.runOnIdle { coordinates.positionInRoot().y }
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f), delayMillis = 100)
        }
        compose.runOnIdle { assertTrue(coordinates.positionInRoot().y > start) }
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(start, coordinates.positionInRoot().y, 0.5f) }
    }

    @Test
    fun bottomPullReturnsToSamePosition() {
        setList()
        compose.runOnIdle { scope.launch { state.scrollToItem(29) } }
        compose.waitForIdle()
        val start = compose.runOnIdle { coordinates.positionInRoot().y }
        val index = compose.runOnIdle { state.firstVisibleItemIndex }
        val offset = compose.runOnIdle { state.firstVisibleItemScrollOffset }
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f), delayMillis = 100)
        }
        compose.runOnIdle { assertTrue(coordinates.positionInRoot().y < start) }
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(start, coordinates.positionInRoot().y, 0.5f)
            assertEquals(index, state.firstVisibleItemIndex)
            assertEquals(offset, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun reversingDragUnwindsThePullBeforeScrolling() {
        setList()
        val start = compose.runOnIdle { coordinates.positionInRoot().y }
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f), delayMillis = 100)
        }
        val pulled = compose.runOnIdle { coordinates.positionInRoot().y }
        compose.onNodeWithTag("list").performTouchInput {
            moveBy(Offset(0f, -20f), delayMillis = 100)
        }
        compose.runOnIdle {
            val position = coordinates.positionInRoot().y
            assertTrue(position > start && position < pulled)
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(start, coordinates.positionInRoot().y, 0.5f) }
    }
}
