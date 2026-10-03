package com.example.aicamalert.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.aicamalert.CameraGeofenceReceiver
import com.example.aicamalert.data.CameraRepository
import com.example.aicamalert.data.model.CameraItem
import com.example.aicamalert.util.PermissionUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos

/**
 * Registers low-power geofences around groups of cameras.
 *
 * The operating system evaluates these regions without keeping our process or a
 * continuous location request alive. Entering a region starts the active radar;
 * leaving all active regions stops it again.
 */
class CameraGeofenceManager(
    private val context: Context,
    private val repository: CameraRepository,
) {

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            Intent(context, CameraGeofenceReceiver::class.java).apply {
                action = CameraGeofenceReceiver.ACTION_GEOFENCE_TRANSITION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
    }

    /**
     * Rebuild and register the geofence set. Geofencing is only available with
     * the user's "Allow all the time" location grant on Android 10+.
     */
    suspend fun register() {
        if (!hasGeofencingPermission() || !isRadarEnabled()) return

        repository.loadCameras()
        val geofences = CameraClusterer.create(repository.cameras).map { cluster ->
            Geofence.Builder()
                .setRequestId(cluster.id)
                .setCircularRegion(
                    cluster.latitude,
                    cluster.longitude,
                    cluster.radiusMeters,
                )
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setLoiteringDelay(0)
                .build()
        }

        if (geofences.isEmpty()) return

        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_ACTIVE_CLUSTER_IDS)
        }

        try {
            geofencingClient.removeGeofences(geofencePendingIntent).awaitCompletion()
            // The user may turn radar off or revoke a grant while removal is pending.
            if (!hasGeofencingPermission() || !isRadarEnabled()) return
            geofencingClient.addGeofences(
                GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(geofences)
                    .build(),
                geofencePendingIntent,
            ).awaitCompletion()
        } catch (e: SecurityException) {
            Log.w("CameraGeofenceManager", "Location permission was revoked during registration", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CameraGeofenceManager", "Unable to register camera geofences", e)
        }
    }

    private fun isRadarEnabled(): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean("bg_radar_enabled", false)

    // Await the actual Play services operation so a boot receiver can keep its
    // asynchronous broadcast alive until registration finishes.
    private suspend fun Task<Void>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            when {
                task.isCanceled -> continuation.cancel()
                task.isSuccessful -> continuation.resume(Unit)
                else -> continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Geofence operation failed"),
                )
            }
        }
    }

    /** Remove all geofences and clear any remembered in-zone state. */
    fun unregister() {
        geofencingClient.removeGeofences(geofencePendingIntent)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_ACTIVE_CLUSTER_IDS)
        }
    }

    private fun hasGeofencingPermission(): Boolean {
        val hasFineLocation =
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        return hasFineLocation && PermissionUtils.hasBackgroundLocationPermission(context)
    }

    companion object {
        const val PREFERENCES_NAME = "aicam_prefs"
        const val KEY_ACTIVE_CLUSTER_IDS = "active_camera_cluster_ids"
        private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 601
    }
}

/** A round operating-system geofence that covers a small cluster of cameras. */
data class CameraCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
)

/**
 * Keeps the registration below Android's 100-geofence limit by grouping cameras
 * into 0.2° cells. If future data exceeds the limit, the cell size grows until
 * the set fits. The final circle includes every camera in the cell plus a
 * 2.5 km activation buffer, giving active radar time to start before an alert.
 */
object CameraClusterer {
    private const val MAX_GEOFENCES = 100
    private const val INITIAL_CELL_SIZE_DEGREES = 0.2
    private const val CELL_GROWTH_FACTOR = 1.5
    private const val MAX_CLUSTERING_PASSES = 10
    private const val ACTIVATION_BUFFER_METERS = 2_500.0
    private const val MIN_RADIUS_METERS = 3_000.0

    fun create(cameras: List<CameraItem>): List<CameraCluster> {
        var cellSize = INITIAL_CELL_SIZE_DEGREES
        repeat(MAX_CLUSTERING_PASSES) {
            val clusters = createForCellSize(cameras, cellSize)
            if (clusters.size <= MAX_GEOFENCES) return clusters
            cellSize *= CELL_GROWTH_FACTOR
        }
        return createForCellSize(cameras, cellSize)
    }

    private fun createForCellSize(
        cameras: List<CameraItem>,
        cellSize: Double,
    ): List<CameraCluster> {
        return cameras
            .groupBy { camera ->
                val latBucket = kotlin.math.floor(camera.latitude / cellSize).toInt()
                val lonBucket = kotlin.math.floor(camera.longitude / cellSize).toInt()
                "$latBucket:$lonBucket"
            }
            .map { (cellId, members) ->
                val centerLat = members.map { it.latitude }.average()
                val centerLon = members.map { it.longitude }.average()
                val furthestCameraMeters = members.maxOf { camera ->
                    distanceMeters(centerLat, centerLon, camera.latitude, camera.longitude)
                }
                CameraCluster(
                    id = "camera-cluster-$cellId",
                    latitude = centerLat,
                    longitude = centerLon,
                    radiusMeters = (furthestCameraMeters + ACTIVATION_BUFFER_METERS)
                        .coerceAtLeast(MIN_RADIUS_METERS)
                        .toFloat(),
                )
            }
            .sortedBy { it.id }
    }

    private fun distanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Double {
        val latitudeMeters = (toLat - fromLat) * 111_320.0
        val longitudeMeters = (toLon - fromLon) * 111_320.0 * cos(Math.toRadians(fromLat))
        return kotlin.math.hypot(latitudeMeters, longitudeMeters)
    }
}
