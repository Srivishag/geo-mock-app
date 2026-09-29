package com.example.locationspoofer.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Road-following movement simulator.
 * Steps incrementally along actual road geometry at a specified physical speed,
 * carrying remaining movement across road segments and calculating dynamic bearings.
 */
class RouteSimulator(
    val route: RoadRoute,
    val speedKmh: Float
) {
    init {
        require(route.points.size >= 2) { "Route must contain at least 2 points." }
        require(speedKmh > 0f) { "Speed must be greater than 0 km/h." }
    }

    // Speed in meters per second (km/h / 3.6)
    val speedMps: Float = speedKmh / 3.6f

    // Pre-calculate segment distances and cumulative distances
    private val segmentDistances: DoubleArray = DoubleArray(route.points.size - 1)
    private val cumulativeDistances: DoubleArray = DoubleArray(route.points.size)

    init {
        cumulativeDistances[0] = 0.0
        for (i in 0 until route.points.size - 1) {
            val p1 = route.points[i]
            val p2 = route.points[i + 1]
            val dist = computeDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            segmentDistances[i] = dist
            cumulativeDistances[i + 1] = cumulativeDistances[i] + dist
        }
    }

    val totalDistanceMeters: Double = cumulativeDistances.last()

    // Current state along the route
    var distanceTraveledMeters: Double = 0.0
        private set

    var currentBearing: Float = calculateInitialBearing()
        private set

    val isCompleted: Boolean
        get() = distanceTraveledMeters >= totalDistanceMeters

    private fun calculateInitialBearing(): Float {
        if (route.points.size < 2) return 0f
        return computeBearingDegrees(
            route.points[0].latitude, route.points[0].longitude,
            route.points[1].latitude, route.points[1].longitude
        )
    }

    /**
     * Advances the simulation by elapsed time in seconds.
     * Calculates distance = speedMps * deltaSeconds, carries across segment vertices,
     * and updates exact current coordinates and bearing.
     */
    fun advance(deltaSeconds: Double): RouteProgress {
        if (isCompleted) {
            val endPoint = route.points.last()
            return RouteProgress(
                currentPoint = endPoint,
                bearing = currentBearing,
                speedKmh = 0f,
                speedMps = 0f,
                distanceTraveledMeters = totalDistanceMeters,
                totalDistanceMeters = totalDistanceMeters,
                remainingDistanceMeters = 0.0,
                progressFraction = 1.0f,
                isCompleted = true
            )
        }

        val stepDistance = speedMps * deltaSeconds
        distanceTraveledMeters = (distanceTraveledMeters + stepDistance).coerceAtMost(totalDistanceMeters)

        val currentPoint = computePositionAtDistance(distanceTraveledMeters)
        val remaining = (totalDistanceMeters - distanceTraveledMeters).coerceAtLeast(0.0)
        val fraction = if (totalDistanceMeters > 0.0) {
            (distanceTraveledMeters / totalDistanceMeters).toFloat().coerceIn(0.0f, 1.0f)
        } else 1.0f

        return RouteProgress(
            currentPoint = currentPoint,
            bearing = currentBearing,
            speedKmh = if (isCompleted) 0f else speedKmh,
            speedMps = if (isCompleted) 0f else speedMps,
            distanceTraveledMeters = distanceTraveledMeters,
            totalDistanceMeters = totalDistanceMeters,
            remainingDistanceMeters = remaining,
            progressFraction = fraction,
            isCompleted = isCompleted
        )
    }

    /**
     * Computes exact coordinate along road geometry at specified distance from start.
     */
    fun computePositionAtDistance(targetDistance: Double): RoutePoint {
        if (targetDistance <= 0.0) return route.points.first()
        if (targetDistance >= totalDistanceMeters) return route.points.last()

        // Binary search / scan for segment index
        var segIndex = 0
        while (segIndex < segmentDistances.size - 1 && cumulativeDistances[segIndex + 1] < targetDistance) {
            segIndex++
        }

        val segStartDistance = cumulativeDistances[segIndex]
        val segLength = segmentDistances[segIndex]
        val p1 = route.points[segIndex]
        val p2 = route.points[segIndex + 1]

        val fraction = if (segLength > 0.0) {
            ((targetDistance - segStartDistance) / segLength).coerceIn(0.0, 1.0)
        } else 0.0

        val lat = p1.latitude + fraction * (p2.latitude - p1.latitude)
        val lon = p1.longitude + fraction * (p2.longitude - p1.longitude)

        // Update bearing towards p2
        currentBearing = computeBearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

        return RoutePoint(lat, lon)
    }

    fun reset() {
        distanceTraveledMeters = 0.0
        currentBearing = calculateInitialBearing()
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

        /**
         * Calculates geodesic distance between two points using Haversine formula (meters).
         */
        fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(rLat1) * cos(rLat2) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        /**
         * Calculates initial forward bearing from point 1 to point 2 (degrees 0..360).
         */
        fun computeBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)

            val y = sin(dLon) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
            val bearingRad = atan2(y, x)
            val bearingDeg = Math.toDegrees(bearingRad)
            return ((bearingDeg + 360.0) % 360.0).toFloat()
        }
    }
}
