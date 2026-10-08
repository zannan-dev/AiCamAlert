package com.example.aicamalert

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class FullScreenAlertContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightAlert() = verifyAlert(false, false)
    @Test fun darkAlert() = verifyAlert(true, false)
    @Test fun compactAlertWithLargeTextKeepsStopReachable() = verifyAlert(false, true)

    private fun verifyAlert(dark: Boolean, compact: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        var stopped = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 1.5f else 1f)) {
                AiCamAlertTheme(dark) {
                    Box(if (compact) Modifier.height(440.dp) else Modifier) {
                        FullScreenAlertContent(
                            "Perumanna junction – Kozhikode bypass camera", "Kozhikode", "250 m", dark,
                            onDismiss = { stopped = true },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Stop alarm").assertIsDisplayed()
        compose.onNodeWithText("250 m").assertIsDisplayed()
        if (!compact) {
            compose.onNodeWithText("Perumanna junction – Kozhikode bypass camera").assertIsDisplayed()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = if (compact) "alert-compact" else if (dark) "alert-dark" else "alert-light"
        File(context.cacheDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Stop alarm").performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }
}
