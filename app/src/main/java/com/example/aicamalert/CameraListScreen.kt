package com.example.aicamalert

import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicamalert.ui.components.*
import com.example.aicamalert.util.PermissionUtils
import com.example.aicamalert.viewmodel.CameraViewModel

/**
 * Main screen composable — thin orchestration layer.
 *
 * All state lives in [CameraViewModel]. This composable only collects
 * state flows, wires up callbacks, and delegates to extracted components.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraListScreen(
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CameraViewModel = viewModel()

    // Collect state from ViewModel
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedDistrict by viewModel.selectedDistrict.collectAsState()
    val selectedDistance by viewModel.selectedDistance.collectAsState()
    val sortByDistance by viewModel.sortByDistance.collectAsState()
    val isListView by viewModel.isListView.collectAsState()
    val isBackgroundRadarEnabled by viewModel.isBackgroundRadarEnabled.collectAsState()
    val focusedCamera by viewModel.focusedCamera.collectAsState()
    val isAlertDismissedLocally by viewModel.isAlertDismissedLocally.collectAsState()
    val activeProximityCamera by viewModel.activeProximityCamera.collectAsState()
    val filteredCameras by viewModel.filteredCameras.collectAsState()
    val districtsList by viewModel.districtsList.collectAsState()
    val districtCounts by viewModel.districtCounts.collectAsState()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsState()
    val hasBatteryExemption by viewModel.hasBatteryExemption.collectAsState()
    val isLocationEnabled by viewModel.isLocationEnabled.collectAsState()
    val userLocation by viewModel.locationManager.location.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var isRadarEnablePending by rememberSaveable { mutableStateOf(false) }
    var showBackgroundLocationGuidance by rememberSaveable { mutableStateOf(false) }

    // Refresh permissions on resume
    DisposableEffect(lifecycleOwner) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(listener)
        onDispose { lifecycleOwner.lifecycle.removeObserver(listener) }
    }

    // Permission launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleBackgroundRadar(true)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.onLocationPermissionGranted()
            if (isRadarEnablePending) {
                showBackgroundLocationGuidance = true
            }
        }
        isRadarEnablePending = false
    }

    fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun handleRadarToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.toggleBackgroundRadar(false)
            return
        }

        if (!PermissionUtils.hasLocationPermission(context)) {
            isRadarEnablePending = true
            requestLocationPermission()
            return
        }
        if (!PermissionUtils.hasBackgroundLocationPermission(context)) {
            showBackgroundLocationGuidance = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.toggleBackgroundRadar(true)
    }

    if (showBackgroundLocationGuidance) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationGuidance = false },
            title = { Text("Allow background camera alerts") },
            text = {
                Text(
                    "To detect camera zones while the app is closed, set Location to “Allow all the time” in the next screen: Permissions → Location.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundLocationGuidance = false
                        PermissionUtils.requestBackgroundLocationPermission(context)
                    },
                ) {
                    Text("Open app settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationGuidance = false }) {
                    Text("Not now")
                }
            },
        )
    }

    // ── UI Layout ──

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isListView) {
            // List View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
            ) {
                HeaderSection(
                    darkTheme = darkTheme,
                    radarEnabled = isBackgroundRadarEnabled,
                    onThemeToggle = onThemeToggle,
                    onRadarToggle = { handleRadarToggle(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(isListView) { viewModel.setIsListView(it) }
                Spacer(modifier = Modifier.height(16.dp))

                SearchAndFilters(
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    selectedDistrict = selectedDistrict,
                    onDistrictChange = { viewModel.setSelectedDistrict(it) },
                    districts = districtsList,
                    districtCounts = districtCounts,
                    selectedDistance = selectedDistance,
                    onDistanceChange = { viewModel.setSelectedDistance(it) },
                    sortByDistance = sortByDistance,
                    onSortChange = { viewModel.setSortByDistance(it) }
                )

                if (isBackgroundRadarEnabled) {
                    BackgroundPermissionSetupCard(
                        hasOverlayPermission = hasOverlayPermission,
                        hasBatteryExemption = hasBatteryExemption,
                        onRequestOverlay = { PermissionUtils.requestOverlayPermission(context) },
                        onRequestBatteryExemption = { PermissionUtils.requestBatteryOptimizationExemption(context) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LocationStatusBar(
                    radarEnabled = isBackgroundRadarEnabled,
                    userLocation = userLocation,
                    cameraCount = filteredCameras.size,
                    onRequestPermission = { requestLocationPermission() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredCameras.isEmpty()) {
                    EmptyCameraState(onResetFilters = { viewModel.resetFilters() })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            items = filteredCameras,
                            key = { "${it.latitude}_${it.longitude}_${it.name}" }
                        ) { camera ->
                            CameraCard(
                                camera = camera,
                                isGpsActive = userLocation != null,
                                onFocusOnMap = { viewModel.setFocusedCamera(camera) }
                            )
                        }
                    }
                }
            }
        } else {
            // Map View
            CameraMapView(
                cameras = filteredCameras,
                focusedCamera = focusedCamera,
                userLocation = userLocation,
                darkTheme = darkTheme,
                onRequestLocation = { requestLocationPermission() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
            ) {
                HeaderSection(
                    darkTheme = darkTheme,
                    radarEnabled = isBackgroundRadarEnabled,
                    onThemeToggle = onThemeToggle,
                    onRadarToggle = { handleRadarToggle(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(isListView) {
                    viewModel.setIsListView(it)
                    if (it) viewModel.setFocusedCamera(null)
                }
            }
        }

        // In-App Full Screen Alert Overlay
        AnimatedVisibility(
            visible = activeProximityCamera != null && !isAlertDismissedLocally,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            activeProximityCamera?.let { cam ->
                FullScreenAlertContent(
                    cameraName = cam.name,
                    district = cam.district,
                    distance = cam.distance,
                    onIHaveNoticed = { viewModel.snoozeAlerts() },
                    onDismiss = { viewModel.dismissAlertLocally() }
                )
            }
        }
    }

    // Location disabled dialog
    if (!isLocationEnabled) {
        AlertDialog(
            onDismissRequest = { /* Force user to enable location */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Location Disabled", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "AiCam Alert requires your device's location to be turned on to warn you about nearby cameras. Please turn on location services in your settings.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { PermissionUtils.requestEnableLocation(context) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Turn On Location")
                }
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }
}
