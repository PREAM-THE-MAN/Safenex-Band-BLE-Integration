package com.safenex.app.data.location

import java.util.Locale

/**
 * Domain data model representing high-accuracy geographic coordinates and reverse geocoded address.
 */
data class SafenexLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Generates standard Google Maps direct pinned coordinate link.
     */
    val mapsUrl: String
        get() = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)

    /**
     * Formats coordinates into readable degrees.
     */
    val formattedCoordinates: String
        get() {
            val latDir = if (latitude >= 0) "N" else "S"
            val lngDir = if (longitude >= 0) "E" else "W"
            return String.format(Locale.US, "%.4f° %s, %.4f° %s", Math.abs(latitude), latDir, Math.abs(longitude), lngDir)
        }
}

/**
 * State representing GPS location resolution lifecycle.
 */
sealed class LocationState {
    object Idle : LocationState()
    object Fetching : LocationState()
    data class Available(val location: SafenexLocation) : LocationState()
    data class Unavailable(val reason: String) : LocationState()
}
