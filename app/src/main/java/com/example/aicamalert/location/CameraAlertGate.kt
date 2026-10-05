package com.example.aicamalert.location

import android.location.Location
import android.os.SystemClock
import com.example.aicamalert.data.model.CameraItem

/** Emits one alert per camera approach, after GPS has confirmed movement. */
class CameraAlertGate(private val engine: ProximityEngine) {
    private val alerted = mutableMapOf<String, CameraItem>()
    private var movementAnchor: Location? = null

    @Synchronized
    fun nextAlert(location: Location): Pair<CameraItem, Double>? {
        if (!isUsable(location)) return null
        alerted.entries.removeAll { (_, camera) ->
            val distance = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                camera.latitude, camera.longitude, distance,
            )
            distance[0] > REARM_DISTANCE_METERS
        }

        val anchor = movementAnchor
        // UI and service can deliver the same fix during a lifecycle transition.
        if (anchor != null && location.elapsedRealtimeNanos <= anchor.elapsedRealtimeNanos) return null
        if (anchor == null ||
            location.elapsedRealtimeNanos - anchor.elapsedRealtimeNanos > MAX_SAMPLE_GAP_NANOS
        ) {
            movementAnchor = Location(location)
            return null
        }

        val elapsedSeconds = (location.elapsedRealtimeNanos - anchor.elapsedRealtimeNanos) / 1e9
        val displacement = anchor.distanceTo(location)
        val minDisplacement = maxOf(
            MIN_MOVEMENT_METERS,
            maxOf(
                if (anchor.hasAccuracy()) anchor.accuracy else 0f,
                if (location.hasAccuracy()) location.accuracy else 0f,
            ),
        )
        val moving = displacement >= minDisplacement &&
            (if (location.hasSpeed()) location.speed >= MIN_SPEED_MPS
             else displacement / elapsedSeconds >= MIN_SPEED_MPS)
        if (!moving) return null

        movementAnchor = Location(location)
        val travelBearing = if (engine.hasReliableBearing(location)) location.bearing
            else anchor.bearingTo(location)
        val result = engine.findApproachingInRange(
            location,
            qualifies = { camera ->
                val cameraLocation = Location("camera").apply {
                    latitude = camera.latitude
                    longitude = camera.longitude
                }
                cameraKey(camera) !in alerted &&
                    anchor.distanceTo(cameraLocation) - location.distanceTo(cameraLocation) >= MIN_PROGRESS_METERS
            },
            travelBearing = travelBearing,
        ) ?: return null
        alerted[cameraKey(result.first)] = result.first
        return result
    }

    @Synchronized
    fun reset() {
        alerted.clear()
        movementAnchor = null
    }

    private fun isUsable(location: Location): Boolean {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return false
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return ageNanos in 0..MAX_LOCATION_AGE_NANOS
    }

    private fun cameraKey(camera: CameraItem) =
        "${camera.latitude}:${camera.longitude}:${camera.name}"

    companion object {
        // Beyond the largest speed-adjusted alert radius, plus a GPS buffer.
        const val REARM_DISTANCE_METERS = 1_400f
        private const val MIN_PROGRESS_METERS = 3f
        private const val MIN_MOVEMENT_METERS = 10f
        private const val MIN_SPEED_MPS = 0.5f
        private const val MAX_ACCURACY_METERS = 75f
        private const val MAX_SAMPLE_GAP_NANOS = 60_000_000_000L
        private const val MAX_LOCATION_AGE_NANOS = 30_000_000_000L
    }
}
