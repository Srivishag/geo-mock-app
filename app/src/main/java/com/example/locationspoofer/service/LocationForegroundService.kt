package com.example.locationspoofer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.locationspoofer.MainActivity
import com.example.locationspoofer.location.MockLocationManager
import com.example.locationspoofer.model.SpoofLocation
import com.example.locationspoofer.route.RoadRoute
import com.example.locationspoofer.route.RouteProgress
import com.example.locationspoofer.route.RouteSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

sealed class ServiceStatus {
    object Stopped : ServiceStatus()
    data class Running(val location: SpoofLocation) : ServiceStatus()
    data class SimulatingRoute(
        val route: RoadRoute,
        val progress: RouteProgress,
        val isPaused: Boolean = false
    ) : ServiceStatus()
    data class RouteCompleted(val route: RoadRoute) : ServiceStatus()
    data class Error(val message: String) : ServiceStatus()
}

/**
 * Foreground Service responsible for publishing mock locations continuously
 * (both static fixed coordinates and dynamic road-following movement simulations)
 * and maintaining an ongoing status notification.
 */
class LocationForegroundService : Service() {

    private lateinit var mockLocationManager: MockLocationManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var publishingJob: Job? = null
    private var simulator: RouteSimulator? = null
    private var isSimulationPaused = false

    companion object {
        const val ACTION_START = "com.example.locationspoofer.action.START"
        const val ACTION_START_ROUTE = "com.example.locationspoofer.action.START_ROUTE"
        const val ACTION_PAUSE_ROUTE = "com.example.locationspoofer.action.PAUSE_ROUTE"
        const val ACTION_RESUME_ROUTE = "com.example.locationspoofer.action.RESUME_ROUTE"
        const val ACTION_STOP = "com.example.locationspoofer.action.STOP"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ACCURACY = "extra_accuracy"
        const val EXTRA_ROUTE = "extra_route"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"

        private const val CHANNEL_ID = "mock_location_service_channel"
        private const val NOTIFICATION_ID = 1001

        private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Stopped)
        val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

