package com.safenex.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Ultra High-Precision GNSS / Satellite & Fused Location Engine for SAFENEX.
 * Prioritizes raw GPS satellite constellation fixes, filters out inaccurate cell-tower estimates,
 * and continuously tightens accuracy radius.
 */
class SafenexLocationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_LocationMgr"
        private const val UPDATE_INTERVAL_MS = 1000L // 1 second refresh
        private const val FASTEST_INTERVAL_MS = 500L
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 50.0f
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val nativeLocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val geocoder = Geocoder(context, Locale.getDefault())

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private var isTracking = false
    private var cancellationTokenSource: CancellationTokenSource? = null
    private var bestLocation: Location? = null

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                handleNewLocation(location)
            }
        }
    }

    private val nativeGpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private var gnssCallback: GnssStatus.Callback? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val satCount = status.satelliteCount
                    var usedCount = 0
                    for (i in 0 until satCount) {
                        if (status.usedInFix(i)) usedCount++
                    }
                    Log.d(TAG, "GNSS Satellites: in-view=$satCount, used-in-fix=$usedCount")
                }
            }
        }
    }

    /**
     * Checks if location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine
    }

    /**
     * Checks if location services (GPS / Network) are toggled ON in device settings.
     */
    fun isLocationEnabled(): Boolean {
        val lm = nativeLocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Creates Intent to open system Location Settings if GPS is turned off.
     */
    fun getEnableLocationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Starts active high-accuracy satellite tracking across Fused & raw GNSS hardware.
     */
    @SuppressLint("MissingPermission")
    fun startEmergencyTracking() {
        if (isTracking) return

        if (!hasLocationPermission()) {
            _locationState.value = LocationState.Unavailable("Fine Location permission required for precise GPS.")
            return
        }

        if (!isLocationEnabled()) {
            _locationState.value = LocationState.Unavailable("GPS is disabled. Please turn on Location in Settings.")
        } else {
            _locationState.value = LocationState.Fetching
        }

        isTracking = true
        bestLocation = null

        try {
            // 1. Register GNSS Status Callback to warm up satellite radios
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                nativeLocationManager?.registerGnssStatusCallback(gnssCallback!!, null)
            }

            // 2. Start Raw GPS Hardware Satellite Stream (Direct NMEA / Satellite fixes)
            nativeLocationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        UPDATE_INTERVAL_MS,
                        0f, // 0m distance threshold to capture every single satellite delta
                        nativeGpsListener,
                        Looper.getMainLooper()
                    )
                    Log.i(TAG, "Direct GPS Hardware Provider registered.")
                }
            }

            // 3. Request Immediate Fresh High-Accuracy Fix via FusedClient
            cancellationTokenSource?.cancel()
            val tokenSource = CancellationTokenSource()
            cancellationTokenSource = tokenSource

            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        handleNewLocation(loc)
                    }
                }

            // 4. Configure Ultra High Precision Fused Location Request
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                UPDATE_INTERVAL_MS
            ).apply {
                setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                setMaxUpdateDelayMillis(UPDATE_INTERVAL_MS)
                setMinUpdateDistanceMeters(0f)
                setGranularity(Granularity.GRANULARITY_FINE)
                setWaitForAccurateLocation(true) // Enforce satellite precision
            }.build()

            fusedClient.requestLocationUpdates(
                locationRequest,
                fusedCallback,
                Looper.getMainLooper()
            )

            Log.i(TAG, "High-Precision GPS tracking active (1s interval, Granularity=FINE, WaitForAccurate=true).")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            _locationState.value = LocationState.Unavailable("Location permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking", e)
            _locationState.value = LocationState.Unavailable("Location error: ${e.localizedMessage}")
        }
    }

    /**
     * Stops continuous GPS tracking when returning to SAFE.
     */
    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        cancellationTokenSource?.cancel()
        try {
            fusedClient.removeLocationUpdates(fusedCallback)
            nativeLocationManager?.removeUpdates(nativeGpsListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                nativeLocationManager?.unregisterGnssStatusCallback(gnssCallback!!)
            }
            Log.i(TAG, "High-Precision GPS tracking stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error removing location updates: ${e.message}")
        }
    }

    /**
     * Selects and updates with the highest precision, freshest coordinate fix.
     */
    private fun handleNewLocation(location: Location) {
        val currentBest = bestLocation

        // Discard stale fixes older than 30 seconds
        val fixAgeMs = System.currentTimeMillis() - location.time
        if (fixAgeMs > 30_000) {
            return
        }

        // Precision criteria:
        // 1. If we have no location yet, accept this fix
        // 2. If new fix has better accuracy (lower meters number), always accept
        // 3. If new fix is fresh and accuracy is within good range (< 25m), accept
        val shouldAccept = when {
            currentBest == null -> true
            location.hasAccuracy() && !currentBest.hasAccuracy() -> true
            location.hasAccuracy() && currentBest.hasAccuracy() && location.accuracy < currentBest.accuracy -> true
            location.hasAccuracy() && location.accuracy <= 25.0f && (location.time - currentBest.time > 1500) -> true
            (location.time - currentBest.time > 5000) && location.accuracy <= (currentBest.accuracy + 10.0f) -> true
            else -> false
        }

        if (shouldAccept) {
            bestLocation = location
            scope.launch {
                val resolvedAddress = resolveAddress(location.latitude, location.longitude)
                val safenexLoc = SafenexLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = resolvedAddress,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 10.0f,
                    timestamp = System.currentTimeMillis()
                )
                _locationState.value = LocationState.Available(safenexLoc)
                Log.i(TAG, "High-Precision Fix [±${safenexLoc.accuracy.toInt()}m]: ${safenexLoc.latitude}, ${safenexLoc.longitude} (${safenexLoc.address})")
            }
        }
    }

    /**
     * Resolves human-readable street address from coordinates using Geocoder.
     */
    private suspend fun resolveAddress(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null

                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                formatAddressLines(addresses)
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder lookup failed: ${e.message}")
                null
            }
        }

    private fun formatAddressLines(addresses: List<Address>?): String? {
        if (addresses.isNullOrEmpty()) return null
        val addr = addresses[0]

        val parts = mutableListOf<String>()
        val thoroughfare = addr.thoroughfare
        val subThoroughfare = addr.subThoroughfare
        if (!subThoroughfare.isNullOrBlank() && !thoroughfare.isNullOrBlank()) {
            parts.add("$subThoroughfare $thoroughfare")
        } else if (!thoroughfare.isNullOrBlank()) {
            parts.add(thoroughfare)
        }

        val subLocality = addr.subLocality
        if (!subLocality.isNullOrBlank()) parts.add(subLocality)

        val locality = addr.locality
        if (!locality.isNullOrBlank() && locality != subLocality) parts.add(locality)

        val postalCode = addr.postalCode
        val adminArea = addr.adminArea
        if (!adminArea.isNullOrBlank() && !postalCode.isNullOrBlank()) {
            parts.add("$adminArea $postalCode")
        } else if (!adminArea.isNullOrBlank()) {
            parts.add(adminArea)
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            addr.getAddressLine(0)
        }
    }
}
