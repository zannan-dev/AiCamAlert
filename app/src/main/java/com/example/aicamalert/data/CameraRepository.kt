package com.example.aicamalert.data

import android.content.Context
import android.location.Location
import com.example.aicamalert.data.model.CameraItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Repository for Kerala AI camera data.
 *
 * Parses camera locations from the bundled JSON asset on a background thread
 * and builds a spatial grid index for efficient proximity lookups.
 *
 * Grid cell size: ~0.01° lat/lon ≈ 1.1km — proximity checks only scan
 * the 9 adjacent cells (3×3), reducing checks from 704 to ~5–15.
 * Optimized on dev branch: HashMap pre-sized to 256 buckets.
 */
class CameraRepository(private val context: Context) {

    private var _cameras: List<CameraItem> = emptyList()
    val cameras: List<CameraItem> get() = _cameras

    /** Spatial grid: maps grid keys → cameras in that cell. */
    private val spatialGrid = HashMap<Long, MutableList<CameraItem>>(256)

    /** Whether the data has been loaded. */
    val isLoaded: Boolean get() = _cameras.isNotEmpty()

    /**
     * Load cameras from the bundled JSON asset on IO thread.
     * Builds the spatial grid index after parsing.
     */
    suspend fun loadCameras() {
        if (_cameras.isNotEmpty()) return

        val loaded = withContext(Dispatchers.IO) {
            try {
                val inputStream = context.assets.open("kerala_ai_cameras.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val list = ArrayList<CameraItem>(jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        CameraItem(
                            name = obj.optString("name", "AI Camera"),
                            district = obj.optString("district", "Kerala"),
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

        _cameras = loaded
        buildSpatialGrid()
    }

    private fun buildSpatialGrid() {
        spatialGrid.clear()
        for (camera in _cameras) {
            spatialGrid.getOrPut(camera.gridKey) { mutableListOf() }.add(camera)
        }
    }

    /**
     * Get cameras in the 3×3 grid neighbourhood around [lat],[lon].
     * Returns only cameras in adjacent ~1.1km cells — typically 5–15 items
     * instead of scanning all 704.
     */
    fun getCamerasNear(lat: Double, lon: Double): List<CameraItem> {
        val centerLatBucket = (lat * 100).toLong()
        val centerLonBucket = (lon * 100).toLong()

        val result = mutableListOf<CameraItem>()
        for (dLat in -1L..1L) {
            for (dLon in -1L..1L) {
                val key = ((centerLatBucket + dLat) shl 32) or ((centerLonBucket + dLon) and 0xFFFFFFFFL)
                spatialGrid[key]?.let { result.addAll(it) }
            }
        }
        return result
    }

    /**
     * Find the nearest camera within [radiusMeters] using the spatial grid.
     * Returns null if no camera is within range.
     */
    fun findNearestCamera(
        lat: Double,
        lon: Double,
        radiusMeters: Double,
        qualifies: (CameraItem) -> Boolean = { true },
    ): Pair<CameraItem, Double>? {
        val nearby = getCamerasNear(lat, lon)
        if (nearby.isEmpty()) return null

        var closest: CameraItem? = null
        var minDist = Double.MAX_VALUE
        val results = FloatArray(1)

        for (camera in nearby) {
            Location.distanceBetween(lat, lon, camera.latitude, camera.longitude, results)
            val dist = results[0].toDouble()
            if (dist <= radiusMeters && dist < minDist && qualifies(camera)) {
                minDist = dist
                closest = camera
            }
        }

        return if (closest != null && minDist <= radiusMeters) {
            Pair(closest, minDist)
        } else null
    }

    /**
     * Compute distances for ALL cameras from the given location in a single pass.
     * Returns a new list with distance fields populated, sorted by distance.
     */
    fun computeAllDistances(lat: Double, lon: Double): List<CameraItem> {
        val results = FloatArray(1)
        return _cameras.map { camera ->
            Location.distanceBetween(lat, lon, camera.latitude, camera.longitude, results)
            val distMeters = results[0].toDouble()
            val distStr = if (distMeters < 1000) {
                "${distMeters.toInt()} m"
            } else {
                String.format(java.util.Locale.US, "%.2f km", distMeters / 1000.0)
            }
            camera.copy(distance = distStr, distanceMeters = distMeters)
        }.sortedBy { it.distanceMeters }
    }
}
