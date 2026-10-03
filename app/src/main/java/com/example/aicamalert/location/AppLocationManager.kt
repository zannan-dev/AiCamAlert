package com.example.aicamalert.location

import android.content.Context
import android.location.Location
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared location provider running callbacks on a background HandlerThread.
 *
 * Exposes location as a [StateFlow] consumed by both the ViewModel and Service.
 * Supports foreground (high-accuracy) and background (battery-friendly) profiles.
 */
class AppLocationManager(private val context: Context) {

    enum class LocationProfile {
        /** Active use: high accuracy, frequent updates. */
        FOREGROUND,
        /** Active background radar: high accuracy inside camera zones. */
        BACKGROUND
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val handlerThread = HandlerThread("LocationThread").apply { start() }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { publishLocation(it) }
        }
    }

    @Synchronized
    private fun publishLocation(location: Location) {
        if (location.elapsedRealtimeNanos > (_location.value?.elapsedRealtimeNanos ?: -1L)) {
            _location.value = location
        }
    }

    @Volatile
    private var isUpdating = false
    private var foregroundLocationEnabled = false
    private var radarLocationEnabled = false
    private var currentProfile = LocationProfile.FOREGROUND

    /**
     * Enables continuous updates while the app UI is visible. This is kept
     * separate from radar use so background tracking can fully stop outside a
     * camera geofence without interrupting the map when the user returns.
     */
    @Synchronized
    fun setForegroundLocationEnabled(enabled: Boolean) {
        foregroundLocationEnabled = enabled
        reconcileUpdates()
    }

    /** Enables continuous updates while background radar is active in a cluster. */
    @Synchronized
    fun setRadarLocationEnabled(enabled: Boolean) {
        radarLocationEnabled = enabled
        reconcileUpdates()
    }

    /** Switch GPS profile; restarts updates if currently enabled. */
    @Synchronized
    fun setProfile(profile: LocationProfile) {
        if (profile == currentProfile) return
        currentProfile = profile
        if (shouldUpdate()) {
            stopInternal()
            startInternal()
        }
    }

    private fun shouldUpdate(): Boolean = foregroundLocationEnabled || radarLocationEnabled

    private fun reconcileUpdates() {
        if (shouldUpdate()) startInternal() else stopInternal()
    }

    private fun startInternal() {
        if (isUpdating) return

        val hasFine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) publishLocation(loc)
            }

            val request = when (currentProfile) {
                LocationProfile.FOREGROUND -> LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 5_000L
                )
                    .setMinUpdateIntervalMillis(3_000L)
                    .setMinUpdateDistanceMeters(10f)
                    .build()

                LocationProfile.BACKGROUND -> LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 5_000L
                )
                    .setMinUpdateIntervalMillis(3_000L)
                    .setMinUpdateDistanceMeters(10f)
                    .build()
            }

            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                handlerThread.looper
            ).addOnFailureListener {
                isUpdating = false
                android.util.Log.e("AppLocationManager", "Location updates failed", it)
            }
            isUpdating = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopInternal() {
        if (!isUpdating) return
        fusedClient.removeLocationUpdates(locationCallback)
        isUpdating = false
    }

    /** Release the handler thread. Only call on process teardown. */
    fun destroy() {
        foregroundLocationEnabled = false
        radarLocationEnabled = false
        stopInternal()
        handlerThread.quitSafely()
    }
}
