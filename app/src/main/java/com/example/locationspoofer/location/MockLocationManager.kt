package com.example.locationspoofer.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import com.example.locationspoofer.model.SpoofLocation

/**
 * Manages Android LocationManager mock/test providers.
 * Registers GPS, Network, Passive, and Fused test providers with comprehensive
 * telemetry attributes to prevent location jumping / rubber-banding.
 */
class MockLocationManager(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Register all providers that system/apps listen to
    private val candidateProviders: List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(LocationManager.FUSED_PROVIDER)
        } else {
            add("fused")
        }
    }

    private val activeProviders = mutableListOf<String>()
    private var isConfigured = false

    /**
     * Configures the test providers on LocationManager.
     */
    fun startMocking(): Result<Unit> {
        activeProviders.clear()
        var hasAtLeastOneProvider = false
        var securityException: SecurityException? = null

        for (provider in candidateProviders) {
            try {
                // Remove existing test provider registration if lingering
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) { }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val properties = ProviderProperties.Builder()
                        .setHasAltitudeSupport(true)
                        .setHasBearingSupport(true)
                        .setHasSpeedSupport(true)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .build()
                    locationManager.addTestProvider(provider, properties)
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        /* requiresNetwork = */ false,
                        /* requiresSatellite = */ true,
                        /* requiresCell = */ false,
                        /* hasMonetaryCost = */ false,
                        /* supportsAltitude = */ true,
                        /* supportsSpeed = */ true,
                        /* supportsBearing = */ true,
                        /* powerRequirement = */ 1,
                        /* accuracy = */ 1
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
                activeProviders.add(provider)
                hasAtLeastOneProvider = true
            } catch (e: SecurityException) {
                securityException = e
            } catch (_: Exception) {
                // Ignore providers not supported on specific hardware/ROMs (e.g. passive/fused on custom ROMs)
            }
        }

        return if (hasAtLeastOneProvider) {
            isConfigured = true
            Result.success(Unit)
        } else if (securityException != null) {
            isConfigured = false
            Result.failure(
                IllegalStateException(
                    "Mock location permission not enabled. Select 'GeoMock - GPS Spoofer' in Developer options -> Select mock location app.",
                    securityException
                )
            )
        } else {
            isConfigured = false
            Result.failure(IllegalStateException("No location providers could be registered as test providers."))
        }
    }

    /**
     * Publishes high-precision synthetic location updates to all active test providers.
     */
    fun publishLocation(spoofLocation: SpoofLocation): Result<Unit> {
        if (!isConfigured || activeProviders.isEmpty()) {
            val configResult = startMocking()
            if (configResult.isFailure) return configResult
        }

        val currentTime = System.currentTimeMillis()
        val currentElapsedNanos = SystemClock.elapsedRealtimeNanos()

        return try {
            for (provider in activeProviders) {
                val mockLocation = Location(provider).apply {
                    latitude = spoofLocation.latitude
                    longitude = spoofLocation.longitude
                    accuracy = spoofLocation.accuracy
                    altitude = 15.0 // Non-zero altitude prevents elevation anomalies in FLP
                    time = currentTime
                    elapsedRealtimeNanos = currentElapsedNanos
                    speed = 0.0f
                    bearing = 0.0f

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 0.1f
                        verticalAccuracyMeters = 0.1f
                        speedAccuracyMetersPerSecond = 0.01f
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        elapsedRealtimeUncertaintyNanos = 0.0
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        isMock = true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mslAltitudeMeters = 15.0
                        mslAltitudeAccuracyMeters = 0.1f
                    }
                }
                locationManager.setTestProviderLocation(provider, mockLocation)
            }
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(
                IllegalStateException(
                    "Mock location permission revoked or app not selected in Developer Options.",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Disables and removes registered test providers cleanly.
     */
    fun stopMocking() {
        for (provider in activeProviders) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) { }
        }
        activeProviders.clear()
        isConfigured = false
    }
}
