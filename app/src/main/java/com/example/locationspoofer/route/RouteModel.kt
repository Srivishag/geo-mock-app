package com.example.locationspoofer.route

import java.io.Serializable

/**
 * An individual coordinate vertex on an actual road route.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double
) : Serializable

/**
 * Complete road route returned from the road routing engine (OSRM).
 */
data class RoadRoute(
    val points: List<RoutePoint>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double
) : Serializable {
    val distanceKm: Double get() = totalDistanceMeters / 1000.0
    val startPoint: RoutePoint? get() = points.firstOrNull()
    val endPoint: RoutePoint? get() = points.lastOrNull()
}

/**
 * Current state and progress of the continuous road movement simulator.
 */
data class RouteProgress(
    val currentPoint: RoutePoint,
    val bearing: Float,
    val speedKmh: Float,
    val speedMps: Float,
    val distanceTraveledMeters: Double,
    val totalDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val progressFraction: Float,
    val isCompleted: Boolean
) : Serializable
