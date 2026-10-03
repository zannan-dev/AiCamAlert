package com.example.aicamalert.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.example.aicamalert.R
import com.example.aicamalert.data.model.CameraItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Playback is owned by the radar foreground service, never by an Activity. */
object AlertSoundManager {
    private val _activeAlert = MutableStateFlow<CameraItem?>(null)
    val activeAlert = _activeAlert.asStateFlow()
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    // System interruptions pause playback without acknowledging the camera.
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        synchronized(this) {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> if (_activeAlert.value != null) player?.start()
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
            }
        }
    }

    @Synchronized
    fun start(context: Context, camera: CameraItem) {
        _activeAlert.value = camera
        if (player != null) return // A second camera updates the warning without restarting playback.
        try {
            audioManager = context.getSystemService(AudioManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(focusListener).build()
                audioManager?.requestAudioFocus(focusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            player = MediaPlayer()
            player!!.apply {
                setAudioAttributes(attributes)
                context.resources.openRawResourceFd(R.raw.camera_alarm).use { asset ->
                    setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
                isLooping = true
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        } catch (error: Exception) {
            Log.e("AlertSoundManager", "Unable to play camera alarm", error)
            player?.release()
            player = null
        }
        @Suppress("DEPRECATION")
        val deviceVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator = deviceVibrator
        val pattern = longArrayOf(0, 600, 300, 600, 900)
        if (Build.VERSION.SDK_INT >= 33) {
            deviceVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0),
                android.os.VibrationAttributes.Builder().setUsage(android.os.VibrationAttributes.USAGE_ALARM).build())
        } else if (Build.VERSION.SDK_INT >= 26) {
            @Suppress("DEPRECATION")
            deviceVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
        } else {
            @Suppress("DEPRECATION")
            deviceVibrator?.vibrate(pattern, 0, attributes)
        }
    }

    /** Explicit acknowledgement or turning off radar ends audio and vibration. */
    @Synchronized
    fun stop() {
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(focusListener)
        }
        focusRequest = null
        audioManager = null
        _activeAlert.value = null
    }

    @Synchronized
    fun isPlaying(): Boolean = player?.isPlaying == true
}
