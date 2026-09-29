package com.example.locationspoofer.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun parseStandardCommaSeparated() {
        val result = CoordinateParser.parse("13.0827, 80.2707")
        assertNotNull(result)
        assertEquals(13.0827, result!!.first, 0.0001)
        assertEquals(80.2707, result.second, 0.0001)
    }

    @Test
    fun parseCommaNoSpace() {
        val result = CoordinateParser.parse("13.0827,80.2707")
        assertNotNull(result)
        assertEquals(13.0827, result!!.first, 0.0001)
        assertEquals(80.2707, result.second, 0.0001)
    }

    @Test
    fun parseSpaceSeparated() {
        val result = CoordinateParser.parse("13.0827 80.2707")
        assertNotNull(result)
        assertEquals(13.0827, result!!.first, 0.0001)
        assertEquals(80.2707, result.second, 0.0001)
    }

    @Test
    fun parseNegativeCoordinates() {
        val result = CoordinateParser.parse("-33.8688, 151.2093")
        assertNotNull(result)
        assertEquals(-33.8688, result!!.first, 0.0001)
        assertEquals(151.2093, result.second, 0.0001)
    }

    @Test
    fun parseLabeledCoordinates() {
        val result = CoordinateParser.parse("Lat: 13.0827, Lon: 80.2707")
        assertNotNull(result)
        assertEquals(13.0827, result!!.first, 0.0001)
        assertEquals(80.2707, result.second, 0.0001)
    }

    @Test
    fun parseInvalidInput_returnsNull() {
        assertNull(CoordinateParser.parse("New York City"))
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("95.00, 200.00")) // Out of bounds
    }
}
