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
        /** Background radar: balanced power, less frequent updates. */
        BACKGROUND
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val handlerThread = HandlerThread("LocationThread").apply { start() }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _location.value = it }
        }
    }

    private var isUpdating = false
    private var foregroundLocationEnabled = false
    private var radarLocationEnabled = false
    private var currentProfile = LocationProfile.FOREGROUND

    /**
     * Enables continuous updates while the app UI is visible. This is kept
     * separate from radar use so background tracking can fully stop outside a
     * camera geofence without interrupting the map when the user returns.
     */
    fun setForegroundLocationEnabled(enabled: Boolean) {
        if (foregroundLocationEnabled == enabled) return
        foregroundLocationEnabled = enabled
        reconcileUpdates()
    }

    /** Enables continuous updates while background radar is active in a cluster. */
    fun setRadarLocationEnabled(enabled: Boolean) {
        if (radarLocationEnabled == enabled) return
        radarLocationEnabled = enabled
        reconcileUpdates()
    }

    /** Switch GPS profile; restarts updates if currently enabled. */
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
                if (loc != null) _location.value = loc
            }

            val request = when (currentProfile) {
                LocationProfile.FOREGROUND -> LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 5_000L
                )
                    .setMinUpdateIntervalMillis(3_000L)
                    .setMinUpdateDistanceMeters(20f)
                    .build()

                LocationProfile.BACKGROUND -> LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY, 12_000L
                )
                    .setMinUpdateIntervalMillis(10_000L)
                    .setMinUpdateDistanceMeters(75f)
                    .build()
            }

            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                handlerThread.looper
            )
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
