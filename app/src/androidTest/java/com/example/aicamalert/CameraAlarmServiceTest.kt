package com.example.aicamalert

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.alert.AlertSoundManager
import com.example.aicamalert.data.model.CameraItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraAlarmServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val camera = CameraItem("Alarm test camera", "Test", "250 m", 10.0, 76.0)

    private fun preparePermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").close()
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Alarm state did not settle", condition())
    }

    @Test
    fun alarmLoopsInBackgroundAndSurvivesLeavingCameraZoneUntilNotificationAction() {
        preparePermissions()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { CameraProximityService.startAlarm(it, camera) }
                awaitCondition { AlertSoundManager.isPlaying() }
                scenario.moveToState(Lifecycle.State.CREATED)
                CameraProximityService.pauseRadarLocation(context)
                // Longer than both the old 1.2-second tone and the full new 3-second audio file.
                SystemClock.sleep(3_600)
                assertTrue(AlertSoundManager.isPlaying())
                assertEquals(camera.name, AlertSoundManager.activeAlert.value?.name)
                val manager = context.getSystemService(NotificationManager::class.java)
                val notification = manager.activeNotifications.single { it.id == CameraProximityService.NOTIFICATION_ID }
                val action = notification.notification.actions.single { it.title.toString() == "Stop alarm" }
                action.actionIntent.send()
                awaitCondition { AlertSoundManager.activeAlert.value == null && !AlertSoundManager.isPlaying() }
            } finally {
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { CameraProximityService.stopService(it) }
            }
        }
    }

    @Test
    fun foregroundAcknowledgementStopsAlarmAndNextCameraCanRingImmediately() {
        preparePermissions()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { CameraProximityService.startAlarm(it, camera) }
                awaitCondition { AlertSoundManager.isPlaying() }
                scenario.onActivity { CameraProximityService.acknowledgeAlarm(it) }
                awaitCondition { !AlertSoundManager.isPlaying() && AlertSoundManager.activeAlert.value == null }
                val next = camera.copy(name = "Next camera")
                // No global sound cooldown may suppress a different camera after acknowledgement.
                scenario.onActivity { CameraProximityService.startAlarm(it, next) }
                awaitCondition { AlertSoundManager.isPlaying() && AlertSoundManager.activeAlert.value?.name == next.name }
                scenario.onActivity { CameraProximityService.acknowledgeAlarm(it) }
                awaitCondition { !AlertSoundManager.isPlaying() }
            } finally {
                scenario.onActivity { CameraProximityService.stopService(it) }
            }
        }
    }
    @Test
    fun serviceRecreationRestoresAlarmUntilAcknowledged() {
        preparePermissions()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { CameraProximityService.startAlarm(it, camera) }
                awaitCondition { AlertSoundManager.isPlaying() }
                // Simulate service destruction without a user acknowledgement.
                scenario.onActivity { it.stopService(android.content.Intent(it, CameraProximityService::class.java)) }
                awaitCondition { !AlertSoundManager.isPlaying() }
                scenario.onActivity { CameraProximityService.startService(it) }
                awaitCondition { AlertSoundManager.isPlaying() && AlertSoundManager.activeAlert.value?.name == camera.name }
                scenario.onActivity { CameraProximityService.acknowledgeAlarm(it) }
                awaitCondition { AlertSoundManager.activeAlert.value == null }
            } finally {
                scenario.onActivity { CameraProximityService.stopService(it) }
            }
        }
    }

}
