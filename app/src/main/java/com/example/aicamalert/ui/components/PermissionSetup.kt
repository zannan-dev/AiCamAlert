package com.example.aicamalert.ui.components

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aicamalert.CameraProximityService
import com.example.aicamalert.alert.AlertSoundManager
import com.example.aicamalert.util.PermissionUtils
import com.example.aicamalert.util.RequiredPermissionState
import com.example.aicamalert.util.RequiredSetupStep

private fun Context.activity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/** Rechecks actual Android grants instead of trusting a saved onboarding flag. */
@Composable
fun PermissionSetupGate(
    onSetupCompleted: () -> Unit,
    readPermissions: (Context) -> RequiredPermissionState = RequiredPermissionState::read,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preferences = remember { context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(readPermissions(context)) }
    var setupComplete by remember { mutableStateOf(preferences.getBoolean("permission_setup_complete", false)) }
    var preciseRequested by rememberSaveable { mutableStateOf(preferences.getBoolean("setup_requested_location", false)) }
    var backgroundRequested by rememberSaveable { mutableStateOf(preferences.getBoolean("setup_requested_background", false)) }
    var notificationsRequested by rememberSaveable { mutableStateOf(preferences.getBoolean("setup_requested_notifications", false)) }
    val pendingAlarm by AlertSoundManager.activeAlert.collectAsState()

    fun refresh() {
        state = readPermissions(context)
        if (!state.isReady && setupComplete) {
            setupComplete = false
            preferences.edit().putBoolean("permission_setup_complete", false).apply()
        }
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        owner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter().apply {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val backgroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val activity = context.activity()
    val locationNeedsSettings = preciseRequested && activity?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
    } == true
    val backgroundNeedsSettings = Build.VERSION.SDK_INT >= 30 ||
        (backgroundRequested && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } == true)
    val notificationsNeedSettings = PermissionUtils.hasNotificationRuntimePermission(context) ||
        (notificationsRequested && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true)

    if (state.isReady && setupComplete) {
        content()
    } else {
        PermissionSetupScreen(
            state = state,
            actionLabel = when (state.nextMissing) {
                RequiredSetupStep.PRECISE_LOCATION -> if (locationNeedsSettings) "Open location permissions" else null
                RequiredSetupStep.NOTIFICATIONS -> if (notificationsNeedSettings) "Open notification settings" else null
                else -> null
            },
            hasActiveAlarm = pendingAlarm != null,
            onStopAlarm = { CameraProximityService.acknowledgeAlarm(context) },
            onNext = {
                // Grants can change while the app is visible (for example through quick settings).
                refresh()
                when (state.nextMissing) {
                    RequiredSetupStep.PRECISE_LOCATION -> {
                        if (locationNeedsSettings) PermissionUtils.openAppSettings(context)
                        else {
                            preciseRequested = true
                            preferences.edit().putBoolean("setup_requested_location", true).apply()
                            locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }
                    RequiredSetupStep.BACKGROUND_LOCATION -> {
                        if (Build.VERSION.SDK_INT == 29 && !backgroundNeedsSettings) {
                            backgroundRequested = true
                            preferences.edit().putBoolean("setup_requested_background", true).apply()
                            backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else PermissionUtils.requestBackgroundLocationPermission(context)
                    }
                    RequiredSetupStep.NOTIFICATIONS -> {
                        if (Build.VERSION.SDK_INT >= 33 && !notificationsNeedSettings) {
                            notificationsRequested = true
                            preferences.edit().putBoolean("setup_requested_notifications", true).apply()
                            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else PermissionUtils.requestNotificationSettings(context)
                    }
                    RequiredSetupStep.LOCATION_SERVICES -> PermissionUtils.requestEnableLocation(context)
                    RequiredSetupStep.FULL_SCREEN_ALERTS -> PermissionUtils.requestFullScreenAlertPermission(context)
                    RequiredSetupStep.OVERLAY -> PermissionUtils.requestOverlayPermission(context)
                    RequiredSetupStep.BATTERY -> PermissionUtils.requestBatteryOptimizationExemption(context)
                    null -> {
                        onSetupCompleted()
                        preferences.edit().putBoolean("permission_setup_complete", true).apply()
                        setupComplete = true
                    }
                }
            },
        )
    }
}

@Composable
fun PermissionSetupScreen(
    state: RequiredPermissionState,
    onNext: () -> Unit,
    actionLabel: String? = null,
    hasActiveAlarm: Boolean = false,
    onStopAlarm: () -> Unit = {},
) {
    val step = state.nextMissing
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Icon(Icons.Default.Shield, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Set up camera alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Complete these required settings before using AiCam Alert. They help camera alarms work while driving and when the app is closed.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("${state.completed.size} of ${RequiredSetupStep.entries.size} ready", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.completed.size.toFloat() / RequiredSetupStep.entries.size },
                        modifier = Modifier.fillMaxWidth())
                    if (hasActiveAlarm) {
                        OutlinedButton(onClick = onStopAlarm, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text("Stop current alarm")
                        }
                    }
                }
                items(RequiredSetupStep.entries, key = { it.name }) { required ->
                    val ready = required in state.completed
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            if (ready) "Ready" else "Required", tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(required.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (required == step) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(step?.title ?: "Ready to go", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(step?.explanation ?: "All required access is enabled. Finish setup to turn on camera alerts.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(actionLabel ?: step?.action ?: "Finish setup & enable alerts")
                    }
                }
            }
        }
    }
}
