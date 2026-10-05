package com.example.aicamalert

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
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
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusBarPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    var headerHeight by remember { mutableStateOf(statusBarPadding + 72.dp) }
    val navigationPadding = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    var bottomNavigationHeight by remember { mutableStateOf(navigationPadding + 120.dp) }
    val viewModel: CameraViewModel = viewModel()

    // Collect state from ViewModel
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedDistrict by viewModel.selectedDistrict.collectAsState()
    val selectedDistance by viewModel.selectedDistance.collectAsState()
    val isListView by viewModel.isListView.collectAsState()
    val isBackgroundRadarEnabled by viewModel.isBackgroundRadarEnabled.collectAsState()
    val focusedCamera by viewModel.focusedCamera.collectAsState()
    val activeProximityCamera by viewModel.activeProximityCamera.collectAsState()
    val filteredCameras by viewModel.filteredCameras.collectAsState()
    val districtsList by viewModel.districtsList.collectAsState()
    val districtCounts by viewModel.districtCounts.collectAsState()
    val isLocationEnabled by viewModel.isLocationEnabled.collectAsState()
    val userLocation by viewModel.locationManager.location.collectAsState()

    val viewStateHolder = rememberSaveableStateHolder()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRadarEnablePending by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchOverlayHeight by remember { mutableStateOf(56.dp) }
    val searchSafeBottom = with(density) {
        if (WindowInsets.ime.getBottom(this) > 0) 0.dp
        else WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val searchBottomPadding by animateDpAsState(
        targetValue = if (showSearch) searchSafeBottom + 12.dp
            else (bottomNavigationHeight - 28.dp).coerceAtLeast(0.dp),
        animationSpec = spring(dampingRatio = 1f, stiffness = 240f),
        label = "Search panel bottom position",
    )
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

        if (!PermissionUtils.hasPreciseLocationPermission(context)) {
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

    androidx.activity.compose.BackHandler(enabled = activeProximityCamera != null) {
        viewModel.dismissAlertLocally()
    }

    // ── UI Layout ──

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = isListView,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState) 1 else -1
                    (fadeIn(tween(280)) + slideInHorizontally(
                        tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { direction * (it / 12) },
                    )).togetherWith(
                        fadeOut(tween(180)) + slideOutHorizontally(
                            tween(320, easing = FastOutSlowInEasing),
                            targetOffsetX = { -direction * (it / 12) },
                        ),
                    ).using(null)
                },
                label = "Camera view transition",
            ) { listView ->
                viewStateHolder.SaveableStateProvider(if (listView) "list" else "map") {
                    Column(Modifier.fillMaxSize().then(
                        if (listView) Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .imePadding()
                        else Modifier,
                    )) {
                        if (listView) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    // Let cards sit beneath the app bar's 24 dp
                                    // fading edge, with 4 dp after its controls.
                                    top = (headerHeight - 20.dp).coerceAtLeast(statusBarPadding),
                                    bottom = searchBottomPadding + searchOverlayHeight + 12.dp,
                                ),
                            ) {
                                if (filteredCameras.isEmpty()) {
                                    item(key = "empty_state") {
                                        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
                                            EmptyCameraState(onResetFilters = { viewModel.resetFilters() })
                                        }
                                    }
                                } else {
                                    items(filteredCameras, key = { "${it.latitude}_${it.longitude}_${it.name}" }) { camera ->
                                        Box(Modifier.padding(horizontal = 16.dp)) {
                                            CameraCard(camera, isGpsActive = userLocation != null,
                                                onFocusOnMap = {
                                                    showSearch = false
                                                    viewModel.setFocusedCamera(camera)
                                                })
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                CameraMapView(
                                    cameras = filteredCameras,
                                    focusedCamera = focusedCamera,
                                    userLocation = userLocation,
                                    darkTheme = darkTheme,
                                    onRequestLocation = { requestLocationPermission() },
                                    bottomOverlayPadding = bottomNavigationHeight,
                                    topOverlayPadding = headerHeight,
                                )
                            }
                        }
                    }
                }
            }
        }

        HeaderSection(
            radarEnabled = isBackgroundRadarEnabled,
            onRadarToggle = { handleRadarToggle(it) },
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged {
                headerHeight = with(density) { it.height.toDp() }
            },
        )

        AnimatedVisibility(
            visible = !showSearch,
            modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
            enter = fadeIn(tween(200)) + slideInVertically(tween(280), initialOffsetY = { it / 3 }),
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200), targetOffsetY = { it / 3 }),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        bottomNavigationHeight = with(density) { it.height.toDp() }
                    }
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                    )))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ))
                    .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FloatingViewNavigation(
                    isListView = isListView,
                    onSelectView = { listView ->
                        if (!listView) showSearch = false
                        viewModel.setIsListView(listView)
                        if (listView) viewModel.setFocusedCamera(null)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = isListView,
            modifier = Modifier.align(Alignment.BottomCenter)
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, bottom = searchBottomPadding),
            enter = fadeIn(tween(200)) + slideInVertically(tween(280), initialOffsetY = { it / 4 }),
            exit = fadeOut(tween(150)),
        ) {
            FloatingCameraSearch(
                expanded = showSearch,
                onExpandedChange = { showSearch = it },
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                selectedDistrict = selectedDistrict,
                onDistrictChange = { viewModel.setSelectedDistrict(it) },
                districts = districtsList,
                districtCounts = districtCounts,
                selectedDistance = selectedDistance,
                onDistanceChange = { viewModel.setSelectedDistance(it) },
                modifier = Modifier.onSizeChanged {
                    searchOverlayHeight = with(density) { it.height.toDp() }
                },
            )
        }

        // In-App Full Screen Alert Overlay
        AnimatedVisibility(
            visible = activeProximityCamera != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            activeProximityCamera?.let { cam ->
                FullScreenAlertContent(
                    cameraName = cam.name,
                    district = cam.district,
                    distance = cam.distance,
                    darkTheme = darkTheme,
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
