package com.example.aicamalert

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.location.ProximityEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun missingBearingFallsBackToNearestCamera() {
        val location = location().apply { removeBearing() }
        assertEquals("Behind", engine.findApproachingInRange(location)?.first?.name)
    }

    @Test
    fun stationaryLocationDoesNotFilterByBearing() {
        val location = location().apply { speed = 0f }
        assertEquals("Behind", engine.findApproachingInRange(location)?.first?.name)
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
}
