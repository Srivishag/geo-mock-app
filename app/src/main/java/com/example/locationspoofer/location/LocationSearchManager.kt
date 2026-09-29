package com.example.locationspoofer.location

import com.example.locationspoofer.model.LocationSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles geocoding search for places and addresses via OpenStreetMap Nominatim,
 * as well as direct parsing of pasted coordinates.
 */
object LocationSearchManager {

    suspend fun search(query: String): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Direct coordinate match (e.g., pasted "13.0827, 80.2707")
        val directCoords = CoordinateParser.parse(trimmed)
        if (directCoords != null) {
            val (lat, lon) = directCoords
            return@withContext listOf(
                LocationSearchResult(
                    title = "Coordinates: $lat, $lon",
                    subtitle = "Direct coordinate match",
                    latitude = lat,
                    longitude = lon
                )
            )
        }

        // 2. OpenStreetMap Nominatim Geocoding search
        val results = mutableListOf<LocationSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val urlString =
                "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "LocationSpoofer/1.0 (Android; dev@locationspoofer.org)")
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val name = item.optString("name").ifEmpty { item.optString("display_name") }
                    val displayName = item.optString("display_name")
                    val lat = item.optDouble("lat")
                    val lon = item.optDouble("lon")
                    results.add(
                        LocationSearchResult(
                            title = name,
                            subtitle = displayName,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Network or parsing issue
        }

        results
    }
}
