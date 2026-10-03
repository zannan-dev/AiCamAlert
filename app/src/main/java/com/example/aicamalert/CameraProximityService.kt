package com.example.aicamalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import com.example.aicamalert.data.model.CameraItem
import com.example.aicamalert.location.CameraGeofenceManager
import org.json.JSONObject
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aicamalert.alert.AlertSoundManager
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.location.AppForegroundTracker
import com.example.aicamalert.location.AppLocationManager
import com.example.aicamalert.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service for background camera proximity detection.
 *
 * Shares [CameraRepository] and [AppLocationManager] with the UI via
 * [AiCamApplication] — no duplicate GPS or JSON parsing.
 *
 * Owns continuous alarm playback and acknowledgement in both foreground and
 * background. Geofences pause GPS, while the user-started service remains ready
 * for audio playback until radar is disabled.
 */
class CameraProximityService : Service() {

    private lateinit var app: AiCamApplication
    private lateinit var repository: CameraRepository
    private lateinit var locationManager: AppLocationManager
    private lateinit var foregroundTracker: AppForegroundTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationCollectJob: Job? = null
    private var radarTracking = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()

        app = AiCamApplication.get(this)
        repository = app.repository
        locationManager = app.locationManager
        foregroundTracker = app.foregroundTracker

        serviceScope.launch {
            repository.loadCameras()
            if (radarTracking) startRadarTracking()
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                clearSavedAlarm(this)
                AlertSoundManager.stop()
                updateForegroundNotification()
                if (!radarEnabled()) stopSelf()
            }
            ACTION_PAUSE_RADAR -> {
                radarTracking = false
                locationCollectJob?.cancel()
                locationManager.setRadarLocationEnabled(false)
                updateForegroundNotification()
            }
            ACTION_START_ALARM -> {
                val camera = cameraFromIntent(intent)
                beginAlarm(camera)
            }
            else -> {
                radarTracking = intent != null || getSharedPreferences("aicam_prefs", MODE_PRIVATE)
                    .getStringSet(CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS, emptySet()).orEmpty().isNotEmpty()
                updateForegroundNotification()
                restoreSavedAlarm()?.let { beginAlarm(it) }
                if (radarTracking) startRadarTracking()
            }
        }
        return START_STICKY
    }

    private fun radarEnabled() = getSharedPreferences("aicam_prefs", MODE_PRIVATE)
        .getBoolean("bg_radar_enabled", false)

    private fun startRadarTracking() {
        if (!PermissionUtils.hasLocationPermission(this)) return
        locationManager.setRadarLocationEnabled(true)
        if (repository.isLoaded) startCollectingLocation()
    }

    private fun beginAlarm(camera: CameraItem) {
        val saved = JSONObject().put("name", camera.name).put("district", camera.district)
            .put("distance", camera.distance).put("latitude", camera.latitude).put("longitude", camera.longitude)
        getSharedPreferences("aicam_prefs", MODE_PRIVATE).edit().putString(KEY_PENDING_ALARM, saved.toString()).apply()
        // Enter the audio foreground-service state before requesting audio focus.
        promoteToForeground(createAlarmNotification(camera))
        AlertSoundManager.start(this, camera)
    }

    private fun restoreSavedAlarm(): CameraItem? {
        val json = getSharedPreferences("aicam_prefs", MODE_PRIVATE).getString(KEY_PENDING_ALARM, null) ?: return null
        return runCatching {
            val saved = JSONObject(json)
            CameraItem(saved.getString("name"), saved.getString("district"), saved.getString("distance"),
                saved.getDouble("latitude"), saved.getDouble("longitude"))
        }.getOrNull()
    }

    private fun startCollectingLocation() {
        locationCollectJob?.cancel()
        locationCollectJob = serviceScope.launch {
            locationManager.location.collect { location ->
                location?.let { checkCameraProximity(it) }
            }
        }
    }

    private fun updateForegroundNotification() {
        val notification = AlertSoundManager.activeAlert.value?.let { createAlarmNotification(it) }
            ?: createForegroundNotification()
        promoteToForeground(notification)
    }

    private fun promoteToForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Proximity check using spatial grid — O(5–15) instead of O(704).
     * Skips UI alerts when the app is in the foreground.
     */
    private fun checkCameraProximity(userLocation: Location) {
        if (!repository.isLoaded || !radarEnabled()) return

        // App is open — ViewModel handles in-app alerts; avoid duplicate sirens
        if (foregroundTracker.isInForeground.value) return

        val result = app.alertGate.nextAlert(userLocation) ?: return
        val (closestCamera, minDistanceMeters) = result
        val distStr = formatDistance(minDistanceMeters)
        beginAlarm(closestCamera.copy(distance = distStr, distanceMeters = minDistanceMeters))
        launchFullScreenAlert(closestCamera.name, closestCamera.district, distStr)
    }

    private fun formatDistance(distMeters: Double): String {
        return if (distMeters < 1000) {
            "${distMeters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.2f km", distMeters / 1000.0)
        }
    }

    private fun launchFullScreenAlert(cameraName: String, district: String, distance: String) {
        try {
            val alertIntent = Intent(this, FullScreenAlertActivity::class.java).apply {
                putExtra("camera_name", cameraName)
                putExtra("district", district)
                putExtra("distance", distance)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pi = PendingIntent.getActivity(
                this, System.currentTimeMillis().toInt(), alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pi.send()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startActivity(
                    Intent(this, FullScreenAlertActivity::class.java).apply {
                        putExtra("camera_name", cameraName)
                        putExtra("district", district)
                        putExtra("distance", distance)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "AiCam Background Radar Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background camera proximity monitoring status"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "AiCam Speed Camera Proximity Warning",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers lock screen heads-up siren alert when approaching speed cameras"
                // The service owns looping audio/vibration; avoid a second notification chime.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle(if (radarTracking) "AiCam Radar Active" else "AiCam Camera Alerts On")
            .setContentText(if (radarTracking) "Monitoring nearby Kerala MVD speed cameras"
                else "GPS pauses outside camera zones; alerts remain enabled")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)

        return builder.build()
    }

    private fun createAlarmNotification(camera: CameraItem): android.app.Notification {
        val fullScreenIntent = alarmIntent(this, camera, FullScreenAlertActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 4, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 5, Intent(this, CameraProximityService::class.java).setAction(ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("Speed camera ahead")
            .setContentText("${camera.name} · ${camera.distance} away. Tap Stop alarm to acknowledge.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop alarm", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        locationCollectJob?.cancel()
        locationManager.setRadarLocationEnabled(false)
        AlertSoundManager.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var isRunning = false
        const val CHANNEL_ID_SERVICE = "aicam_foreground_service_channel"
        const val CHANNEL_ID_ALERT = "aicam_repeating_alarm_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_ALARM = "com.example.aicamalert.START_CAMERA_ALARM"
        const val ACTION_STOP_ALARM = "com.example.aicamalert.STOP_CAMERA_ALARM"
        const val ACTION_PAUSE_RADAR = "com.example.aicamalert.PAUSE_RADAR_LOCATION"
        private const val KEY_PENDING_ALARM = "pending_camera_alarm"

        private fun alarmIntent(context: Context, camera: CameraItem, target: Class<*>) =
            Intent(context, target).apply {
                putExtra("camera_name", camera.name)
                putExtra("district", camera.district)
                putExtra("distance", camera.distance)
                putExtra("latitude", camera.latitude)
                putExtra("longitude", camera.longitude)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        private fun cameraFromIntent(intent: Intent) = CameraItem(
            name = intent.getStringExtra("camera_name") ?: "AI Speed Camera",
            district = intent.getStringExtra("district") ?: "Kerala",
            distance = intent.getStringExtra("distance") ?: "Nearby",
            latitude = intent.getDoubleExtra("latitude", 0.0),
            longitude = intent.getDoubleExtra("longitude", 0.0),
        )

        fun startAlarm(context: Context, camera: CameraItem) {
            val intent = alarmIntent(context, camera, CameraProximityService::class.java)
                .setAction(ACTION_START_ALARM)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun acknowledgeAlarm(context: Context) {
            // Stop immediately, including when the UI is already visible.
            clearSavedAlarm(context)
            AlertSoundManager.stop()
            context.startService(Intent(context, CameraProximityService::class.java).setAction(ACTION_STOP_ALARM))
        }

        private fun clearSavedAlarm(context: Context) {
            context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE).edit()
                .remove(KEY_PENDING_ALARM).apply()
        }

        fun pauseRadarLocation(context: Context) {
            if (!isRunning) return
            context.startService(Intent(context, CameraProximityService::class.java).setAction(ACTION_PAUSE_RADAR))
        }

        fun startService(context: Context) {
            if (!PermissionUtils.hasNotificationPermission(context)) return

            val intent = Intent(context, CameraProximityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            clearSavedAlarm(context)
            AlertSoundManager.stop()
            val intent = Intent(context, CameraProximityService::class.java)
            context.stopService(intent)
        }
    }
}
