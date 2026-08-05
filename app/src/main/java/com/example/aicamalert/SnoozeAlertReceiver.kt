package com.example.aicamalert

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class SnoozeAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val prefs = context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)

        when (action) {
            CameraProximityService.ACTION_SNOOZE_ALERTS -> {
                val oneHourMillis = 60 * 60 * 1000L
                val snoozeUntil = System.currentTimeMillis() + oneHourMillis
                prefs.edit().putLong("snooze_until_timestamp", snoozeUntil).apply()

                // Cancel the active heads-up alert notification
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(CameraProximityService.ALERT_NOTIFICATION_ID)

                // Update service foreground notification text & notify service
                CameraProximityService.updateSnoozeState(context)

                Toast.makeText(context, "AiCam Alerts Snoozed for 1 Hour", Toast.LENGTH_SHORT).show()
            }
            CameraProximityService.ACTION_RESUME_ALERTS -> {
                prefs.edit().putLong("snooze_until_timestamp", 0L).apply()

                CameraProximityService.updateSnoozeState(context)

                Toast.makeText(context, "AiCam Radar Alerts Resumed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
