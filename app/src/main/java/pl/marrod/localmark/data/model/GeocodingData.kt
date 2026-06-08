package pl.marrod.localmark.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.marrod.localmark.data.repository.GeocodingRepository
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.ui.screens.map.MapUiState
// Reference: https://developers.google.com/maps/documentation/geocoding/guides-v3/requests-geocoding

/**
 * Top-level response envelope returned by the
 * [Google Maps Geocoding REST API](https://developers.google.com/maps/documentation/geocoding/guides-v3/requests-geocoding).
 *
 * Deserialized by [GeocodingRepository] via
 * Retrofit + kotlinx-serialization after a reverse-geocoding request.
 * The repository inspects [status] before consuming [results].
 *
 * @property status  API status string (e.g. `"OK"`, `"ZERO_RESULTS"`, `"REQUEST_DENIED"`).
 *                   [GeocodingRepository.reverseGeocode]
 *                   returns `null` for any value other than `"OK"`.
 * @property results Ordered list of geocoding results. The repository uses only the first
 *                   entry ([List.firstOrNull]). Defaults to an empty list when the field is
 *                   absent from the JSON payload.
 */
@Serializable
data class GeocodingResponse(
    val status: String,
    val results: List<GeocodingResult> = emptyList(),
)

/**
 * A single reverse-geocoding result within a [GeocodingResponse].
 *
 * [GeocodingRepository.reverseGeocode] returns
 * [formattedAddress] of the first result directly to the caller
 * ([MapViewModel]), which stores it in
 * [MapUiState.locationName].
 *
 * [addressComponents] is available for finer-grained parsing — used by
 * `GeocodingRepository.parseShortAddress` to build a compact street-level label.
 *
 * @property formattedAddress  The full human-readable address string as returned by the API
 *                             (JSON key `formatted_address`).
 * @property addressComponents Structured breakdown of the address into typed components
 *                             (JSON key `address_components`). Defaults to an empty list.
 */
@Serializable
data class GeocodingResult(
    @SerialName("formatted_address") val formattedAddress: String = "",
    @SerialName("address_components") val addressComponents: List<AddressComponent> = emptyList(),
)

/**
 * One component of a structured address within a [GeocodingResult].
 *
 * Each component carries a human-readable name ([longName]) and a list of
 * [semantic type tags][types] (e.g. `"street_number"`, `"route"`, `"locality"`)
 * that correspond to the values defined in
 * [GeocodingRepository.ResultType][GeocodingRepository.ResultType].
 *
 * Used by `GeocodingRepository.parseShortAddress` to extract the most relevant
 * part of an address (street, neighbourhood, or city) for display purposes.
 *
 * @property longName The full text of the address component (JSON key `long_name`),
 *                    e.g. `"Main Street"` or `"Warsaw"`.
 * @property types    One or more type strings that classify this component,
 *                    e.g. `["route"]` or `["locality", "political"]`.
 */
@Serializable
data class AddressComponent(
    @SerialName("long_name") val longName: String,
    val types: List<String>,
)
