package com.example.locationspoofer.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Utility to query real device GPS / Network location safely.
 */
object DeviceLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? = withContext(Dispatchers.Main) {
        if (!hasLocationPermission(context)) {
            return@withContext null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // 1. Try last known location first for instantaneous response
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        if (bestLocation != null && System.currentTimeMillis() - bestLocation.time < 120_000) {
            return@withContext Pair(bestLocation.latitude, bestLocation.longitude)
        }

        // 2. Request single active location update
        return@withContext suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            var resumed = false

            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val executor = ContextCompat.getMainExecutor(context)
                val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    LocationManager.GPS_PROVIDER
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    LocationManager.NETWORK_PROVIDER
                } else {
                    LocationManager.PASSIVE_PROVIDER
                }

                try {
                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        executor
                    ) { location ->
                        if (!resumed) {
                            resumed = true
                            if (location != null) {
                                continuation.resume(Pair(location.latitude, location.longitude))
                            } else if (bestLocation != null) {
                                continuation.resume(Pair(bestLocation.latitude, bestLocation.longitude))
                            } else {
                                continuation.resume(null)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(bestLocation?.let { Pair(it.latitude, it.longitude) })
                    }
                }
            } else {
                // Fallback for older API levels
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!resumed) {
                            resumed = true
                            locationManager.removeUpdates(this)
                            continuation.resume(Pair(location.latitude, location.longitude))
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        LocationManager.NETWORK_PROVIDER
                    }
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                    }
                } catch (e: Exception) {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(bestLocation?.let { Pair(it.latitude, it.longitude) })
                    }
                }
            }
        }
    }
}
