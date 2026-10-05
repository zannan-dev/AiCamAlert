package com.example.aicamalert

import android.location.Location
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.location.CameraAlertGate
import com.example.aicamalert.location.ProximityEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraAlertGateTest {
    private lateinit var gate: CameraAlertGate
    private var baseTime = 0L

    @Before
    fun prepare() = runBlocking {
        val repository = CameraRepository(InstrumentationRegistry.getInstrumentation().context)
        repository.loadCameras()
        assertEquals(4, repository.cameras.size)
        gate = CameraAlertGate(ProximityEngine(repository))
        baseTime = SystemClock.elapsedRealtimeNanos() - 20_000_000_000L
    }

    private fun fix(lat: Double, second: Long, speed: Float = 10f, bearing: Float = 0f) =
        Location("test").apply {
            latitude = lat
            longitude = 76.0
            accuracy = 5f
            this.speed = speed
            this.bearing = bearing
            elapsedRealtimeNanos = baseTime + second * 1_000_000_000L
        }

    @Test
    fun stationaryFixesNeverAlertEvenWithReportedSpeed() {
        assertNull(gate.nextAlert(fix(10.002, 0)))
        assertNull(gate.nextAlert(fix(10.00202, 5)))
        assertNull(gate.nextAlert(fix(10.00201, 10)))
    }

    @Test
    fun cameraAlertsOnceUntilDriverLeavesAndReturns() {
        assertNull(gate.nextAlert(fix(10.0017, 0)))
        assertEquals("Ahead", gate.nextAlert(fix(10.002, 5))?.first?.name)
        assertNotEquals("Ahead", gate.nextAlert(fix(10.0023, 10))?.first?.name)
        // Move beyond the maximum alert radius, then drive back towards it.
        gate.nextAlert(fix(10.020, 15))
        assertEquals("Ahead", gate.nextAlert(fix(10.0052, 17, bearing = 180f))?.first?.name)
        assertNotEquals("Ahead", gate.nextAlert(fix(10.0048, 19, bearing = 180f))?.first?.name)
    }

    @Test
    fun staleFixDoesNotAlert() {
        val stale = fix(10.002, 0).apply { elapsedRealtimeNanos -= 60_000_000_000L }
        assertNull(gate.nextAlert(stale))
    }
    @Test
    fun slowApproachCanAlertBeforeTwentyMetersOfMovement() {
        assertNull(gate.nextAlert(fix(10.00188, 0, speed = 1f)))
        // Low-speed GPS bearing is replaced by the direction of confirmed movement.
        assertEquals("Ahead", gate.nextAlert(fix(10.002, 15, speed = 1f, bearing = 180f))?.first?.name)
    }

    @Test
    fun outOfOrderFixDoesNotReplaceMovementAnchor() {
        assertNull(gate.nextAlert(fix(10.0017, 5)))
        assertNull(gate.nextAlert(fix(10.002, 0)))
        // Only 5 m from the older fix, but 39 m from the correct anchor.
        assertEquals("Ahead", gate.nextAlert(fix(10.00205, 10))?.first?.name)
    }

    @Test
    fun poorAccuracyFixDoesNotAlert() {
        gate.nextAlert(fix(10.0017, 0))
        assertNull(gate.nextAlert(fix(10.002, 5).apply { accuracy = 100f }))
    }

    @Test
    fun parallelRoadWithinAlertRadiusNeverAlerts() {
        assertNull(gate.nextAlert(fix(10.0017, 0).apply { longitude = 76.001 }))
        assertNull(gate.nextAlert(fix(10.002, 5).apply { longitude = 76.001 }))
    }

    @Test
    fun missingSpeedAndBearingUseConfirmedMovement() {
        assertNull(gate.nextAlert(fix(10.0017, 0).apply { removeSpeed(); removeBearing() }))
        assertEquals("Ahead", gate.nextAlert(
            fix(10.002, 5).apply { removeSpeed(); removeBearing() },
        )?.first?.name)
    }

    @Test
    fun movingAwayDoesNotAlertDespiteIncorrectReportedHeading() {
        assertNull(gate.nextAlert(fix(10.0023, 0)))
        assertNull(gate.nextAlert(fix(10.002, 5)))
    }

    @Test
    fun unreliableHeadingUsesMovementInstead() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        assertNull(gate.nextAlert(fix(10.0017, 0)))
        assertEquals("Ahead", gate.nextAlert(
            fix(10.002, 5, bearing = 180f).apply { bearingAccuracyDegrees = 80f },
        )?.first?.name)
    }
}
