package com.example.locationspoofer.location

object LocationValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validate(latStr: String, lonStr: String, accStr: String): ValidationResult {
        val trimmedLat = latStr.trim()
        val trimmedLon = lonStr.trim()
        val trimmedAcc = accStr.trim()

        if (trimmedLat.isEmpty()) {
            return ValidationResult.Invalid("Latitude cannot be empty.")
        }
        if (trimmedLon.isEmpty()) {
            return ValidationResult.Invalid("Longitude cannot be empty.")
        }
        if (trimmedAcc.isEmpty()) {
            return ValidationResult.Invalid("Accuracy cannot be empty.")
        }

        val lat = trimmedLat.toDoubleOrNull()
            ?: return ValidationResult.Invalid("Latitude must be a valid number.")
        val lon = trimmedLon.toDoubleOrNull()
            ?: return ValidationResult.Invalid("Longitude must be a valid number.")
        val acc = trimmedAcc.toFloatOrNull()
            ?: return ValidationResult.Invalid("Accuracy must be a valid number.")

        if (lat < -90.0 || lat > 90.0) {
            return ValidationResult.Invalid("Latitude must be between -90 and 90 degrees.")
        }
        if (lon < -180.0 || lon > 180.0) {
            return ValidationResult.Invalid("Longitude must be between -180 and 180 degrees.")
        }
        if (acc <= 0f) {
            return ValidationResult.Invalid("Accuracy must be greater than 0 meters.")
        }

        return ValidationResult.Valid
    }
}