        fun startService(context: Context, location: SpoofLocation) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATITUDE, location.latitude)
                putExtra(EXTRA_LONGITUDE, location.longitude)
                putExtra(EXTRA_ACCURACY, location.accuracy)
            }
            startServiceIntent(context, intent)
        }

        fun startRouteSimulation(context: Context, route: RoadRoute, speedKmh: Float, accuracy: Float = 5.0f) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_ROUTE
                putExtra(EXTRA_ROUTE, route)
                putExtra(EXTRA_SPEED_KMH, speedKmh)
                putExtra(EXTRA_ACCURACY, accuracy)
            }
            startServiceIntent(context, intent)
        }

        fun pauseRouteSimulation(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_PAUSE_ROUTE
            }
            context.startService(intent)
        }

        fun resumeRouteSimulation(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_RESUME_ROUTE
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private fun startServiceIntent(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mockLocationManager = MockLocationManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val acc = intent.getFloatExtra(EXTRA_ACCURACY, 5.0f)
                val spoofLocation = SpoofLocation(lat, lon, acc)
                startMockingLocation(spoofLocation)
            }
            ACTION_START_ROUTE -> {
                @Suppress("DEPRECATION")
                val route = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_ROUTE, RoadRoute::class.java)
                } else {
                    intent.getSerializableExtra(EXTRA_ROUTE) as? RoadRoute
                }

                val speedKmh = intent.getFloatExtra(EXTRA_SPEED_KMH, 20f)
                val accuracy = intent.getFloatExtra(EXTRA_ACCURACY, 5.0f)

                if (route != null && route.points.size >= 2) {
                    startMockingRoute(route, speedKmh, accuracy)
                } else {
                    _serviceStatus.value = ServiceStatus.Error("Invalid road route geometry.")
                    stopSelf()
                }
            }
            ACTION_PAUSE_ROUTE -> {
                isSimulationPaused = true
                val currentStatus = _serviceStatus.value
                if (currentStatus is ServiceStatus.SimulatingRoute) {
                    _serviceStatus.value = currentStatus.copy(isPaused = true)
                    updateNotification(buildRouteNotification(currentStatus.route, currentStatus.progress, isPaused = true))
                }
            }
            ACTION_RESUME_ROUTE -> {
                isSimulationPaused = false
                val currentStatus = _serviceStatus.value
                if (currentStatus is ServiceStatus.SimulatingRoute) {
                    _serviceStatus.value = currentStatus.copy(isPaused = false)
                    updateNotification(buildRouteNotification(currentStatus.route, currentStatus.progress, isPaused = false))
                }
            }
            ACTION_STOP -> {
                stopMockingLocation()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMockingLocation(spoofLocation: SpoofLocation) {
        val notification = buildStaticNotification(spoofLocation)
        if (!startForegroundServiceInternal(notification)) return

        val startResult = mockLocationManager.startMocking()
        if (startResult.isFailure) {
            val errorMsg = startResult.exceptionOrNull()?.message
                ?: "Failed to initialize mock location provider."
            _serviceStatus.value = ServiceStatus.Error(errorMsg)
            stopSelf()
            return
        }

        publishingJob?.cancel()
        simulator = null
        publishingJob = serviceScope.launch {
            _serviceStatus.value = ServiceStatus.Running(spoofLocation)
            while (isActive) {
                val pubResult = mockLocationManager.publishLocation(spoofLocation)
                if (pubResult.isFailure) {
                    val errorMsg = pubResult.exceptionOrNull()?.message
                        ?: "Failed to publish mock location."
                    _serviceStatus.value = ServiceStatus.Error(errorMsg)
                    stopMockingLocation()
                    break
                }
                delay(500L)
            }
        }
    }

    private fun startMockingRoute(route: RoadRoute, speedKmh: Float, accuracy: Float) {
        val sim = RouteSimulator(route, speedKmh)
        simulator = sim
        isSimulationPaused = false

        val initialProgress = sim.advance(0.0)
        val notification = buildRouteNotification(route, initialProgress, isPaused = false)
        if (!startForegroundServiceInternal(notification)) return

        val startResult = mockLocationManager.startMocking()
        if (startResult.isFailure) {
            val errorMsg = startResult.exceptionOrNull()?.message
                ?: "Failed to initialize mock location provider."
            _serviceStatus.value = ServiceStatus.Error(errorMsg)
            stopSelf()
            return
        }

        publishingJob?.cancel()
        publishingJob = serviceScope.launch {
            val stepSeconds = 0.5 // 500ms update interval (2 Hz)
            val stepMillis = (stepSeconds * 1000).toLong()

            while (isActive) {
                if (!isSimulationPaused) {
                    val progress = sim.advance(stepSeconds)
                    val spoofLocation = SpoofLocation(
                        latitude = progress.currentPoint.latitude,
                        longitude = progress.currentPoint.longitude,
                        accuracy = accuracy
                    )

                    val pubResult = mockLocationManager.publishLocation(
                        spoofLocation = spoofLocation,
                        bearing = progress.bearing,
                        speedMps = progress.speedMps
                    )

                    if (pubResult.isFailure) {
                        val errorMsg = pubResult.exceptionOrNull()?.message
                            ?: "Failed to publish route mock location."
                        _serviceStatus.value = ServiceStatus.Error(errorMsg)
                        stopMockingLocation()
                        break
                    }

                    if (progress.isCompleted) {
                        _serviceStatus.value = ServiceStatus.RouteCompleted(route)
                        updateNotification(buildRouteCompletedNotification(route))
                        break
                    } else {
                        _serviceStatus.value = ServiceStatus.SimulatingRoute(
                            route = route,
                            progress = progress,
                            isPaused = false
                        )
                        updateNotification(buildRouteNotification(route, progress, isPaused = false))
                    }
                } else {
                    // While paused, keep injecting the current position to prevent rubber-banding
                    val currentPos = sim.computePositionAtDistance(sim.distanceTraveledMeters)
                    mockLocationManager.publishLocation(
                        spoofLocation = SpoofLocation(currentPos.latitude, currentPos.longitude, accuracy),
                        bearing = sim.currentBearing,
                        speedMps = 0f
                    )
                }

                delay(stepMillis)
            }
        }
    }

    private fun startForegroundServiceInternal(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            _serviceStatus.value = ServiceStatus.Error("Foreground service start failed: ${e.localizedMessage}")
            stopSelf()
            false
        }
    }

    private fun updateNotification(notification: Notification) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun stopMockingLocation() {
        publishingJob?.cancel()
        publishingJob = null
        simulator = null
        mockLocationManager.stopMocking()
        _serviceStatus.value = ServiceStatus.Stopped
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Spoofer Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when mock location is actively broadcasting coordinates"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildStaticNotification(location: SpoofLocation): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Static Mock Location Active")
            .setContentText("Lat: ${location.latitude}, Lon: ${location.longitude} (±${location.accuracy}m)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(activityPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Mocking",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildRouteNotification(route: RoadRoute, progress: RouteProgress, isPaused: Boolean): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = ACTION_RESUME_ROUTE
            }
            val resumePendingIntent = PendingIntent.getService(
                this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        } else {
            val pauseIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = ACTION_PAUSE_ROUTE
            }
            val pausePendingIntent = PendingIntent.getService(
                this, 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        }

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusPrefix = if (isPaused) "⏸ PAUSED" else "🚗 SIMULATING ROAD ROUTE"
        val percent = (progress.progressFraction * 100).toInt()
        val remainingKm = progress.remainingDistanceMeters / 1000.0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$statusPrefix ($percent%)")
            .setContentText(String.format(Locale.US, "%.1f km/h | Remaining: %.2f km / %.2f km", progress.speedKmh, remainingKm, route.distanceKm))
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(activityPendingIntent)
            .addAction(pauseResumeAction)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildRouteCompletedNotification(route: RoadRoute): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏁 Road Simulation Completed")
            .setContentText(String.format(Locale.US, "Reached destination. Total distance: %.2f km", route.distanceKm))
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setAutoCancel(true)
            .setContentIntent(activityPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        publishingJob?.cancel()
        serviceScope.cancel()
        mockLocationManager.stopMocking()
        if (_serviceStatus.value !is ServiceStatus.Stopped) {
            _serviceStatus.value = ServiceStatus.Stopped
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
