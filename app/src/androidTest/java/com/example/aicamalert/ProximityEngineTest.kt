package com.example.aicamalert

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.location.ProximityEngine
import com.example.aicamalert.data.model.CameraItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProximityEngineTest {
    private lateinit var engine: ProximityEngine

    @Before
    fun loadFixture() = runBlocking {
        // The test APK contains a small, deterministic camera dataset.
        val repository = CameraRepository(InstrumentationRegistry.getInstrumentation().context)
        repository.loadCameras()
        assertEquals(4, repository.cameras.size)
        engine = ProximityEngine(repository)
    }

    private fun location() = Location("test").apply {
        latitude = 10.002
        longitude = 76.0
        speed = 20f
        bearing = 0f
    }

    @Test
    fun closerCameraBehindDoesNotHideNearestCameraAhead() {
        val result = engine.findApproachingInRange(location())
        assertEquals("Ahead", result?.first?.name)
    }

    @Test
    fun allCamerasBehindProducesNoAlert() {
        val location = location().apply { latitude = 10.011 }
        assertNull(engine.findApproachingInRange(location))
    }

    @Test
    fun cameraAheadOutsideRadiusProducesNoAlert() {
        val location = location().apply { latitude = 9.994 }
        assertNull(engine.findApproachingInRange(location))
    }

    @Test
    fun missingBearingRequiresMovementDerivedHeading() {
        val location = location().apply { removeBearing() }
        assertNull(engine.findApproachingInRange(location))
    }

    @Test
    fun stationaryLocationDoesNotQualifyWithoutMovement() {
        val location = location().apply { speed = 0f }
        assertNull(engine.findApproachingInRange(location))
    }

    @Test
    fun bearingWrapsAcrossNorth() {
        val location = location().apply { bearing = 359f }
        assertEquals("Ahead", engine.findApproachingInRange(location)?.first?.name)
    }

    @Test
    fun alertRadiusGrowsWithSpeedAndIsCapped() {
        assertEquals(500.0, engine.computeDynamicAlertRadius(0f), 0.01)
        assertEquals(740.0, engine.computeDynamicAlertRadius(30f), 0.01)
        assertEquals(1200.0, engine.computeDynamicAlertRadius(100f), 0.01)
    }
    @Test
    fun maximumRadiusSearchIncludesCameraTwoGridCellsAway() {
        val location = location().apply { latitude = 9.9999; speed = 100f }
        val result = engine.findApproachingInRange(location) { it.name == "Out of range" }
        assertEquals("Out of range", result?.first?.name)
    }

    @Test
    fun nearestDisplaySearchWorksOutsideNeighbouringGrid() {
        assertEquals("Out of range", engine.findNearest(10.1, 76.0)?.first?.name)
    }

    @Test
    fun unreliableBearingRequiresMovementDerivedHeading() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val location = location().apply { bearing = 180f; bearingAccuracyDegrees = 80f }
        assertNull(engine.findApproachingInRange(location) { it.name == "Ahead" })
    }

    @Test
    fun cameraOnParallelRoadIsRejectedEvenWhenAhead() {
        val camera = CameraItem("Parallel road", "Test", latitude = 10.006, longitude = 76.001)
        assertFalse(engine.isApproaching(location(), camera))
    }

    @Test
    fun cameraOnCrossStreetIsRejected() {
        val camera = CameraItem("Cross street", "Test", latitude = 10.0022, longitude = 76.002)
        assertFalse(engine.isApproaching(location(), camera))
    }

    @Test
    fun roadsideCameraWithinPathToleranceStillQualifies() {
        val camera = CameraItem("Roadside", "Test", latitude = 10.004, longitude = 76.0002)
        assertTrue(engine.isApproaching(location(), camera))
    }

    @Test
    fun derivedHeadingAllowsApproachWithoutGpsBearing() {
        assertEquals("Ahead", engine.findApproachingInRange(
            location().apply { removeBearing() }, travelBearing = 0f,
        )?.first?.name)
    }

    @Test
    fun poorBearingPrecisionRequiresDerivedHeading() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        assertFalse(engine.hasReliableBearing(location().apply { bearingAccuracyDegrees = 30f }))
    }
}
