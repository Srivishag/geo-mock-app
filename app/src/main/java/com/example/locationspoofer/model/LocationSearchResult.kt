package com.example.locationspoofer.model

import java.io.Serializable

data class LocationSearchResult(
    val title: String,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double
) : Serializable
