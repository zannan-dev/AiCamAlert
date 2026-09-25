package com.example.aicamalert.location

import android.location.Location
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.data.model.CameraItem
import kotlin.math.abs

/**
 * Proximity engine using spatial grid for efficient camera detection.
 *
 * Instead of linearly scanning all 704 cameras on every location update,
 * this engine uses [CameraRepository.findNearestCamera] which only checks
 * cameras in adjacent ~1.1km grid cells — typically 5–15 items.
 */
class ProximityEngine(private val repository: CameraRepository) {

    companion object {
        const val BASE_ALERT_RADIUS_METERS = 500.0
        const val MAX_ALERT_RADIUS_METERS = 1_200.0
        /** Seconds of travel time used to extend alert radius at speed. */
        const val LOOKAHEAD_SECONDS = 8.0
        /** Below this speed (m/s), bearing check is skipped (~7 km/h). */
        const val MIN_SPEED_FOR_BEARING_MPS = 2f
        /** Camera must be within this angle of travel direction (degrees). */
        const val MAX_BEARING_OFFSET_DEG = 90f
    }

    /**
     * Find the nearest camera within alert range that the user is actually approaching.
     *
     * @return Pair of (camera, distanceMeters) or null if no qualifying camera.
     */
    fun findApproachingInRange(location: Location): Pair<CameraItem, Double>? {
        val radius = computeDynamicAlertRadius(location.speed)
        return repository.findNearestCamera(
            location.latitude, location.longitude, radius
        ) { camera -> isApproaching(location, camera) }
    }

    /**
     * @deprecated Use [findApproachingInRange] for alert logic.
     */
    fun findNearestInRange(lat: Double, lon: Double): Pair<CameraItem, Double>? {
        return repository.findNearestCamera(lat, lon, BASE_ALERT_RADIUS_METERS)
    }

    /**
     * Find the nearest camera at any distance (for display purposes).
     */
    fun findNearest(lat: Double, lon: Double): Pair<CameraItem, Double>? {
        return repository.findNearestCamera(lat, lon, Double.MAX_VALUE)
    }

    /**
     * Extend alert radius at higher speeds so highway drivers get earlier warning.
     * At 0 m/s → 500 m; at 30 m/s (~108 km/h) → 740 m; capped at 1200 m.
     * Tuned on dev branch for Kerala highway conditions (NH66, MC Road).
     */
    fun computeDynamicAlertRadius(speedMps: Float): Double {
        if (speedMps <= 0f) return BASE_ALERT_RADIUS_METERS
        return (BASE_ALERT_RADIUS_METERS + speedMps * LOOKAHEAD_SECONDS)
            .coerceIn(BASE_ALERT_RADIUS_METERS, MAX_ALERT_RADIUS_METERS)
    }

    /**
     * Returns true if the camera is ahead of the user's direction of travel.
     * Falls back to true when speed/bearing are unavailable (stationary or GPS gap).
     */
    fun isApproaching(location: Location, camera: CameraItem): Boolean {
        if (location.speed >= 0 && location.speed < MIN_SPEED_FOR_BEARING_MPS) {
            return true
        }
        if (!location.hasBearing()) {
            return true
        }

        val results = FloatArray(2)
        Location.distanceBetween(
            location.latitude, location.longitude,
            camera.latitude, camera.longitude,
            results
        )
        val bearingToCamera = results[1]
        val diff = abs(normalizeAngle(bearingToCamera - location.bearing))
        return diff <= MAX_BEARING_OFFSET_DEG
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }
}
