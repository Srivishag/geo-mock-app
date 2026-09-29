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

sealed class ServiceStatus {
    object Stopped : ServiceStatus()
    data class Running(val location: SpoofLocation) : ServiceStatus()
    data class Error(val message: String) : ServiceStatus()
}

/**
 * Foreground Service responsible for publishing mock locations continuously
 * and maintaining an ongoing status notification.
 */
class LocationForegroundService : Service() {

    private lateinit var mockLocationManager: MockLocationManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var publishingJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.locationspoofer.action.START"
        const val ACTION_STOP = "com.example.locationspoofer.action.STOP"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ACCURACY = "extra_accuracy"

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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
        val notification = buildNotification(spoofLocation)

        try {
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
        } catch (e: Exception) {
            _serviceStatus.value = ServiceStatus.Error("Foreground service start failed: ${e.localizedMessage}")
            stopSelf()
            return
        }

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
                // High-frequency injection (500ms / 2 Hz) ensures mock fixes dominate
                // the Android fused location cache and prevents rubber-banding
                delay(500L)
            }
        }
    }

    private fun stopMockingLocation() {
        publishingJob?.cancel()
        publishingJob = null
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

    private fun buildNotification(location: SpoofLocation): Notification {
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
            .setContentTitle("Mock Location Active")
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

    override fun onDestroy() {
        super.onDestroy()
        publishingJob?.cancel()
        serviceScope.cancel()
        mockLocationManager.stopMocking()
        if (_serviceStatus.value is ServiceStatus.Running) {
            _serviceStatus.value = ServiceStatus.Stopped
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
