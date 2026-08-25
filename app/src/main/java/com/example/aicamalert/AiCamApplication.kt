package com.example.aicamalert

import android.app.Application
import android.content.Context
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.location.AppForegroundTracker
import com.example.aicamalert.location.CameraGeofenceManager
import com.example.aicamalert.location.AppLocationManager
import com.example.aicamalert.location.ProximityEngine

/**
 * Application-scoped container for shared resources.
 *
 * Ensures a single [CameraRepository], [AppLocationManager], and
 * [ProximityEngine] across the UI and background service.
 */
class AiCamApplication : Application() {

    lateinit var repository: CameraRepository
        private set

    lateinit var locationManager: AppLocationManager
        private set

    lateinit var proximityEngine: ProximityEngine
        private set

    lateinit var geofenceManager: CameraGeofenceManager
        private set

    lateinit var foregroundTracker: AppForegroundTracker
        private set

    override fun onCreate() {
        super.onCreate()
        repository = CameraRepository(this)
        locationManager = AppLocationManager(this)
        proximityEngine = ProximityEngine(repository)
        geofenceManager = CameraGeofenceManager(this, repository)
        foregroundTracker = AppForegroundTracker()

        foregroundTracker.start(this) { inForeground ->
            locationManager.setProfile(
                if (inForeground) AppLocationManager.LocationProfile.FOREGROUND
                else AppLocationManager.LocationProfile.BACKGROUND
            )
            locationManager.setForegroundLocationEnabled(inForeground)
        }
    }

    companion object {
        fun get(context: Context): AiCamApplication =
            context.applicationContext as AiCamApplication
    }
}
