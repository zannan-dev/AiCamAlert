package com.example.aicamalert.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aicamalert.R
import com.example.aicamalert.data.model.CameraItem
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.events.MapEventsReceiver

@Composable
fun CameraMapView(
    cameras: List<CameraItem>,
    focusedCamera: CameraItem?,
    userLocation: Location?,
    darkTheme: Boolean,
    onRequestLocation: () -> Unit,
    bottomOverlayPadding: Dp = 0.dp,
    topOverlayPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val markerContentColor = MaterialTheme.colorScheme.onPrimary
    val mapBackgroundColor = if (darkTheme) NightMapStyle.backgroundColor
        else MaterialTheme.colorScheme.background.toArgb()
    val mapColorFilter = remember(darkTheme) {
        if (darkTheme) ColorMatrixColorFilter(NightMapStyle.colorMatrix()) else null
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedCameraKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCamera = cameras.firstOrNull { "${it.latitude}_${it.longitude}" == selectedCameraKey }
    BackHandler(enabled = selectedCamera != null) { selectedCameraKey = null }
    // Mutated by map callbacks without recomposing during pan/zoom; saved per tab.
    val viewport = rememberSaveable { doubleArrayOf(Double.NaN, Double.NaN, 13.5) }

    // Handle Map Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep camera pins blue with white icons in both map themes.
    val cameraPinBackground = primaryColor
    val cameraPinForeground = androidx.compose.ui.graphics.Color.White
    val markerIcon = remember(cameraPinBackground, cameraPinForeground) {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = cameraPinBackground.toArgb()
            isAntiAlias = true
        }

        paint.alpha = 50
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.alpha = 255
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint)

        val iconSize = size / 3
        val cameraIcon = ContextCompat.getDrawable(context, R.drawable.ic_map_videocam)
        cameraIcon?.let {
            it.setBounds(
                (size / 2 - iconSize / 2),
                (size / 2 - iconSize / 2),
                (size / 2 + iconSize / 2),
                (size / 2 + iconSize / 2)
            )
            it.setColorFilter(cameraPinForeground.toArgb(), PorterDuff.Mode.SRC_IN)
            it.draw(canvas)
        }
        BitmapDrawable(context.resources, bitmap)
    }

    // User Location Indicator Dot
    val userLocationIcon = remember(primaryColor, markerContentColor) {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = primaryColor.toArgb()
        paint.alpha = 60
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = markerContentColor.toArgb()
        paint.alpha = 255
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

        paint.color = primaryColor.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

        BitmapDrawable(context.resources, bitmap)
    }

    // Track previous marker state for diff-based updates
    var prevCameraKeys by remember(markerIcon) { mutableStateOf(emptySet<String>()) }
    var prevUserLocStr by remember(userLocationIcon) { mutableStateOf("") }

    // Handle map camera animate when focusedCamera changes
    LaunchedEffect(focusedCamera, mapViewRef) {
        if (focusedCamera != null && mapViewRef != null) {
            selectedCameraKey = "${focusedCamera.latitude}_${focusedCamera.longitude}"
            mapViewRef?.controller?.animateTo(
                GeoPoint(focusedCamera.latitude, focusedCamera.longitude),
                16.0,
                800L
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val detailsMaxHeight = (maxHeight - topOverlayPadding - bottomOverlayPadding - 32.dp)
            .coerceAtLeast(96.dp)
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(aiCamTileSource)
                    setMultiTouchControls(true)

                    val mapBgColor = mapBackgroundColor
                    setBackgroundColor(mapBgColor)

                    overlayManager.tilesOverlay.loadingBackgroundColor = mapBgColor
                    overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                    overlayManager.tilesOverlay.setColorFilter(mapColorFilter)

                    isTilesScaledToDpi = true
                    maxZoomLevel = 19.0
                    minZoomLevel = 3.0

                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                    val initialCenter = if (focusedCamera != null) {
                        GeoPoint(focusedCamera.latitude, focusedCamera.longitude)
                    } else if (viewport[0].isFinite() && viewport[1].isFinite()) {
                        GeoPoint(viewport[0], viewport[1])
                    } else if (userLocation != null) {
                        GeoPoint(userLocation.latitude, userLocation.longitude)
                    } else if (cameras.isNotEmpty()) {
                        GeoPoint(cameras[0].latitude, cameras[0].longitude)
                    } else {
                        GeoPoint(10.8505, 76.2711)
                    }
                    controller.setZoom(if (focusedCamera != null) 16.0 else viewport[2])
                    controller.setCenter(initialCenter)
                    val map = this
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            selectedCameraKey = null
                            InfoWindow.closeAllInfoWindowsOn(map)
                            return false
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }))
                    fun saveViewport() {
                        viewport[0] = map.mapCenter.latitude
                        viewport[1] = map.mapCenter.longitude
                        viewport[2] = map.zoomLevelDouble
                    }
                    saveViewport()
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            saveViewport()
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            saveViewport()
                            return false
                        }
                    })

                    mapViewRef = this
                }
            },
            update = { view ->
                val mapBgColor = mapBackgroundColor
                view.setBackgroundColor(mapBgColor)
                view.overlayManager.tilesOverlay.loadingBackgroundColor = mapBgColor
                view.overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                view.overlayManager.tilesOverlay.setColorFilter(mapColorFilter)
                view.invalidate()

                // Diff-based marker update: only recreate if data actually changed
                val currentCameraKeys = cameras.map { "${it.latitude}_${it.longitude}" }.toSet()
                val currentUserLocStr = userLocation?.let { "${it.latitude}_${it.longitude}" } ?: ""

                if (currentCameraKeys != prevCameraKeys || currentUserLocStr != prevUserLocStr) {
                    view.overlays.removeAll { it is Marker }

                    // Add User Location Marker
                    userLocation?.let { loc ->
                        val userMarker = Marker(view).apply {
                            position = GeoPoint(loc.latitude, loc.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = userLocationIcon
                            title = "My Location"
                            snippet = "You are here"
                        }
                        view.overlays.add(userMarker)
                    }

                    // Add Camera Markers
                    cameras.forEach { camera ->
                        val marker = Marker(view).apply {
                            position = GeoPoint(camera.latitude, camera.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = markerIcon
                            title = camera.name
                            snippet = "${camera.district} • ${camera.distance}"
                            infoWindow = null
                            setOnMarkerClickListener { _, tappedMap ->
                                InfoWindow.closeAllInfoWindowsOn(tappedMap)
                                selectedCameraKey = "${camera.latitude}_${camera.longitude}"
                                tappedMap.controller.animateTo(
                                    GeoPoint(camera.latitude, camera.longitude),
                                    tappedMap.zoomLevelDouble,
                                    450L,
                                )
                                true
                            }
                        }
                        view.overlays.add(marker)
                    }

                    prevCameraKeys = currentCameraKeys
                    prevUserLocStr = currentUserLocStr
                    view.invalidate()
                }


            },
            onRelease = { view ->
                view.onPause()
            },
            modifier = Modifier.fillMaxSize()
        )

        Text(
            "© OpenStreetMap",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = Shadow(
                    color = MaterialTheme.colorScheme.background,
                    blurRadius = 6f,
                ),
            ),
            modifier = Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = topOverlayPadding + 8.dp, end = 12.dp)
                .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )

        // Recenter control; map zoom is handled by touch gestures.
        AnimatedVisibility(
            visible = selectedCamera == null,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            FloatingActionButton(
                onClick = {
                    if (userLocation != null) {
                        mapViewRef?.controller?.animateTo(
                            GeoPoint(userLocation.latitude, userLocation.longitude),
                            15.5,
                            800L
                        )
                    } else {
                        onRequestLocation()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = bottomOverlayPadding + 16.dp, end = 20.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(28.dp))
            }
        }

        AnimatedVisibility(
            visible = selectedCamera != null,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, bottom = (bottomOverlayPadding - 28.dp).coerceAtLeast(0.dp)),
            enter = fadeIn(tween(200)) + slideInVertically(
                spring(dampingRatio = 1f, stiffness = 240f), initialOffsetY = { it / 3 }),
            exit = fadeOut(tween(140)) + slideOutVertically(tween(200), targetOffsetY = { it / 4 }),
        ) {
            // Retain the outgoing camera while the dismissal animation completes.
            var displayedCamera by remember { mutableStateOf(selectedCamera) }
            if (selectedCamera != null) displayedCamera = selectedCamera
            displayedCamera?.let { camera ->
                MapCameraDetails(
                    camera = camera,
                    isGpsActive = userLocation != null,
                    onDismiss = { selectedCameraKey = null },
                    onCenter = {
                        mapViewRef?.controller?.animateTo(
                            GeoPoint(camera.latitude, camera.longitude),
                            mapViewRef?.zoomLevelDouble,
                            450L,
                        )
                    },
                    onDirections = {
                        uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=${camera.latitude},${camera.longitude}")
                    },
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = detailsMaxHeight),
                )
            }
        }
    }
}
