package com.example.locationspoofer.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationValidatorTest {

    @Test
    fun validCoordinates_returnsValid() {
        val result = LocationValidator.validate("13.0827", "80.2707", "5")
        assertEquals(LocationValidator.ValidationResult.Valid, result)
    }

    @Test
    fun validBoundaryCoordinates_returnsValid() {
        val result1 = LocationValidator.validate("90.0", "180.0", "0.1")
        assertEquals(LocationValidator.ValidationResult.Valid, result1)

        val result2 = LocationValidator.validate("-90.0", "-180.0", "1")
        assertEquals(LocationValidator.ValidationResult.Valid, result2)
    }

    @Test
    fun emptyInputs_returnsInvalid() {
        val resLat = LocationValidator.validate("", "80.2707", "5")
        assertTrue(resLat is LocationValidator.ValidationResult.Invalid)

        val resLon = LocationValidator.validate("13.0827", "", "5")
        assertTrue(resLon is LocationValidator.ValidationResult.Invalid)

        val resAcc = LocationValidator.validate("13.0827", "80.2707", "")
        assertTrue(resAcc is LocationValidator.ValidationResult.Invalid)
    }

    @Test
    fun outOfRangeLatitude_returnsInvalid() {
        val resOver = LocationValidator.validate("90.0001", "80.2707", "5")
        assertTrue(resOver is LocationValidator.ValidationResult.Invalid)

        val resUnder = LocationValidator.validate("-90.0001", "80.2707", "5")
        assertTrue(resUnder is LocationValidator.ValidationResult.Invalid)
    }

    @Test
    fun outOfRangeLongitude_returnsInvalid() {
        val resOver = LocationValidator.validate("13.0827", "180.0001", "5")
        assertTrue(resOver is LocationValidator.ValidationResult.Invalid)

        val resUnder = LocationValidator.validate("13.0827", "-180.0001", "5")
        assertTrue(resUnder is LocationValidator.ValidationResult.Invalid)
    }

    @Test
    fun nonPositiveAccuracy_returnsInvalid() {
        val resZero = LocationValidator.validate("13.0827", "80.2707", "0")
        assertTrue(resZero is LocationValidator.ValidationResult.Invalid)

        val resNegative = LocationValidator.validate("13.0827", "80.2707", "-5")
        assertTrue(resNegative is LocationValidator.ValidationResult.Invalid)
    }
}
