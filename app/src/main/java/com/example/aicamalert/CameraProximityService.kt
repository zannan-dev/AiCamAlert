package com.example.aicamalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class CameraProximityService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var cameras: List<CameraItem> = emptyList()

    private var toneGenerator: ToneGenerator? = null
    private var lastAlertTime = 0L
    private var lastAlertCameraName = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        cameras = loadKeralaCameras(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    checkCameraProximity(location)
                }
            }
        }
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_UPDATE_SNOOZE) {
            updateForegroundNotification()
            return START_STICKY
        }

        updateForegroundNotification()
        return START_STICKY
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

    private fun startLocationUpdates() {
        try {
            // Swiggy-style battery optimized location updates: 5s interval, 20m distance threshold
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000L
            )
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(20f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun checkCameraProximity(userLocation: Location) {
        if (cameras.isEmpty()) return

        // Check if alerts are snoozed by the user ("I HAVE NOTICED THIS")
        val prefs = getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_until_timestamp", 0L)
        val now = System.currentTimeMillis()

        if (now < snoozeUntil) {
            // Alerts are snoozed for 1 hour
            updateForegroundNotification()
            return
        }

        var closestCamera: CameraItem? = null
        var minDistanceMeters = Double.MAX_VALUE

        val results = FloatArray(1)
        for (camera in cameras) {
            Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                camera.latitude, camera.longitude,
                results
            )
            val dist = results[0].toDouble()
            if (dist < minDistanceMeters) {
                minDistanceMeters = dist
                closestCamera = camera
            }
        }

        if (closestCamera != null && minDistanceMeters <= 500.0) {
            // Alert if new camera zone entered or 15 seconds elapsed
            if (now - lastAlertTime > 15000 || lastAlertCameraName != closestCamera.name) {
                lastAlertTime = now
                lastAlertCameraName = closestCamera.name

                val distStr = if (minDistanceMeters < 1000) {
                    "${minDistanceMeters.toInt()} m"
                } else {
                    String.format(java.util.Locale.US, "%.2f km", minDistanceMeters / 1000.0)
                }

                playSirenSound()
                showLockScreenHeadsUpNotification(closestCamera.name, closestCamera.district, distStr)
            }
        }
    }

    private fun playSirenSound() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            // Loud siren alarm type sound
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(this, alertUri)
                ringtone?.play()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Foreground Service Channel (Low importance for persistent status bar)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "AiCam Background Radar Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background camera proximity monitoring status"
            }

            // High Priority Proximity Alert Channel with Lock Screen Heads-Up Banner
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

        // Full Screen Pay Alert Activity Intent for Lock Screen
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

        // Lock screen "I HAVE NOTICED THIS" 1-Hour Snooze Action
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
        toneGenerator?.release()
        toneGenerator = null
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
            val intent = Intent(context, CameraProximityService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateSnoozeState(context: Context) {
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
