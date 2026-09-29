package com.example.locationspoofer.data

import java.io.Serializable
import java.util.UUID

/**
 * Represents a saved location bookmark with metadata.
 */
data class SavedLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 5.0f,
    val icon: String = "📍",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
