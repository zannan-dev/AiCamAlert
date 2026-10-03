package com.example.aicamalert.util

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Centralized permission and settings utility functions. */
object PermissionUtils {

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun isLocationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm?.isLocationEnabled == true
        } else {
            @Suppress("DEPRECATION")
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    fun requestEnableLocation(context: Context) {
        openSettings(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else true
    }

    fun hasNotificationRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean {
        if (!hasNotificationRuntimePermission(context) ||
            !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = context.getSystemService(android.app.NotificationManager::class.java)
                ?.getNotificationChannel(com.example.aicamalert.CameraProximityService.CHANNEL_ID_ALERT)
            if (channel?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun hasFullScreenAlertPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java)
            ?.canUseFullScreenIntent() == true

    fun hasPreciseLocationPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Geofence transitions need "Allow all the time" on Android 10 and newer. */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            hasLocationPermission(context)
        }
    }

    /** Android 11+ has no public deep link directly to this app's location grant. */
    fun requestBackgroundLocationPermission(context: Context) = openAppSettings(context)

    fun appSettingsIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"),
    )

    fun openAppSettings(context: Context) = openSettings(context, appSettingsIntent(context))

    fun notificationSettingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val blockedChannel = manager?.getNotificationChannel(
                com.example.aicamalert.CameraProximityService.CHANNEL_ID_ALERT,
            )?.importance == android.app.NotificationManager.IMPORTANCE_NONE
            return if (androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() && blockedChannel) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, com.example.aicamalert.CameraProximityService.CHANNEL_ID_ALERT)
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
        return appSettingsIntent(context)
    }

    fun requestNotificationSettings(context: Context) = openSettings(context, notificationSettingsIntent(context))

    fun fullScreenSettingsIntent(context: Context) =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}")) else appSettingsIntent(context)

    fun requestFullScreenAlertPermission(context: Context) = openSettings(context, fullScreenSettingsIntent(context))

    fun overlaySettingsIntent(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"))

    fun requestOverlayPermission(context: Context) = openSettings(context, overlaySettingsIntent(context))

    fun batterySettingsIntent(context: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"))

    fun requestBatteryOptimizationExemption(context: Context) = openSettings(context,
        batterySettingsIntent(context), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /** Try the specific settings screen first, then fall back on devices that lack it. */
    private fun openSettings(context: Context, vararg preferred: Intent): Boolean {
        val candidates = preferred.toList() + appSettingsIntent(context) + Intent(Settings.ACTION_SETTINGS)
        for (intent in candidates) {
            try {
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: android.content.ActivityNotFoundException) {
                // Some manufacturers omit or replace individual settings activities.
            } catch (_: SecurityException) {
                // Continue to the public app settings fallback.
            }
        }
        android.widget.Toast.makeText(context, "Open Android Settings → Apps → AiCam Alert to finish setup.",
            android.widget.Toast.LENGTH_LONG).show()
        return false
    }
}
