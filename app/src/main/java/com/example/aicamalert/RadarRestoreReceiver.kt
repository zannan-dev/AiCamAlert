package com.example.aicamalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.example.aicamalert.location.CameraGeofenceManager
import com.example.aicamalert.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Restores OS geofences after reboot or replacement of this app's package. */
class RadarRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            CameraGeofenceManager.PREFERENCES_NAME, Context.MODE_PRIVATE,
        )
        // Previous cluster membership is stale after reboot. INITIAL_TRIGGER_ENTER
        // will establish the current membership when geofences are restored.
        preferences.edit { remove(CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS) }
        if (!preferences.getBoolean("bg_radar_enabled", false) ||
            !PermissionUtils.hasBackgroundLocationPermission(appContext) ||
            !PermissionUtils.hasNotificationPermission(appContext)
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    AiCamApplication.get(appContext).geofenceManager.register()
                }
            } catch (e: Exception) {
                Log.e("RadarRestoreReceiver", "Unable to restore camera geofences", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
