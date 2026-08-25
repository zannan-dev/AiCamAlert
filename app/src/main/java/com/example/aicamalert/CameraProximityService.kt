package com.example.aicamalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aicamalert.alert.AlertSoundManager
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.location.AppForegroundTracker
import com.example.aicamalert.location.AppLocationManager
import com.example.aicamalert.location.ProximityEngine
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
 * Suppresses sound/notification/full-screen alerts when the app is in foreground
 * (the ViewModel handles in-app alerts in that case).
 */
class CameraProximityService : Service() {

    private lateinit var app: AiCamApplication
    private lateinit var repository: CameraRepository
    private lateinit var proximityEngine: ProximityEngine
    private lateinit var locationManager: AppLocationManager
    private lateinit var foregroundTracker: AppForegroundTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationCollectJob: Job? = null

    private var lastAlertTime = 0L
    private var lastAlertCameraName = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        app = AiCamApplication.get(this)
        repository = app.repository
        proximityEngine = app.proximityEngine
        locationManager = app.locationManager
        foregroundTracker = app.foregroundTracker

        serviceScope.launch {
            repository.loadCameras()
        }

        if (PermissionUtils.hasLocationPermission(this)) {
            locationManager.setRadarLocationEnabled(true)
            startCollectingLocation()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_SNOOZE) {
            updateForegroundNotification()
            return START_STICKY
        }

        updateForegroundNotification()
        return START_STICKY
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
        val prefs = getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_until_timestamp", 0L)
        val now = System.currentTimeMillis()

        val notificationText = if (now < snoozeUntil) {
            val remainingMins = ((snoozeUntil - now) / 60000L).coerceAtLeast(1)
            "Radar Snoozed • Alerts paused for ${remainingMins} mins (Tap Resume to re-enable)"
        } else {
            "AiCam Radar Active • Monitoring 704 Kerala MVD speed cameras"
        }

        val notification = createForegroundNotification(notificationText, isSnoozed = now < snoozeUntil)
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Proximity check using spatial grid — O(5–15) instead of O(704).
     * Skips UI alerts when the app is in the foreground.
     */
    private fun checkCameraProximity(userLocation: Location) {
        if (!repository.isLoaded) return

        val prefs = getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_until_timestamp", 0L)
        val now = System.currentTimeMillis()

        if (now < snoozeUntil) {
            updateForegroundNotification()
            return
        }

        // App is open — ViewModel handles in-app alerts; avoid duplicate sirens
        if (foregroundTracker.isInForeground.value) return

        val result = proximityEngine.findApproachingInRange(userLocation) ?: return
        val (closestCamera, minDistanceMeters) = result

        if (now - lastAlertTime > 15000 || lastAlertCameraName != closestCamera.name) {
            lastAlertTime = now
            lastAlertCameraName = closestCamera.name

            val distStr = formatDistance(minDistanceMeters)

            AlertSoundManager.playSiren(this)
            showLockScreenHeadsUpNotification(closestCamera.name, closestCamera.district, distStr)
            launchFullScreenAlert(closestCamera.name, closestCamera.district, distStr)
        }
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
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(contentText: String, isSnoozed: Boolean): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle(if (isSnoozed) "AiCam Radar Snoozed" else "AiCam Radar Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)

        if (isSnoozed) {
            val resumeIntent = Intent(this, SnoozeAlertReceiver::class.java).apply {
                action = ACTION_RESUME_ALERTS
            }
            val resumePendingIntent = PendingIntent.getBroadcast(
                this, 1, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "RESUME RADAR", resumePendingIntent)
        }

        return builder.build()
    }

    private fun showLockScreenHeadsUpNotification(cameraName: String, district: String, distance: String) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, FullScreenAlertActivity::class.java).apply {
            putExtra("camera_name", cameraName)
            putExtra("district", district)
            putExtra("distance", distance)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 4, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, SnoozeAlertReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALERTS
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, 3, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("⚠️ AI SPEED CAMERA AHEAD!")
            .setContentText("$cameraName ($district) is $distance away. Slow down!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "I HAVE NOTICED THIS (Snooze 1h)",
                snoozePendingIntent
            )
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(ALERT_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCollectJob?.cancel()
        locationManager.setRadarLocationEnabled(false)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID_SERVICE = "aicam_foreground_service_channel"
        const val CHANNEL_ID_ALERT = "aicam_proximity_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2002

        const val ACTION_SNOOZE_ALERTS = "com.example.aicamalert.ACTION_SNOOZE_ALERTS"
        const val ACTION_RESUME_ALERTS = "com.example.aicamalert.ACTION_RESUME_ALERTS"
        const val ACTION_UPDATE_SNOOZE = "com.example.aicamalert.ACTION_UPDATE_SNOOZE"

        fun startService(context: Context) {
            if (!PermissionUtils.hasNotificationPermission(context)) return

            val intent = Intent(context, CameraProximityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateSnoozeState(context: Context) {
            val activeClusters = context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
                .getStringSet("active_camera_cluster_ids", emptySet())
                .orEmpty()
            if (activeClusters.isEmpty()) return

            val intent = Intent(context, CameraProximityService::class.java).apply {
                action = ACTION_UPDATE_SNOOZE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CameraProximityService::class.java)
            context.stopService(intent)
        }
    }
}
