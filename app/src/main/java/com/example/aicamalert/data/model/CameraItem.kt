package com.example.aicamalert.data.model

/**
 * Represents a single AI speed camera location in Kerala.
 *
 * @property gridKey Spatial hash key for O(1) proximity lookup.
 *   Computed from lat/lon at ~1.1km resolution (0.01° grid cells).
 */
data class CameraItem(
    val name: String,
    val district: String,
    val distance: String = "-- km",
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double = Double.MAX_VALUE
) {
    /** Spatial grid key: buckets cameras into ~1.1km cells. */
    val gridKey: Long
        get() {
            val latBucket = (latitude * 100).toLong()
            val lonBucket = (longitude * 100).toLong()
            // Pack two 32-bit ints into a single Long for efficient HashMap key
            return (latBucket shl 32) or (lonBucket and 0xFFFFFFFFL)
        }
}
