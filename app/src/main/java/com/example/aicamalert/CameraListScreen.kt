package com.example.aicamalert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val longitude: Double,
    val distanceMeters: Double = 999999.0
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
                } else {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { curLoc ->
                            if (curLoc != null) onLocationFound(curLoc)
                        }
                }
            }
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

object ProximitySoundAlertManager {
    private var toneGenerator: ToneGenerator? = null
    private var lastAlertTime = 0L

    fun playProximityAlarm(context: Context) {
        val prefs = context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)
        val snoozeUntil = prefs.getLong("snooze_until_timestamp", 0L)
        val now = System.currentTimeMillis()

        if (now < snoozeUntil) return // Alerts snoozed for 1 hour

        if (now - lastAlertTime < 15000) return // 15 second cooldown between sound alerts
        lastAlertTime = now

        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
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

    val prefs = remember(context) { context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE) }
    var isBackgroundRadarEnabled by remember { mutableStateOf(prefs.getBoolean("bg_radar_enabled", true)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            CameraProximityService.startService(context)
        }
    }

    fun toggleBackgroundRadar(enabled: Boolean) {
        isBackgroundRadarEnabled = enabled
        prefs.edit().putBoolean("bg_radar_enabled", enabled).apply()
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotifPermission = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasNotifPermission) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            CameraProximityService.startService(context)
        } else {
            CameraProximityService.stopService(context)
        }
    }

    // Camera focused from list item tap
    var focusedCamera by remember { mutableStateOf<CameraItem?>(null) }

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

    // Dynamic list of unique districts with counts
    val districtCounts = remember(allCameras) {
        allCameras.groupingBy { it.district }.eachCount()
    }

    val districtsList = remember(districtCounts) {
        listOf("All Districts") + districtCounts.keys.sorted()
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

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { userLocation = it }
            }
        }
    }

    // Dynamic Location Update Control tied to isBackgroundRadarEnabled
    DisposableEffect(isBackgroundRadarEnabled) {
        if (isBackgroundRadarEnabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                CameraProximityService.startService(context)
            }
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
                        locationCallback,
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
        } else {
            // STOP location updates, stop background service, and clear userLocation when ALERT OFF
            fusedLocationClient.removeLocationUpdates(locationCallback)
            CameraProximityService.stopService(context)
            userLocation = null
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Fallback reference location (Kozhikode, Kerala) if user location is pending
    val effectiveLocation = userLocation ?: remember {
        Location("fallback").apply {
            latitude = 11.5233
            longitude = 75.6133
        }
    }

    // Compute distance for all cameras relative to user location (or fallback location)
    val camerasWithDistance = remember(allCameras, userLocation) {
        val refLoc = effectiveLocation
        allCameras.map { camera ->
            val results = FloatArray(1)
            Location.distanceBetween(
                refLoc.latitude, refLoc.longitude,
                camera.latitude, camera.longitude,
                results
            )
            val distMeters = results[0].toDouble()
            val distStr = if (distMeters < 1000) {
                "${distMeters.toInt()} m"
            } else {
                String.format(java.util.Locale.US, "%.2f km", distMeters / 1000.0)
            }
            camera.copy(
                distance = distStr,
                distanceMeters = distMeters
            )
        }
    }

    // Filter and Sort strictly by proximity (nearest camera first)
    val filteredCameras = remember(searchQuery, selectedDistrict, selectedDistance, camerasWithDistance, effectiveLocation, sortByDistance) {
        var list = camerasWithDistance.filter { camera ->
            val matchesSearch = searchQuery.isEmpty() ||
                camera.name.contains(searchQuery, ignoreCase = true) ||
                camera.district.contains(searchQuery, ignoreCase = true)
            val matchesDistrict = selectedDistrict == "All Districts" || camera.district.equals(selectedDistrict, ignoreCase = true)

            val results = FloatArray(1)
            Location.distanceBetween(
                effectiveLocation.latitude, effectiveLocation.longitude,
                camera.latitude, camera.longitude,
                results
            )
            val distKm = results[0] / 1000.0

            val matchesDistance = when (selectedDistance) {
                "< 5 km" -> distKm <= 5.0
                "< 10 km" -> distKm <= 10.0
                "< 25 km" -> distKm <= 25.0
                "< 50 km" -> distKm <= 50.0
                else -> true
            }

            matchesSearch && matchesDistrict && matchesDistance
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

    // Active proximity camera within 500 meters (only evaluated when ALERT IS ON)
    val activeProximityCamera = remember(camerasWithDistance, userLocation, isBackgroundRadarEnabled) {
        if (isBackgroundRadarEnabled && userLocation != null) {
            val closest = camerasWithDistance.minByOrNull { it.distanceMeters }
            if (closest != null && closest.distanceMeters <= 500.0) {
                closest
            } else null
        } else null
    }

    // Play proximity alarm tone when user gets within 500 meters of a speed camera (ALERT ON only)
    LaunchedEffect(activeProximityCamera, isBackgroundRadarEnabled) {
        if (isBackgroundRadarEnabled && activeProximityCamera != null) {
            ProximitySoundAlertManager.playProximityAlarm(context)
        }
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
                HeaderSection(
                    darkTheme = darkTheme,
                    radarEnabled = isBackgroundRadarEnabled,
                    onThemeToggle = onThemeToggle,
                    onRadarToggle = { toggleBackgroundRadar(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ToggleRow(isListView) { isListView = it }

                Spacer(modifier = Modifier.height(16.dp))

                SearchAndFilters(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    selectedDistrict = selectedDistrict,
                    onDistrictChange = { selectedDistrict = it },
                    districts = districtsList,
                    districtCounts = districtCounts,
                    selectedDistance = selectedDistance,
                    onDistanceChange = { selectedDistance = it },
                    sortByDistance = sortByDistance,
                    onSortChange = { sortByDistance = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Location Status Bar
                LocationStatusBar(
                    radarEnabled = isBackgroundRadarEnabled,
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

                // Camera List with key optimization and empty state UI
                if (filteredCameras.isEmpty()) {
                    EmptyCameraState(
                        onResetFilters = {
                            searchQuery = ""
                            selectedDistrict = "All Districts"
                            selectedDistance = "All"
                        }
                    )
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
                                onFocusOnMap = {
                                    focusedCamera = camera
                                    isListView = false
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Map View fills the screen
            CameraMapView(
                cameras = filteredCameras,
                focusedCamera = focusedCamera,
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
                HeaderSection(
                    darkTheme = darkTheme,
                    radarEnabled = isBackgroundRadarEnabled,
                    onThemeToggle = onThemeToggle,
                    onRadarToggle = { toggleBackgroundRadar(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(isListView) { 
                    isListView = it 
                    if (it) focusedCamera = null
                }
            }
        }
    }
}

@Composable
fun EmptyCameraState(onResetFilters: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No AI Cameras Found",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Try searching for another place or clear your district/distance filters.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onResetFilters,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Filters", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LocationStatusBar(
    radarEnabled: Boolean,
    userLocation: Location?,
    cameraCount: Int,
    onRequestPermission: () -> Unit
) {
    val (dotColor, statusText) = remember(radarEnabled, userLocation) {
        when {
            !radarEnabled -> Pair(
                Color.Gray,
                "Radar Paused • Tap ALERT ON to activate"
            )
            userLocation != null -> Pair(
                Color(0xFF00E5FF),
                "Live Radar Active • Sorted by nearest camera"
            )
            else -> Pair(
                Color(0xFFFFB300),
                "Connecting GPS • Acquiring location fix..."
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                if (radarEnabled && userLocation != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { onRequestPermission() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "$cameraCount items",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun HeaderSection(
    darkTheme: Boolean,
    radarEnabled: Boolean,
    onThemeToggle: () -> Unit,
    onRadarToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        ),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "AiCam Alert",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Kerala MVD Network",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Master Alert ON/OFF Pill Button
            Surface(
                onClick = { onRadarToggle(!radarEnabled) },
                shape = RoundedCornerShape(12.dp),
                color = if (radarEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (radarEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (radarEnabled) Icons.Default.Radar else Icons.Default.PowerSettingsNew,
                        contentDescription = "Radar Alert Toggle",
                        tint = if (radarEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (radarEnabled) "ALERT ON" else "ALERT OFF",
                        color = if (radarEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Dark/Light Theme
            IconButton(onClick = onThemeToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
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
            options = listOf("Map" to Icons.Default.Map, "List" to Icons.AutoMirrored.Filled.FormatListBulleted),
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
    districtCounts: Map<String, Int>,
    selectedDistance: String,
    onDistanceChange: (String) -> Unit,
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
                .clip(RoundedCornerShape(14.dp)),
            placeholder = { Text("Search by camera or district", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true
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
                counts = districtCounts,
                onOptionSelected = onDistrictChange,
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = "Distance Filter",
                selectedOption = selectedDistance,
                options = listOf("All", "< 5 km", "< 10 km", "< 25 km", "< 50 km"),
                counts = emptyMap(),
                onOptionSelected = onDistanceChange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

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
    focusedCamera: CameraItem?,
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
                    val tileSource = if (darkTheme) darkTileSource else TileSourceFactory.MAPNIK
                    setTileSource(tileSource)
                    setMultiTouchControls(true)
                    
                    val mapBgColor = if (darkTheme) android.graphics.Color.parseColor("#121212") else android.graphics.Color.parseColor("#F5F5F5")
                    setBackgroundColor(mapBgColor)
                    
                    overlayManager.tilesOverlay.loadingBackgroundColor = mapBgColor
                    overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                    
                    isTilesScaledToDpi = true
                    maxZoomLevel = 21.0
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
                val targetTileSource = if (darkTheme) darkTileSource else TileSourceFactory.MAPNIK
                if (view.tileProvider.tileSource != targetTileSource) {
                    view.setTileSource(targetTileSource)
                }
                
                val mapBgColor = if (darkTheme) android.graphics.Color.parseColor("#121212") else android.graphics.Color.parseColor("#F5F5F5")
                view.setBackgroundColor(mapBgColor)
                view.overlayManager.tilesOverlay.loadingBackgroundColor = mapBgColor
                view.overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
                
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

                if (focusedCamera != null) {
                    view.controller.animateTo(
                        GeoPoint(focusedCamera.latitude, focusedCamera.longitude),
                        16.0,
                        500L
                    )
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
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (text, icon) ->
            val isSelected = selectedOption == text
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onOptionSelected(text) }
                    .padding(vertical = 8.dp, horizontal = 26.dp),
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
    counts: Map<String, Int>,
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selectedOption, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 340.dp)
            ) {
                options.forEach { option ->
                    val count = counts[option]
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(option, fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal)
                                if (count != null) {
                                    Text("($count)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        },
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
fun CameraCard(
    camera: CameraItem,
    isGpsActive: Boolean = false,
    onFocusOnMap: () -> Unit
) {
    val isNearby = remember(camera.distanceMeters, isGpsActive) {
        isGpsActive && camera.distanceMeters <= 5000.0
    }

    val (distValue, distUnit) = remember(camera.distance) {
        val parts = camera.distance.split(" ")
        if (parts.size >= 2) {
            Pair(parts[0], parts[1].uppercase())
        } else {
            Pair(camera.distance, "")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFocusOnMap() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Icon with Dual Glow
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isNearby) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (isNearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (isNearby) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = camera.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNearby) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "NEARBY",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = camera.district,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Compact Distance HUD Badge
            Surface(
                modifier = Modifier.width(60.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isNearby) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isNearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (isNearby) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = if (isNearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = distValue,
                        color = if (isNearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = distUnit,
                        color = if (isNearby) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
