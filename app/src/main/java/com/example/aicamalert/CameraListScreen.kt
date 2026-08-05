package com.example.aicamalert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class CameraItem(
    val name: String,
    val district: String,
    val distance: String,
    val latitude: Double,
    val longitude: Double
)

fun loadKeralaCameras(context: Context): List<CameraItem> {
    return try {
        val inputStream = context.assets.open("kerala_ai_cameras.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<CameraItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                CameraItem(
                    name = obj.optString("name", "AI Camera"),
                    district = obj.optString("district", "Kerala"),
                    distance = "-- km",
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0)
                )
            )
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

fun fetchBestLocation(
    context: Context,
    fusedClient: FusedLocationProviderClient,
    onLocationFound: (Location) -> Unit
) {
    val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return

    try {
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                onLocationFound(loc)
            } else {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = gpsLoc ?: netLoc
                if (best != null) {
                    onLocationFound(best)
                }
            }
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraListScreen(
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedDistrict by remember { mutableStateOf("All Districts") }
    var selectedDistance by remember { mutableStateOf("All") }
    var sortByDistance by remember { mutableStateOf(true) }
    var isListView by remember { mutableStateOf(false) }

    // User Location state
    var userLocation by remember { mutableStateOf<Location?>(null) }
    
    // Load 704 Kerala AI cameras dataset from assets
    val allCameras = remember(context) {
        val loaded = loadKeralaCameras(context)
        if (loaded.isNotEmpty()) loaded else listOf(
            CameraItem("Payyoli Beach road", "Kozhikode", "5.53 km", 11.5233, 75.6133),
            CameraItem("Kizhur", "Kozhikode", "5.58 km", 11.5333, 75.6233),
            CameraItem("Meppayyur", "Kozhikode", "9.56 km", 11.5433, 75.7133),
            CameraItem("Thiruvallur", "Kozhikode", "13.50 km", 11.6233, 75.6533),
            CameraItem("Pannimukku", "Kozhikode", "13.52 km", 11.5533, 75.6433)
        )
    }

    // Dynamic list of unique districts
    val districtsList = remember(allCameras) {
        listOf("All Districts") + allCameras.map { it.district }.distinct().sorted()
    }

    // Fused Location Provider Setup
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchBestLocation(context, fusedLocationClient) { loc -> userLocation = loc }
        }
    }

    // Request Continuous Location Updates
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchBestLocation(context, fusedLocationClient) { loc -> userLocation = loc }
            try {
                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 4000L
                ).setMinUpdateIntervalMillis(2000L).build()

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { userLocation = it }
                        }
                    },
                    android.os.Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Fallback reference location (Kozhikode, Kerala) if user location is pending
    val effectiveLocation = userLocation ?: remember {
        Location("fallback").apply {
            latitude = 11.5233
            longitude = 75.6133
        }
    }

    // Compute distance for all cameras relative to user location (or fallback Kozhikode location)
    val camerasWithDistance = remember(allCameras, userLocation) {
        val refLoc = effectiveLocation
        allCameras.map { camera ->
            val results = FloatArray(1)
            Location.distanceBetween(
                refLoc.latitude, refLoc.longitude,
                camera.latitude, camera.longitude,
                results
            )
            val distMeters = results[0]
            val distStr = if (distMeters < 1000) {
                "${distMeters.toInt()} m"
            } else {
                String.format(java.util.Locale.US, "%.2f km", distMeters / 1000.0)
            }
            camera.copy(distance = distStr)
        }
    }

    // Filter and Sort strictly by proximity (nearest camera first)
    val filteredCameras = remember(searchQuery, selectedDistrict, camerasWithDistance, effectiveLocation, sortByDistance) {
        var list = camerasWithDistance.filter { camera ->
            val matchesSearch = searchQuery.isEmpty() ||
                camera.name.contains(searchQuery, ignoreCase = true) ||
                camera.district.contains(searchQuery, ignoreCase = true)
            val matchesDistrict = selectedDistrict == "All Districts" || camera.district.equals(selectedDistrict, ignoreCase = true)
            matchesSearch && matchesDistrict
        }
        
        if (sortByDistance) {
            list = list.sortedBy { camera ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    effectiveLocation.latitude, effectiveLocation.longitude,
                    camera.latitude, camera.longitude,
                    results
                )
                results[0]
            }
        } else {
            list = list.sortedBy { it.district }
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Content: List or Map
        if (isListView) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
            ) {
                HeaderSection(darkTheme, onThemeToggle)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ToggleRow(isListView) { isListView = it }

                Spacer(modifier = Modifier.height(16.dp))

                SearchAndFilters(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    selectedDistrict = selectedDistrict,
                    onDistrictChange = { selectedDistrict = it },
                    districts = districtsList,
                    selectedDistance = selectedDistance,
                    sortByDistance = sortByDistance,
                    onSortChange = { sortByDistance = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Location Status Bar
                LocationStatusBar(
                    userLocation = userLocation,
                    cameraCount = filteredCameras.size,
                    onRequestPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Camera List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredCameras) { camera ->
                        CameraCard(camera)
                    }
                }
            }
        } else {
            // Map View fills the screen
            CameraMapView(
                cameras = filteredCameras,
                userLocation = userLocation,
                darkTheme = darkTheme,
                onRequestLocation = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
            
            // Overlays for Map
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
            ) {
                HeaderSection(darkTheme, onThemeToggle)
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(isListView) { isListView = it }
            }
        }
    }
}

