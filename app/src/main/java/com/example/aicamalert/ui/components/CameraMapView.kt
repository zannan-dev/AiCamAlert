package com.example.aicamalert.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aicamalert.data.model.CameraItem
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun CameraMapView(
    cameras: List<CameraItem>,
    focusedCamera: CameraItem?,
    userLocation: Location?,
    darkTheme: Boolean,
    onRequestLocation: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

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

    // Camera Pin Marker Icon
    val markerIcon = remember(primaryColor) {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = primaryColor.toArgb()
            isAntiAlias = true
        }

        paint.alpha = 50
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.alpha = 255
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint)

        val iconSize = size / 3
        val cameraIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_camera)
        cameraIcon?.let {
            it.setBounds(
                (size / 2 - iconSize / 2),
                (size / 2 - iconSize / 2),
                (size / 2 + iconSize / 2),
                (size / 2 + iconSize / 2)
            )
            it.setColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
            it.draw(canvas)
        }
        BitmapDrawable(context.resources, bitmap)
    }

    // User Location Indicator Dot
    val userLocationIcon = remember {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = android.graphics.Color.parseColor("#4285F4")
        paint.alpha = 60
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.alpha = 255
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

        paint.color = android.graphics.Color.parseColor("#1A73E8")
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

        BitmapDrawable(context.resources, bitmap)
    }

    // Track previous marker state for diff-based updates
    var prevCameraKeys by remember { mutableStateOf(emptySet<String>()) }
    var prevUserLocStr by remember { mutableStateOf("") }

    // Handle map camera animate when focusedCamera changes
    LaunchedEffect(focusedCamera) {
        if (focusedCamera != null && mapViewRef != null) {
            mapViewRef?.controller?.animateTo(
                GeoPoint(focusedCamera.latitude, focusedCamera.longitude),
                16.0,
                800L
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(aiCamTileSource)
                    setMultiTouchControls(true)

                    val mapBgColor = if (darkTheme) android.graphics.Color.parseColor("#121212") else android.graphics.Color.parseColor("#F5F5F5")
                    setBackgroundColor(mapBgColor)

                    overlayManager.tilesOverlay.loadingBackgroundColor = mapBgColor
                    overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                    overlayManager.tilesOverlay.setColorFilter(
                        if (darkTheme) TilesOverlay.INVERT_COLORS else null
                    )

                    isTilesScaledToDpi = true
                    maxZoomLevel = 19.0
                    minZoomLevel = 3.0

                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                    val initialCenter = if (focusedCamera != null) {
                        GeoPoint(focusedCamera.latitude, focusedCamera.longitude)
                    } else if (userLocation != null) {
                        GeoPoint(userLocation.latitude, userLocation.longitude)
                    } else if (cameras.isNotEmpty()) {
                        GeoPoint(cameras[0].latitude, cameras[0].longitude)
                    } else {
                        GeoPoint(10.8505, 76.2711)
                    }
                    controller.setZoom(if (focusedCamera != null) 16.0 else 13.5)
                    controller.setCenter(initialCenter)

                    mapViewRef = this
                }
            },
            update = { view ->
                val mapBgColor = if (darkTheme) android.graphics.Color.parseColor("#121212") else android.graphics.Color.parseColor("#F5F5F5")
                view.setBackgroundColor(mapBgColor)
                view.overlayManager.tilesOverlay.loadingBackgroundColor = mapBgColor
                view.overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                view.overlayManager.tilesOverlay.setColorFilter(
                    if (darkTheme) TilesOverlay.INVERT_COLORS else null
                )
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
                        }
                        view.overlays.add(marker)
                    }

                    prevCameraKeys = currentCameraKeys
                    prevUserLocStr = currentUserLocStr
                    view.invalidate()
                }

                if (focusedCamera != null) {
                    view.controller.animateTo(
                        GeoPoint(focusedCamera.latitude, focusedCamera.longitude),
                        16.0,
                        500L
                    )
                }
            },
            onRelease = { view ->
                view.onPause()
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                "© OpenStreetMap contributors",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        // Floating Action Buttons on the bottom right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 36.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom In (+)
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out (-)
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            // My Location / Find My Location Button
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
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(28.dp))
            }
        }
    }
}
