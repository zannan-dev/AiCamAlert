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
        val notification = createForegroundNotification("Monitoring 704 Kerala MVD speed cameras in background")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun startLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000L
            ).setMinUpdateIntervalMillis(1500L).build()

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
            val now = System.currentTimeMillis()
            // Alert if new camera or 10 seconds elapsed since last alert
            if (now - lastAlertTime > 10000 || lastAlertCameraName != closestCamera.name) {
                lastAlertTime = now
                lastAlertCameraName = closestCamera.name

                val distStr = if (minDistanceMeters < 1000) {
                    "${minDistanceMeters.toInt()} m"
                } else {
                    String.format(java.util.Locale.US, "%.2f km", minDistanceMeters / 1000.0)
                }

                playAlarmSound()
                showHeadsUpProximityNotification(closestCamera.name, closestCamera.district, distStr)
            }
        }
    }

    private fun playAlarmSound() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
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

            // High Priority Proximity Alert Channel
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "AiCam Speed Camera Proximity Warning",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers sound and heads-up alert when approaching speed cameras"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(contentText: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("AiCam Radar Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showHeadsUpProximityNotification(cameraName: String, district: String, distance: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("⚠️ AI SPEED CAMERA AHEAD!")
            .setContentText("$cameraName ($district) is $distance away. Slow down!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
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

        fun startService(context: Context) {
            val intent = Intent(context, CameraProximityService::class.java)
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
