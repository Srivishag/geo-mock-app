package com.example.locationspoofer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistent storage for saved location bookmarks across application sessions.
 */
class LocationRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("locationspoofer_prefs", Context.MODE_PRIVATE)

    private val _savedLocations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val savedLocations: StateFlow<List<SavedLocation>> = _savedLocations.asStateFlow()

    init {
        loadSavedLocations()
    }

    private fun loadSavedLocations() {
        val jsonStr = prefs.getString(KEY_SAVED_LOCATIONS, null)
        if (jsonStr.isNullOrEmpty()) {
            // Populate initial starter locations
            val defaults = getStarterLocations()
            _savedLocations.value = defaults
            persist(defaults)
            return
        }

        val list = mutableListOf<SavedLocation>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SavedLocation(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        latitude = obj.optDouble("latitude"),
                        longitude = obj.optDouble("longitude"),
                        accuracy = obj.optDouble("accuracy", 5.0).toFloat(),
                        icon = obj.optString("icon", "📍"),
                        description = obj.optString("description", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) { }

        _savedLocations.value = if (list.isEmpty()) getStarterLocations() else list
    }

    fun saveLocation(location: SavedLocation) {
        val current = _savedLocations.value.toMutableList()
        val index = current.indexOfFirst { it.id == location.id }
        if (index >= 0) {
            current[index] = location
        } else {
            current.add(0, location)
        }
        persist(current)
    }

    fun deleteLocation(id: String) {
        val current = _savedLocations.value.filter { it.id != id }
        persist(current)
    }

    fun resetToDefaults() {
        val defaults = getStarterLocations()
        persist(defaults)
    }

    private fun persist(locations: List<SavedLocation>) {
        val jsonArray = JSONArray()
        for (loc in locations) {
            val obj = JSONObject().apply {
                put("id", loc.id)
                put("name", loc.name)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("accuracy", loc.accuracy.toDouble())
                put("icon", loc.icon)
                put("description", loc.description)
                put("timestamp", loc.timestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_LOCATIONS, jsonArray.toString()).apply()
        _savedLocations.value = locations
    }

    companion object {
        private const val KEY_SAVED_LOCATIONS = "saved_locations_json"

        fun getStarterLocations(): List<SavedLocation> = listOf(
            SavedLocation(
                id = "preset_times_square",
                name = "Times Square, NYC",
                latitude = 40.7580,
                longitude = -73.9855,
                accuracy = 5.0f,
                icon = "🗽",
                description = "New York, USA"
            ),
            SavedLocation(
                id = "preset_eiffel_tower",
                name = "Eiffel Tower",
                latitude = 48.8584,
                longitude = 2.2945,
                accuracy = 4.0f,
                icon = "🗼",
                description = "Paris, France"
            ),
            SavedLocation(
                id = "preset_shibuya",
                name = "Shibuya Crossing",
                latitude = 35.6595,
                longitude = 139.7005,
                accuracy = 5.0f,
                icon = "🗾",
                description = "Tokyo, Japan"
            ),
            SavedLocation(
                id = "preset_golden_gate",
                name = "Golden Gate Bridge",
                latitude = 37.8199,
                longitude = -122.4783,
                accuracy = 6.0f,
                icon = "🌉",
                description = "San Francisco, USA"
            ),
            SavedLocation(
                id = "preset_marina_bay",
                name = "Marina Bay Sands",
                latitude = 1.2838,
                longitude = 103.8591,
                accuracy = 5.0f,
                icon = "🌴",
                description = "Singapore"
            ),
            SavedLocation(
                id = "preset_big_ben",
                name = "Big Ben",
                latitude = 51.5007,
                longitude = -0.1246,
                accuracy = 5.0f,
                icon = "🏛️",
                description = "London, UK"
            )
        )
    }
}
