package com.example.locationspoofer.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * OpenStreetMap road routing engine integration via OSRM (Open Source Routing Machine).
 * Fetches actual road geometry polyline between coordinates.
 * Strictly avoids straight-line fallbacks.
 */
object RouteManager {

    private const val USER_AGENT = "GeoMock-GPS-Spoofer/1.0 (Android; dev@locationspoofer.org)"
    private const val ERROR_NO_ROUTE = "Unable to find a road route between the selected locations."

    /**
     * Calculates the actual road route between start and end coordinates using OSRM.
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Result<RoadRoute> = withContext(Dispatchers.IO) {
        // Formulate OSRM coordinates: lon,lat;lon,lat
        val urlString = String.format(
            Locale.US,
            "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=false",
            startLon, startLat, endLon, endLat
        )

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("$ERROR_NO_ROUTE (HTTP $responseCode)")
                )
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseText = reader.use { it.readText() }

            val json = JSONObject(responseText)
            val code = json.optString("code", "")
            if (!code.equals("Ok", ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException(ERROR_NO_ROUTE))
            }

            val routesArray = json.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                return@withContext Result.failure(IllegalStateException(ERROR_NO_ROUTE))
            }

            val routeObj = routesArray.getJSONObject(0)
            val distance = routeObj.optDouble("distance", 0.0)
            val duration = routeObj.optDouble("duration", 0.0)

            val geometryObj = routeObj.optJSONObject("geometry")
            val coordinatesArray = geometryObj?.optJSONArray("coordinates")
            if (coordinatesArray == null || coordinatesArray.length() < 2) {
                return@withContext Result.failure(IllegalStateException(ERROR_NO_ROUTE))
            }

            val points = mutableListOf<RoutePoint>()
            for (i in 0 until coordinatesArray.length()) {
                val coordPair = coordinatesArray.getJSONArray(i)
                val lon = coordPair.getDouble(0)
                val lat = coordPair.getDouble(1)
                points.add(RoutePoint(lat, lon))
            }

            if (points.size < 2 || distance <= 0.0) {
                return@withContext Result.failure(IllegalStateException(ERROR_NO_ROUTE))
            }

            Result.success(
                RoadRoute(
                    points = points,
                    totalDistanceMeters = distance,
                    estimatedDurationSeconds = duration
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(e.message?.takeIf { it.isNotBlank() } ?: ERROR_NO_ROUTE, e)
            )
        } finally {
            connection?.disconnect()
        }
    }
}
