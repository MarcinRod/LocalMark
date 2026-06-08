package pl.marrod.localmark.data

import com.google.android.gms.maps.model.LatLng
import pl.marrod.localmark.data.model.MarkerCategory
import pl.marrod.localmark.data.model.MarkerData
import kotlin.math.cos
import kotlin.random.Random

/**
 * Generates fake [MarkerData] entries scattered around a given origin point.
 *
 * Use this during development/demo to populate the map without a live backend.
 *
 * ### Radius logic
 * Each marker is placed at a random bearing and a random distance between
 * [minRadiusMeters] and [maxRadiusMeters].  The offset is computed using a
 * simple flat-Earth approximation that is accurate at the scales used here
 * (≤ 5 km):
 *
 * ```
 * Δlat = distanceM / EARTH_RADIUS_M  (in radians → degrees)
 * Δlng = (distanceM / EARTH_RADIUS_M) / cos(lat_rad)
 * ```
 *
 * @param minRadiusMeters Minimum distance from [origin] for a generated marker (default 100 m).
 * @param maxRadiusMeters Maximum distance from [origin] for a generated marker (default 1 000 m).
 * @param seed            Optional random seed for reproducible output.
 */
class DummyMarkerDataSource(
    val minRadiusMeters: Int = MIN_RADIUS_M,
    val maxRadiusMeters: Int = MAX_RADIUS_M,
    private val seed: Long? = null,
) {

    // ── Configuration constants ───────────────────────────────────────────────

    companion object {
        const val MIN_RADIUS_M = 100
        const val MAX_RADIUS_M = 9_000

        /** Metres per degree of latitude (constant everywhere on Earth). */
        private const val METERS_PER_DEGREE = 111_139.0  // ≈ 2π × 6_371_000 / 360

        // Dummy content pools
        private val AUTHORS = listOf(
            "Alex Mercer", "Sara Kim", "John Doe", "Maria Santos",
            "Liam Chen", "Priya Nair", "Tom Walsh", "Elena Russo",
        )
        private val MESSAGES = mapOf(
            MarkerCategory.Hazard to listOf(
                "Large pothole blocking the right lane — drive carefully.",
                "Fallen tree across the pavement, pedestrians affected.",
                "Broken traffic lights at the intersection.",
                "Severe flooding — multiple cars stuck, avoid the area.",
                "Gas leak reported near the corner. Emergency services on site.",
            ),
            MarkerCategory.Traffic to listOf(
                "Heavy congestion — expect 20-minute delays.",
                "Road works ahead — two lanes closed until Friday.",
                "Major accident blocking the highway, use alternate routes.",
                "Slow traffic due to event nearby. Allow extra travel time.",
                "Bus breakdown causing partial blockage.",
            ),
            MarkerCategory.Event to listOf(
                "Open-air concert tonight — roads around the venue will be busy.",
                "Marathon in progress — several streets closed until noon.",
                "Street market this weekend — limited parking available.",
                "Football match ending soon — expect crowds near the stadium.",
                "City parade — main boulevard closed until 18:00.",
            ),
        )

    }


    /**
     * Generates [count] random markers centred on [origin].
     *
     * @param origin Current user location.
     * @param count  Number of markers to generate (must be ≥ 1).
     * @return Immutable list of [MarkerData]; empty if [count] < 1.
     */
    fun generate(origin: LatLng, count: Int): List<MarkerData> {
        if (count < 1) return emptyList()

        val rng = if (seed != null) Random(seed) else Random.Default

        return List(count) { index ->
            val category = MarkerCategory.entries[rng.nextInt(MarkerCategory.entries.size)]
            val distanceM = rng.nextInt(minRadiusMeters, maxRadiusMeters + 1)
            val position = randomPosition(origin, distanceM, rng)

            MarkerData(
                id = "dummy-${System.currentTimeMillis()}-$index-${rng.nextInt()}",
                location = position,
                locationName = "Location ${index + 1}",
                category = category,
                authorName = AUTHORS.random(rng),
                authorId = "dummy-user-${rng.nextInt(1000)}",
                message = MESSAGES[category]!!.random(rng),
                timestamp = System.currentTimeMillis() - rng.nextLong(0, 60 * 60 * 1000), // Up to 1 hour ago
                confirmations = emptyList(), // No confirmations for dummy data

            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calculates a [LatLng] offset from [origin] by [distanceMeters] in a
     * random cartographic direction.
     */
    private fun randomPosition(origin: LatLng, distanceMeters: Int, rng: Random): LatLng {
        val bearingRad = rng.nextDouble(0.0, 2.0 * Math.PI)

        val deltaLat = distanceMeters * cos(bearingRad) / METERS_PER_DEGREE
        val deltaLng = distanceMeters * kotlin.math.sin(bearingRad) /
                (METERS_PER_DEGREE * cos(Math.toRadians(origin.latitude)))

        return LatLng(origin.latitude + deltaLat, origin.longitude + deltaLng)
    }
}