@Composable
fun LocationStatusBar(
    userLocation: Location?,
    cameraCount: Int,
    onRequestPermission: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                if (userLocation != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { if (userLocation == null) onRequestPermission() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (userLocation != null) Icons.Default.MyLocation else Icons.Default.LocationOff,
                contentDescription = null,
                tint = if (userLocation != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (userLocation != null) "Sorted by distance to your location" else "GPS offline • Showing relative distances (Tap to acquire)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "$cameraCount cams",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HeaderSection(darkTheme: Boolean, onThemeToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "AiCam Alert (Kerala)",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onThemeToggle) {
            Icon(
                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Theme",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ToggleRow(isListView: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        SegmentedToggle(
            options = listOf("Map" to Icons.Default.Map, "List" to Icons.Default.FormatListBulleted),
            selectedOption = if (isListView) "List" else "Map",
            onOptionSelected = { onToggle(it == "List") }
        )
    }
}

@Composable
fun SearchAndFilters(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedDistrict: String,
    onDistrictChange: (String) -> Unit,
    districts: List<String>,
    selectedDistance: String,
    sortByDistance: Boolean,
    onSortChange: (Boolean) -> Unit
) {
    Column {
        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("Search by camera or district", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // District and Distance Dropdowns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterDropdown(
                label = "District",
                selectedOption = selectedDistrict,
                options = districts,
                onOptionSelected = onDistrictChange,
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = "Distance",
                selectedOption = selectedDistance,
                options = listOf("All", "< 5 km", "< 10 km", "< 25 km"),
                onOptionSelected = { /* Distance filter */ },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sort Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SortSegmentedToggle(
                selected = if (sortByDistance) "By Distance" else "By District",
                onSelected = { onSortChange(it == "By Distance") }
            )
        }
    }
}

@Composable
fun CameraMapView(
    cameras: List<CameraItem>,
    userLocation: Location?,
    darkTheme: Boolean,
    onRequestLocation: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Multi-subdomain CartoDB Dark Matter tile source for fast parallel loading & high contrast place names
    val darkTileSource = remember {
        XYTileSource(
            "CartoDark",
            0, 20, 256, ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/dark_all/",
                "https://b.basemaps.cartocdn.com/dark_all/",
                "https://c.basemaps.cartocdn.com/dark_all/",
                "https://d.basemaps.cartocdn.com/dark_all/"
            ),
            "© OpenStreetMap contributors © CARTO"
        )
    }

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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    val tileSource = if (darkTheme) darkTileSource else TileSourceFactory.MAPNIK
                    setTileSource(tileSource)
                    setMultiTouchControls(true)
                    
                    isTilesScaledToDpi = true
                    maxZoomLevel = 21.0
                    minZoomLevel = 3.0
                    
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    
                    val initialCenter = if (userLocation != null) {
                        GeoPoint(userLocation.latitude, userLocation.longitude)
                    } else if (cameras.isNotEmpty()) {
                        GeoPoint(cameras[0].latitude, cameras[0].longitude)
                    } else {
                        GeoPoint(10.8505, 76.2711)
                    }
                    controller.setZoom(13.5)
                    controller.setCenter(initialCenter)
                    
                    mapViewRef = this
                }
            },
            update = { view ->
                val targetTileSource = if (darkTheme) darkTileSource else TileSourceFactory.MAPNIK
                if (view.tileProvider.tileSource != targetTileSource) {
                    view.setTileSource(targetTileSource)
                }
                
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
                view.invalidate()
            },
            onRelease = { view ->
                view.onPause()
            },
            modifier = Modifier.fillMaxSize()
        )

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

@Composable
fun SegmentedToggle(
    options: List<Pair<String, ImageVector>>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (text, icon) ->
            val isSelected = selectedOption == text
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onOptionSelected(text) }
                    .padding(vertical = 8.dp, horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun FilterDropdown(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selectedOption, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SortSegmentedToggle(
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        listOf("By Distance", "By District").forEach { text ->
            val isSelected = selected == text
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelected(text) }
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (text == "By District") {
                     Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                     Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CameraCard(camera: CameraItem) {
    val isNearby = remember(camera.distance) {
        camera.distance.contains("m") || (camera.distance.contains("km") && (camera.distance.replace(" km", "").toDoubleOrNull() ?: 999.0) < 5.0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with Glow
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isNearby) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            RoundedCornerShape(14.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (isNearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = camera.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNearby) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "NEARBY",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = camera.district,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            // Distance Badge
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .background(
                        if (isNearby) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (isNearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    tint = if (isNearby) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = camera.distance,
                    color = if (isNearby) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
