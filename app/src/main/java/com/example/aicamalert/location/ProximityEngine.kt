package com.example.aicamalert.location

import android.location.Location
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.data.model.CameraItem
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

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
        /** Below this speed (m/s), use the movement-derived bearing in the gate. */
        const val MIN_SPEED_FOR_BEARING_MPS = 2f
        /** Camera must be within this angle of travel direction (degrees). */
        const val MAX_BEARING_OFFSET_DEG = 30f
        /** Tolerance around projected travel path, including camera placement beside the road. */
        const val PATH_HALF_WIDTH_METERS = 35f
        const val MAX_BEARING_ACCURACY_DEG = 20f
    }

    /**
     * Find the nearest camera within alert range that the user is actually approaching.
     *
     * @return Pair of (camera, distanceMeters) or null if no qualifying camera.
     */
    fun findApproachingInRange(
        location: Location,
        travelBearing: Float? = null,
        qualifies: (CameraItem) -> Boolean = { true },
    ): Pair<CameraItem, Double>? {
        val radius = computeDynamicAlertRadius(location.speed)
        return repository.findNearestCamera(
            location.latitude, location.longitude, radius
        ) { camera -> qualifies(camera) && isApproaching(location, camera, travelBearing) }
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
        if (!speedMps.isFinite() || speedMps <= 0f) return BASE_ALERT_RADIUS_METERS
        return (BASE_ALERT_RADIUS_METERS + speedMps * LOOKAHEAD_SECONDS)
            .coerceIn(BASE_ALERT_RADIUS_METERS, MAX_ALERT_RADIUS_METERS)
    }

    /** Whether GPS provides a heading precise enough for projecting the travel path. */
    fun hasReliableBearing(location: Location): Boolean =
        location.hasSpeed() && location.speed.isFinite() &&
            location.speed >= MIN_SPEED_FOR_BEARING_MPS &&
            location.hasBearing() && location.bearing.isFinite() &&
            (android.os.Build.VERSION.SDK_INT < 26 || !location.hasBearingAccuracy() ||
                (location.bearingAccuracyDegrees.isFinite() &&
                    location.bearingAccuracyDegrees in 0f..MAX_BEARING_ACCURACY_DEG))

    /**
     * Requires a camera ahead and close to the projected travel path. This is a
     * geometric filter, not road matching: the dataset contains points, not roads.
     * [travelBearing] can be inferred from confirmed movement when GPS heading is unavailable.
     */
    fun isApproaching(
        location: Location,
        camera: CameraItem,
        travelBearing: Float? = null,
    ): Boolean {
        val heading = travelBearing ?: if (hasReliableBearing(location)) location.bearing else return false
        if (!heading.isFinite()) return false
        val results = FloatArray(2)
        Location.distanceBetween(
            location.latitude, location.longitude,
            camera.latitude, camera.longitude,
            results
        )
        val diff = abs(normalizeAngle(results[1] - heading))
        if (diff > MAX_BEARING_OFFSET_DEG) return false
        val radians = Math.toRadians(diff.toDouble())
        val alongPath = results[0] * cos(radians)
        val acrossPath = results[0] * sin(radians)
        val gpsTolerance = if (location.hasAccuracy()) location.accuracy.coerceIn(0f, 25f) else 0f
        return alongPath > 0 && acrossPath <= PATH_HALF_WIDTH_METERS + gpsTolerance
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }
}
