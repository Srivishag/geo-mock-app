package com.example.locationspoofer.location

object CoordinateParser {
    /**
     * Parses raw input strings containing coordinates in various formats:
     * - "13.0827, 80.2707"
     * - "13.0827,80.2707"
     * - "13.0827 80.2707"
     * - "Lat: 13.0827, Lon: 80.2707"
     * - "13.0827° N, 80.2707° E"
     * - "-33.8688, 151.2093"
     */
    fun parse(input: String): Pair<Double, Double>? {
        val clean = input.trim()
        if (clean.isEmpty()) return null

        // Comma separated
        if (clean.contains(",")) {
            val parts = clean.split(",")
            if (parts.size == 2) {
                val lat = extractNumber(parts[0])
                val lon = extractNumber(parts[1])
                if (lat != null && lon != null && isValid(lat, lon)) {
                    return Pair(lat, lon)
                }
            }
        }

        // Space separated
        val spaceParts = clean.split("\\s+".toRegex())
        if (spaceParts.size == 2) {
            val lat = extractNumber(spaceParts[0])
            val lon = extractNumber(spaceParts[1])
            if (lat != null && lon != null && isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // General regex search for 2 numbers
        val regex = Regex("""[-+]?([0-9]*\.[0-9]+|[0-9]+)""")
        val matches = regex.findAll(clean).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (matches.size == 2) {
            val lat = matches[0]
            val lon = matches[1]
            if (isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        return null
    }

    private fun extractNumber(s: String): Double? {
        val cleaned = s.replace("[^0-9.-]".toRegex(), "")
        return cleaned.toDoubleOrNull()
    }

    private fun isValid(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }
}
