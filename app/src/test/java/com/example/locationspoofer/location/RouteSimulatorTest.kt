package com.example.locationspoofer.location

import com.example.locationspoofer.route.RoadRoute
import com.example.locationspoofer.route.RoutePoint
import com.example.locationspoofer.route.RouteSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RouteSimulatorTest {

    @Test
    fun totalRoadDistance_isSumOfRoadSegmentsNotStraightLine() {
        // Create an L-shaped road path: (0.0, 0.0) -> (0.0, 0.01) -> (0.01, 0.01)
        val p0 = RoutePoint(0.0, 0.0)
        val p1 = RoutePoint(0.0, 0.01)
        val p2 = RoutePoint(0.01, 0.01)

        val d01 = RouteSimulator.computeDistanceMeters(p0.latitude, p0.longitude, p1.latitude, p1.longitude)
        val d12 = RouteSimulator.computeDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        val straightDist = RouteSimulator.computeDistanceMeters(p0.latitude, p0.longitude, p2.latitude, p2.longitude)

        val route = RoadRoute(
            points = listOf(p0, p1, p2),
            totalDistanceMeters = d01 + d12,
            estimatedDurationSeconds = 120.0
        )

        val simulator = RouteSimulator(route, 20f)

        // The road distance should be greater than the straight-line diagonal distance
        assertTrue("Road distance must exceed straight line hypotenuse", simulator.totalDistanceMeters > straightDist)
        assertEquals(d01 + d12, simulator.totalDistanceMeters, 0.01)
    }

    @Test
    fun speedConversion_calculatesCorrectMetersPerSecond() {
        // 20 km/h = 20 / 3.6 = 5.5555... m/s
        val route = RoadRoute(
            points = listOf(RoutePoint(13.0, 80.0), RoutePoint(13.01, 80.01)),
            totalDistanceMeters = 1500.0,
            estimatedDurationSeconds = 270.0
        )
        val simulator = RouteSimulator(route, 20f)
        assertEquals(20f / 3.6f, simulator.speedMps, 0.001f)
    }

    @Test
    fun roadFollowing_carriesRemainderAcrossSegments() {
        // 3-point route where segment 1 is small (~111m) and segment 2 is ~111m
        val p0 = RoutePoint(0.0, 0.0)
        val p1 = RoutePoint(0.001, 0.0) // ~111.2 meters north
        val p2 = RoutePoint(0.001, 0.001) // ~111.2 meters east

        val seg1Dist = RouteSimulator.computeDistanceMeters(p0.latitude, p0.longitude, p1.latitude, p1.longitude)
        val seg2Dist = RouteSimulator.computeDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

        val route = RoadRoute(
            points = listOf(p0, p1, p2),
            totalDistanceMeters = seg1Dist + seg2Dist,
            estimatedDurationSeconds = 50.0
        )

        // Speed of 100 km/h = 27.78 m/s. In 5 seconds = 138.89 meters
        // This exceeds segment 1 (~111.2m), so it must cross into segment 2 (~27.69m into segment 2)
        val simulator = RouteSimulator(route, 100f)
        val progress = simulator.advance(5.0)

        assertFalse(progress.isCompleted)
        assertTrue("Distance traveled must cross into segment 2", progress.distanceTraveledMeters > seg1Dist)

        // The latitude should be at or near p1 (0.001) and longitude should be greater than 0.0 (moving East along segment 2)
        assertEquals(0.001, progress.currentPoint.latitude, 0.00001)
        assertTrue("Longitude must have advanced east into segment 2", progress.currentPoint.longitude > 0.0)

        // Bearing on segment 2 should be East (~90 degrees)
        assertEquals(90.0f, progress.bearing, 1.0f)
    }

    @Test
    fun bearingCalculation_followsTurnCorrectly() {
        // Heading North (0 deg)
        val bearingNorth = RouteSimulator.computeBearingDegrees(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0f, bearingNorth, 0.5f)

        // Heading East (90 deg)
        val bearingEast = RouteSimulator.computeBearingDegrees(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0f, bearingEast, 0.5f)

        // Heading South (180 deg)
        val bearingSouth = RouteSimulator.computeBearingDegrees(1.0, 0.0, 0.0, 0.0)
        assertEquals(180.0f, bearingSouth, 0.5f)

        // Heading West (270 deg)
        val bearingWest = RouteSimulator.computeBearingDegrees(0.0, 1.0, 0.0, 0.0)
        assertEquals(270.0f, bearingWest, 0.5f)
    }

    @Test
    fun simulationCompletion_stopsAtDestinationVertex() {
        val p0 = RoutePoint(13.0827, 80.2707)
        val p1 = RoutePoint(13.0837, 80.2717)
        val dist = RouteSimulator.computeDistanceMeters(p0.latitude, p0.longitude, p1.latitude, p1.longitude)

        val route = RoadRoute(
            points = listOf(p0, p1),
            totalDistanceMeters = dist,
            estimatedDurationSeconds = 30.0
        )

        val simulator = RouteSimulator(route, 100f) // fast speed to complete quickly
        val progress = simulator.advance(100.0) // 100 seconds

        assertTrue(progress.isCompleted)
        assertEquals(1.0f, progress.progressFraction, 0.001f)
        assertEquals(0.0, progress.remainingDistanceMeters, 0.001)
        assertEquals(p1.latitude, progress.currentPoint.latitude, 0.00001)
        assertEquals(p1.longitude, progress.currentPoint.longitude, 0.00001)
    }
}
