package pl.marrod.localmark.ui.helpers

import android.location.Location
import com.google.android.gms.maps.model.LatLng

/**
 * Returns a human-readable distance label between [from] and [to].
 * - Under 1 km → "320 m away"
 * - 1 km or more → "1.2 km away"
 * Returns `null` if either location is unavailable.
 */
 fun distanceLabel(from: LatLng?, to: LatLng): String? {
    if (from == null) return null
    val results = FloatArray(1)
    Location.distanceBetween(
        from.latitude, from.longitude,
        to.latitude, to.longitude,
        results,
    )
    val meters = results[0]
    return if (meters < 1_000f) "${meters.toInt()} m away"
    else "${"%.1f".format(meters / 1_000f)} km away"
}