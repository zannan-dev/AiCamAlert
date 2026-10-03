package com.example.aicamalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.example.aicamalert.location.CameraGeofenceManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Starts and stops continuous radar only while the device is near camera zones. */
class CameraGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_GEOFENCE_TRANSITION) return

        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val triggeredIds = event.triggeringGeofences
            ?.map { it.requestId }
            .orEmpty()
        if (triggeredIds.isEmpty()) return

        val preferences = context.getSharedPreferences(
            CameraGeofenceManager.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val activeIds = preferences.getStringSet(
            CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS,
            emptySet(),
        )?.toMutableSet() ?: mutableSetOf()

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> activeIds.addAll(triggeredIds)
            Geofence.GEOFENCE_TRANSITION_EXIT -> activeIds.removeAll(triggeredIds.toSet())
            else -> return
        }

        preferences.edit {
            putStringSet(CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS, activeIds)
        }

        if (activeIds.isNotEmpty() && preferences.getBoolean("bg_radar_enabled", false)) {
            CameraProximityService.startService(context)
        } else if (preferences.getBoolean("bg_radar_enabled", false)) {
            // Keep the user-started foreground service available for background audio;
            // only GPS tracking pauses outside camera zones. An active alarm keeps ringing.
            CameraProximityService.pauseRadarLocation(context)
        } else {
            CameraProximityService.stopService(context)
        }
    }

    companion object {
        const val ACTION_GEOFENCE_TRANSITION =
            "com.example.aicamalert.ACTION_GEOFENCE_TRANSITION"
    }
}
