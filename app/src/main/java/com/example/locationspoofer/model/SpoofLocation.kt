package com.example.locationspoofer.model

import java.io.Serializable

/**
 * Represents the target mock location coordinates and accuracy.
 */
data class SpoofLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 5.0f
) : Serializable
