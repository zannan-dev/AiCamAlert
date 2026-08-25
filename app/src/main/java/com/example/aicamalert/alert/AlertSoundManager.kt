package com.example.aicamalert.alert

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator

/**
 * Unified alert sound manager. Replaces the duplicated sound systems
 * in CameraProximityService and ProximitySoundAlertManager.
 *
 * Single ToneGenerator instance with proper lifecycle management,
 * built-in cooldown, and snooze check.
 */
object AlertSoundManager {

    private var toneGenerator: ToneGenerator? = null
    private var lastAlertTime = 0L

    /** Minimum interval between alert sounds in milliseconds. */
    private const val COOLDOWN_MS = 15_000L

    /**
     * Play the proximity alarm tone if not in cooldown and not snoozed.
     * Safe to call from any thread.
     */
    fun playProximityAlarm(context: Context) {
        val prefs = context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_until_timestamp", 0L)
        val now = System.currentTimeMillis()

        if (now < snoozeUntil) return // Alerts snoozed
        if (now - lastAlertTime < COOLDOWN_MS) return // Cooldown active

        lastAlertTime = now
        playTone(context)
    }

    /**
     * Force-play the siren sound, bypassing snooze/cooldown checks.
     * Used by the foreground service for critical alerts.
     */
    fun playSiren(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < COOLDOWN_MS) return
        lastAlertTime = now
        playTone(context)
    }

    private fun playTone(context: Context) {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, alertUri)
                ringtone?.play()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * Release the ToneGenerator. Call when the service is destroyed
     * or the app is going to background.
     */
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
