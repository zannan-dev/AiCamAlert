package com.example.aicamalert.util

import android.content.Context

enum class RequiredSetupStep(val title: String, val explanation: String, val action: String) {
    PRECISE_LOCATION("Precise location", "Choose Precise and allow location while using the app. If Settings opens, select Permissions → Location.", "Allow precise location"),
    BACKGROUND_LOCATION("Background location", "Select Permissions → Location → Allow all the time. Keep Precise location enabled so alerts work while the app is closed.", "Allow background location"),
    NOTIFICATIONS("Notifications", "Allow notifications, including Speed Camera Proximity Warning, so every alarm has a visible Stop alarm action.", "Allow notifications"),
    LOCATION_SERVICES("Device location", "Turn on your phone’s Location switch to receive GPS fixes while travelling.", "Turn on location"),
    FULL_SCREEN_ALERTS("Full-screen alerts", "Allow full-screen notifications so camera warnings can appear when your phone is locked.", "Allow full-screen alerts"),
    OVERLAY("Display over other apps", "Choose AiCam Alert if Android shows an app list, then enable Allow display over other apps.", "Allow display over apps"),
    BATTERY("Background battery access", "Allow AiCam Alert to run without battery optimisation so background radar and alarms can keep running.", "Allow background activity"),
}

data class RequiredPermissionState(val completed: Set<RequiredSetupStep>) {
    val isReady: Boolean get() = completed.containsAll(RequiredSetupStep.entries)
    val nextMissing: RequiredSetupStep? get() = RequiredSetupStep.entries.firstOrNull { it !in completed }

    companion object {
        fun read(context: Context): RequiredPermissionState = RequiredPermissionState(buildSet {
            if (PermissionUtils.hasPreciseLocationPermission(context)) add(RequiredSetupStep.PRECISE_LOCATION)
            if (PermissionUtils.hasBackgroundLocationPermission(context)) add(RequiredSetupStep.BACKGROUND_LOCATION)
            if (PermissionUtils.hasNotificationPermission(context)) add(RequiredSetupStep.NOTIFICATIONS)
            if (PermissionUtils.isLocationServicesEnabled(context)) add(RequiredSetupStep.LOCATION_SERVICES)
            if (PermissionUtils.hasFullScreenAlertPermission(context)) add(RequiredSetupStep.FULL_SCREEN_ALERTS)
            if (PermissionUtils.isOverlayPermissionGranted(context)) add(RequiredSetupStep.OVERLAY)
            if (PermissionUtils.isBatteryOptimizationIgnored(context)) add(RequiredSetupStep.BATTERY)
        })
    }
}
