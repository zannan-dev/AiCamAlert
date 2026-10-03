package com.example.aicamalert.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicamalert.AiCamApplication
import com.example.aicamalert.CameraProximityService
import com.example.aicamalert.alert.AlertSoundManager
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.data.model.CameraItem
import com.example.aicamalert.location.AppLocationManager
import com.example.aicamalert.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the main camera list/map screen.
 *
 * Owns all state, handles filtering/sorting in a single pass,
 * and manages the location/proximity lifecycle. Survives configuration changes.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val app = AiCamApplication.get(application)
    private val context: Context get() = getApplication()

    // Shared app-scoped dependencies (single instance)
    val repository: CameraRepository = app.repository
    val locationManager: AppLocationManager = app.locationManager

    // ── UI State ──

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDistrict = MutableStateFlow("All Districts")
    val selectedDistrict: StateFlow<String> = _selectedDistrict.asStateFlow()

    private val _selectedDistance = MutableStateFlow("All")
    val selectedDistance: StateFlow<String> = _selectedDistance.asStateFlow()

    private val _sortByDistance = MutableStateFlow(true)
    val sortByDistance: StateFlow<Boolean> = _sortByDistance.asStateFlow()

    private val _isListView = MutableStateFlow(false)
    val isListView: StateFlow<Boolean> = _isListView.asStateFlow()

    private val _focusedCamera = MutableStateFlow<CameraItem?>(null)
    val focusedCamera: StateFlow<CameraItem?> = _focusedCamera.asStateFlow()

    // ── Radar State ──

    private val prefs = context.getSharedPreferences("aicam_prefs", Context.MODE_PRIVATE)

    private val _isBackgroundRadarEnabled = MutableStateFlow(prefs.getBoolean("bg_radar_enabled", false))
    val isBackgroundRadarEnabled: StateFlow<Boolean> = _isBackgroundRadarEnabled.asStateFlow()

    // ── Permission State ──

    private val _hasOverlayPermission = MutableStateFlow(PermissionUtils.isOverlayPermissionGranted(context))
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _hasBatteryExemption = MutableStateFlow(PermissionUtils.isBatteryOptimizationIgnored(context))
    val hasBatteryExemption: StateFlow<Boolean> = _hasBatteryExemption.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(PermissionUtils.isLocationServicesEnabled(context))
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    // ── Computed State ──

    private val _camerasWithDistance = MutableStateFlow<List<CameraItem>>(emptyList())

    val activeProximityCamera: StateFlow<CameraItem?> = AlertSoundManager.activeAlert

    private val _districtCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val districtCounts: StateFlow<Map<String, Int>> = _districtCounts.asStateFlow()

    private val _districtsList = MutableStateFlow<List<String>>(listOf("All Districts"))
    val districtsList: StateFlow<List<String>> = _districtsList.asStateFlow()

    val filteredCameras: StateFlow<List<CameraItem>> = combine(
        _camerasWithDistance, _searchQuery, _selectedDistrict, _selectedDistance, _sortByDistance
    ) { cameras, query, district, distance, byDistance ->
        filterAndSort(cameras, query, district, distance, byDistance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Distance recompute throttling ──
    private var lastDistComputeMs = 0L
    private var lastDistLat = 0.0
    private var lastDistLon = 0.0

    companion object {
        private const val DIST_RECOMPUTE_INTERVAL_MS = 12_000L
        private const val DIST_RECOMPUTE_MIN_MOVE_METERS = 100f
    }

    // ── Fallback location (Kozhikode, Kerala) ──
    private val fallbackLocation = Location("fallback").apply {
        latitude = 11.5233
        longitude = 75.6133
    }

    init {
        // Never present radar as active when its required background grant has
        // been removed in Android settings.
        if (_isBackgroundRadarEnabled.value && !hasRequiredRadarPermissions()) {
            toggleBackgroundRadar(false)
        }

        viewModelScope.launch {
            repository.loadCameras()
            val counts = repository.cameras.groupingBy { it.district }.eachCount()
            _districtCounts.value = counts
            _districtsList.value = listOf("All Districts") + counts.keys.sorted()
            recomputeDistances(locationManager.location.value ?: fallbackLocation)
            locationManager.location.value?.let { checkProximity(it) }
        }

        viewModelScope.launch {
            locationManager.location.collect { location ->
                val effectiveLoc = location ?: fallbackLocation
                maybeRecomputeDistances(effectiveLoc)
                checkProximity(effectiveLoc)
            }
        }

        if (_isBackgroundRadarEnabled.value) {
            registerCameraGeofencesIfAllowed()
        }
    }

    /**
     * Geofences activate location tracking near cameras. The user-started
     * radar service stays available for alarm audio while GPS pauses outside zones.
     */
    private fun registerCameraGeofencesIfAllowed() {
        if (!hasRequiredRadarPermissions()) return
        viewModelScope.launch {
            app.geofenceManager.register()
        }
    }

    private fun hasRequiredRadarPermissions(): Boolean {
        return PermissionUtils.hasPreciseLocationPermission(context) &&
            PermissionUtils.hasBackgroundLocationPermission(context) &&
            PermissionUtils.hasNotificationPermission(context)
    }

    // ── Actions ──

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSelectedDistrict(district: String) { _selectedDistrict.value = district }
    fun setSelectedDistance(distance: String) { _selectedDistance.value = distance }
    fun setSortByDistance(byDistance: Boolean) { _sortByDistance.value = byDistance }

    fun setIsListView(listView: Boolean) {
        _isListView.value = listView
        if (listView) {
            locationManager.location.value?.let { recomputeDistances(it) }
        }
    }

    fun setFocusedCamera(camera: CameraItem?) {
        _focusedCamera.value = camera
        if (camera != null) {
            _isListView.value = false
        }
    }

    fun dismissAlertLocally() {
        CameraProximityService.acknowledgeAlarm(context)
    }

    fun toggleBackgroundRadar(enabled: Boolean) {
        _isBackgroundRadarEnabled.value = enabled
        prefs.edit().putBoolean("bg_radar_enabled", enabled).apply()

        if (enabled) {
            if (hasRequiredRadarPermissions()) CameraProximityService.startService(context)
            registerCameraGeofencesIfAllowed()
        } else {
            app.geofenceManager.unregister()
            CameraProximityService.stopService(context)
            app.alertGate.reset()
        }
    }

    fun refreshPermissions() {
        _hasOverlayPermission.value = PermissionUtils.isOverlayPermissionGranted(context)
        _hasBatteryExemption.value = PermissionUtils.isBatteryOptimizationIgnored(context)
        _isLocationEnabled.value = PermissionUtils.isLocationServicesEnabled(context)
        if (_isBackgroundRadarEnabled.value && !hasRequiredRadarPermissions()) {
            toggleBackgroundRadar(false)
            return
        }
        if (_isBackgroundRadarEnabled.value) {
            // Starting while the screen is visible keeps background audio eligible.
            CameraProximityService.startService(context)
            registerCameraGeofencesIfAllowed()
        }
    }

    fun onLocationPermissionGranted() {
        locationManager.setForegroundLocationEnabled(app.foregroundTracker.isInForeground.value)
        if (_isBackgroundRadarEnabled.value) registerCameraGeofencesIfAllowed()
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _selectedDistrict.value = "All Districts"
        _selectedDistance.value = "All"
    }

    // ── Internal ──

    /**
     * Throttle full distance recompute: at most every 12 s or 100 m of movement.
     * Avoids O(704) work on every GPS tick while driving.
     */
    private fun maybeRecomputeDistances(location: Location) {
        val now = System.currentTimeMillis()
        val moved = if (lastDistLat != 0.0 || lastDistLon != 0.0) {
            val r = FloatArray(1)
            Location.distanceBetween(lastDistLat, lastDistLon, location.latitude, location.longitude, r)
            r[0]
        } else {
            Float.MAX_VALUE
        }

        if (now - lastDistComputeMs < DIST_RECOMPUTE_INTERVAL_MS && moved < DIST_RECOMPUTE_MIN_MOVE_METERS) {
            return
        }

        lastDistComputeMs = now
        lastDistLat = location.latitude
        lastDistLon = location.longitude
        recomputeDistances(location)
    }

    private fun recomputeDistances(location: Location) {
        viewModelScope.launch(Dispatchers.Default) {
            if (!repository.isLoaded) return@launch
            val withDist = repository.computeAllDistances(location.latitude, location.longitude)
            _camerasWithDistance.value = withDist
        }
    }

    /**
     * In-app proximity alerts — only when app is in foreground.
     * Background alerts are handled by [CameraProximityService].
     */
    private fun checkProximity(location: Location) {
        if (!repository.isLoaded || !_isBackgroundRadarEnabled.value ||
            !app.foregroundTracker.isInForeground.value) return

        val (camera, distance) = app.alertGate.nextAlert(location) ?: return
        CameraProximityService.startAlarm(context,
            camera.copy(distance = formatDistance(distance), distanceMeters = distance))
    }

    private fun formatDistance(distMeters: Double): String {
        return if (distMeters < 1000) {
            "${distMeters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.2f km", distMeters / 1000.0)
        }
    }

    private fun filterAndSort(
        cameras: List<CameraItem>,
        query: String,
        district: String,
        distance: String,
        byDistance: Boolean
    ): List<CameraItem> {
        var list = cameras.filter { camera ->
            val matchesSearch = query.isEmpty() ||
                camera.name.contains(query, ignoreCase = true) ||
                camera.district.contains(query, ignoreCase = true)

            val matchesDistrict = district == "All Districts" ||
                camera.district.equals(district, ignoreCase = true)

            val distKm = camera.distanceMeters / 1000.0
            val matchesDistance = when (distance) {
                "< 5 km" -> distKm <= 5.0
                "< 10 km" -> distKm <= 10.0
                "< 25 km" -> distKm <= 25.0
                "< 50 km" -> distKm <= 50.0
                else -> true
            }

            matchesSearch && matchesDistrict && matchesDistance
        }

        list = if (byDistance) {
            list.sortedBy { it.distanceMeters }
        } else {
            list.sortedBy { it.district }
        }

        return list
    }
}
