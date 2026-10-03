package com.example.aicamalert

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.ui.components.PermissionSetupGate
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import com.example.aicamalert.util.PermissionUtils
import com.example.aicamalert.util.RequiredPermissionState
import com.example.aicamalert.util.RequiredSetupStep
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PermissionSetupTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun savedCompletionCannotBypassARevokedPermissionAndReturningAfterGrantUnlocksApp() {
        val prefs = context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
        val old = prefs.getBoolean("permission_setup_complete", false)
        prefs.edit().putBoolean("permission_setup_complete", true).commit()
        var granted = RequiredPermissionState(RequiredSetupStep.entries.toSet() - RequiredSetupStep.PRECISE_LOCATION)
        var completed = false
        try {
            compose.setContent {
                AiCamAlertTheme(darkTheme = false) {
                    PermissionSetupGate(onSetupCompleted = { completed = true }, readPermissions = { granted }) {
                        Text("Camera home")
                    }
                }
            }
            compose.onNodeWithText("Set up camera alerts").assertIsDisplayed()
            compose.onNodeWithText("Camera home").assertDoesNotExist()
            compose.onNodeWithText("Finish setup & enable alerts").assertDoesNotExist()
            compose.runOnIdle { granted = RequiredPermissionState(RequiredSetupStep.entries.toSet()) }
            // The button rechecks Android state, so a completed external grant is picked up.
            compose.onNode(hasText("Allow precise location") or hasText("Open location permissions")).performClick()
            compose.onNodeWithText("Camera home").assertIsDisplayed()
            assertTrue(completed)
        } finally {
            prefs.edit().putBoolean("permission_setup_complete", old).commit()
        }
    }

    @Test fun fullScreenAccessOpensThisAppsDedicatedSettingsOnSupportedAndroid() {
        val intent = PermissionUtils.fullScreenSettingsIntent(context)
        assertEquals(if (Build.VERSION.SDK_INT >= 34) Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test fun unavailableSpecialSettingsFallBackToAppDetails() {
        val opened = mutableListOf<Intent>()
        val device = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                opened += intent
                if (intent.action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                    throw android.content.ActivityNotFoundException("Simulated manufacturer omission")
                }
            }
        }
        PermissionUtils.requestOverlayPermission(device)
        assertEquals(listOf(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            opened.map { it.action })
        assertEquals("package:${context.packageName}", opened.last().data.toString())
    }
}
