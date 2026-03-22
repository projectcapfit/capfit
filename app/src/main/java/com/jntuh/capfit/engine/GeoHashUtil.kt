package com.jntuh.capfit.engine

/**
 * GeoHashUtil — Pure Kotlin Geohash implementation
 *
 * No external library needed — geohash is simple bit-interleaving math.
 *
 * What is a Geohash?
 * ------------------
 * A geohash encodes a lat/lng coordinate into a short alphanumeric string.
 * Nearby locations share a common prefix.
 *
 * Example (Hyderabad area):
 *   (17.465, 78.543) → "te7ud5xk..."
 *   (17.466, 78.544) → "te7ud5xm..."  ← same prefix "te7ud5" = same ~150m cell
 *   (28.613, 77.209) → "ttnjp..."     ← Delhi, completely different prefix
 *
 * Why this helps Firestore queries:
 * ----------------------------------
 * Current query:  whereGreaterThan("xMax", longitude)
 * → Scans potentially thousands of documents across the entire globe east of that longitude
 *
 * Geohash query:  whereGreaterThanOrEqualTo("geohash", "te7u")
 *                 .whereLessThan("geohash", "te7u~")
 * → Scans ONLY documents within ~5km box around user
 * → ~10-50x fewer documents downloaded from Firestore
 *
 * Precision guide (approximate cell sizes):
 *   precision 1 → ±2500km  (continent level)
 *   precision 2 → ±630km
 *   precision 3 → ±78km    (city level)
 *   precision 4 → ±20km
 *   precision 5 → ±2.4km   ← we use this for nearby query (~5km box)
 *   precision 6 → ±0.61km  ← we store this for exact session location
 *   precision 7 → ±76m
 *   precision 8 → ±19m
 *
 * We store geohash at precision 6 (~600m accuracy) on each session.
 * We query using precision 4 prefix (~20km range) to find all nearby sessions.
 * Then still apply bounding box filter in memory for exact overlap.
 */
object GeoHashUtil {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /**
     * Encodes a lat/lng into a geohash string of given precision.
     *
     * @param lat       latitude  (-90 to 90)
     * @param lng       longitude (-180 to 180)
     * @param precision number of characters (1-12). We use 6 for storage.
     */
    fun encode(lat: Double, lng: Double, precision: Int = 6): String {
        var minLat = -90.0;  var maxLat = 90.0
        var minLng = -180.0; var maxLng = 180.0

        val hash = StringBuilder()
        var isLng = true   // alternate between lng and lat bits
        var bit = 0
        var charIndex = 0

        while (hash.length < precision) {
            if (isLng) {
                val mid = (minLng + maxLng) / 2
                if (lng >= mid) {
                    charIndex = charIndex or (1 shl (4 - bit))
                    minLng = mid
                } else {
                    maxLng = mid
                }
            } else {
                val mid = (minLat + maxLat) / 2
                if (lat >= mid) {
                    charIndex = charIndex or (1 shl (4 - bit))
                    minLat = mid
                } else {
                    maxLat = mid
                }
            }

            isLng = !isLng
            bit++

            if (bit == 5) {
                hash.append(BASE32[charIndex])
                bit = 0
                charIndex = 0
            }
        }

        return hash.toString()
    }

    /**
     * Returns the query range [start, end) for a geohash prefix.
     * Use with Firestore:
     *   .whereGreaterThanOrEqualTo("geohash", range.first)
     *   .whereLessThan("geohash", range.second)
     *
     * The "~" character has ASCII value 126, higher than any base32 character (z = 122).
     * So prefix + "~" is guaranteed to be just above all hashes with that prefix.
     */
    fun queryRange(prefix: String): Pair<String, String> {
        return Pair(prefix, prefix + "~")
    }

    /**
     * Returns all 8 neighboring geohash cells + the center cell.
     * Useful for edge cases where a territory straddles a geohash boundary.
     *
     * When querying nearby territories, we query all 9 cells (center + 8 neighbors)
     * to avoid missing territories that are just across a cell boundary.
     */
    fun neighbors(geohash: String): List<String> {
        // Decode the geohash to lat/lng center
        val (lat, lng) = decode(geohash)
        val (latErr, lngErr) = decodeError(geohash.length)

        // Generate neighbors by offsetting slightly beyond cell boundaries
        val neighborOffsets = listOf(
            Pair(latErr * 2, 0.0),           // N
            Pair(-latErr * 2, 0.0),          // S
            Pair(0.0, lngErr * 2),           // E
            Pair(0.0, -lngErr * 2),          // W
            Pair(latErr * 2, lngErr * 2),    // NE
            Pair(latErr * 2, -lngErr * 2),   // NW
            Pair(-latErr * 2, lngErr * 2),   // SE
            Pair(-latErr * 2, -lngErr * 2)   // SW
        )

        return neighborOffsets.map { (dLat, dLng) ->
            encode(
                lat = (lat + dLat).coerceIn(-90.0, 90.0),
                lng = (lng + dLng).coerceIn(-180.0, 180.0),
                precision = geohash.length
            )
        }.distinct() + geohash  // include center
    }

    fun decode(geohash: String): Pair<Double, Double> {
        var minLat = -90.0;  var maxLat = 90.0
        var minLng = -180.0; var maxLng = 180.0
        var isLng = true

        for (char in geohash) {
            val charIndex = BASE32.indexOf(char)
            for (bits in 4 downTo 0) {
                val bit = (charIndex shr bits) and 1
                if (isLng) {
                    val mid = (minLng + maxLng) / 2
                    if (bit == 1) minLng = mid else maxLng = mid
                } else {
                    val mid = (minLat + maxLat) / 2
                    if (bit == 1) minLat = mid else maxLat = mid
                }
                isLng = !isLng
            }
        }

        return Pair((minLat + maxLat) / 2, (minLng + maxLng) / 2)
    }

    /**
     * Returns the error margin (half cell size) for a given precision.
     * Used to compute neighbor offsets.
     */
    private fun decodeError(precision: Int): Pair<Double, Double> {
        var latErr = 90.0
        var lngErr = 180.0
        var isLng = true
        repeat(precision * 5) {
            if (isLng) lngErr /= 2 else latErr /= 2
            isLng = !isLng
        }
        return Pair(latErr, lngErr)
    }

    const val STORAGE_PRECISION = 6

    const val QUERY_PRECISION = 4
}