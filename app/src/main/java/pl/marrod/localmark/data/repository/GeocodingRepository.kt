package pl.marrod.localmark.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import pl.marrod.localmark.BuildConfig
import pl.marrod.localmark.data.model.GeocodingResponse
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.di.AppViewModelProvider
import pl.marrod.localmark.ui.screens.map.MapUiState
/**
 * Retrofit service that maps directly to the
 * [Google Maps Geocoding REST API](https://developers.google.com/maps/documentation/geocoding/overview).
 * The single endpoint hit is `GET geocode/json`.
 */
private interface GeocodingApi {
    @GET("json")
    suspend fun reverseGeocode(
        @Query("latlng") latlng: String,
        @Query("result_type") resultType: String,
        @Query("key") key: String,
    ): GeocodingResponse
}

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Repository that uses the Google Maps Geocoding REST API to convert a [LatLng]
 * coordinate into a human-readable address string (reverse geocoding).
 *
 * **Instantiation**
 *
 * A single instance is created lazily inside [AppContainer]
 * (no-arg constructor; the API key is read from `BuildConfig.GEOCODING_API_KEY`) and
 * injected into [MapViewModel] via
 * [AppViewModelProvider].
 *
 * **Usage in [MapViewModel]**
 *
 * | ViewModel function | Repository call |
 * |---|---|
 * | `showAddBottomDialog(position)` | [reverseGeocode] — resolves the tapped map position (or the current device location as a fallback) into a readable name shown in the "Add marker" bottom sheet as [MapUiState.locationName] |
 *
 * The call is launched in `viewModelScope` so it never blocks the UI. If geocoding
 * fails or returns nothing, the coordinates are formatted as a degrees string instead.
 *
 * **Networking**
 *
 * Retrofit + kotlinx-serialization are used for the HTTP layer. The client is built
 * internally so the repository has no external dependencies beyond the API key.
 */
class GeocodingRepository {

    /**
     * Restricts which
     * [result types](https://developers.google.com/maps/documentation/geocoding/guides-v3/requests-reverse-geocoding)
     * are returned by the Geocoding API when calling [reverseGeocode].
     *
     * Passing a subset of these values in the `result_type` query parameter tells the API
     * to filter results to those that match at least one of the requested types.
     *
     * @property value The string value sent to the API as the `result_type` query parameter.
     */

    enum class ResultType(val value: String) {
        // https://developers.google.com/maps/documentation/geocoding/guides-v3/requests-reverse-geocoding

        STREET_ADDRESS("street_address"),
        ROUTE("route"),
        INTERSECTION("intersection"),
        POLITICAL("political"),
        COUNTRY("country"),
        LOCALITY("locality"),
        SUBLOCALITY("sublocality"),
        NEIGHBORHOOD("neighborhood"),
        NATURAL_FEATURE("natural_feature"),
    }
    private val json = Json { ignoreUnknownKeys = true  /* Ignore API response fields we don't model in our DTOs. */ }

    private val api: GeocodingApi = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/maps/api/geocode/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeocodingApi::class.java)

    /**
     * Reverse-geocodes [latLng] into a short, human-readable location string.
     *
     * The API call filters results by [resultTypes] (pipe-separated in the query string).
     * The first matching result's `formatted_address` is returned. If the API responds
     * with a non-`OK` status, a warning is logged and `null` is returned. On any network
     * or serialization error the exception is swallowed, logged as `ERROR`, and `null` is
     * returned so callers can substitute a fallback value.
     *
     * Called by [MapViewModel.showAddBottomDialog]
     * (inside a `viewModelScope` coroutine) to populate
     * [MapUiState.locationName] in the
     * "Add marker" bottom sheet.
     *
     * @param latLng      The geographic coordinate to reverse-geocode.
     * @param resultTypes The ordered list of [ResultType] filters sent to the API.
     *                    Defaults to `[STREET_ADDRESS, ROUTE, NEIGHBORHOOD, LOCALITY]`,
     *                    which gives the most specific street-level address available,
     *                    falling back to progressively coarser granularity.
     * @return The formatted address string of the first matching result, or `null` if
     *         the request fails, the status is not `OK`, or no results are returned.
     */
    suspend fun reverseGeocode(latLng: LatLng, resultTypes: List<ResultType> = listOf(
        ResultType.STREET_ADDRESS,
        ResultType.ROUTE,
        ResultType.NEIGHBORHOOD,
        ResultType.LOCALITY,
    ),): String? {
        return try {
            val response = api.reverseGeocode(
                latlng = "${latLng.latitude},${latLng.longitude}",
                resultType = resultTypes.joinToString("|") { it.value },
                // The GEOCODING API key is different from the MAPS_API_KEY because of
                // Google Cloud's API key restrictions.
                key = BuildConfig.GEOCODING_API_KEY,


            )
            if (response.status != "OK") {
                Log.w("GeocodingRepository", "Geocoding API returned status: ${response.status}")
                return null
            }
            response.results.firstOrNull()?.formattedAddress
        } catch (e: Exception) {
            Log.e("GeocodingRepository", "Reverse geocoding failed for $latLng: ${e.message}")
            null
        }
    }



}
