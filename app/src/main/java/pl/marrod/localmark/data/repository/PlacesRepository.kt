package pl.marrod.localmark.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await
import pl.marrod.localmark.data.model.PlaceResult
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.di.AppViewModelProvider
import pl.marrod.localmark.ui.screens.map.MapUiState
/**
 * Repository that wraps the Google Places SDK, providing a coroutine-friendly API
 * for place search and location lookup.
 *
 * **Instantiation**
 *
 * A single instance is created lazily inside [AppContainer] and
 * injected into [MapViewModel] via
 * [AppViewModelProvider].
 *
 * **Usage in [MapViewModel]**
 *
 * | ViewModel function | Repository call |
 * |---|---|
 * | `onSearchQueryChange(query)` | [searchPlaces] — debounced autocomplete for the search bar |
 * | `onPlaceSelected(placeId)` | [fetchLatLng] — resolves coordinates to animate the map camera |
 *
 * Both calls share the same [AutocompleteSessionToken] that is created when the user opens
 * the search bar and reset after a place is selected, in line with Google's billing
 * recommendations for [session-based pricing](https://developers.google.com/maps/documentation/places/android-sdk/autocomplete#get-session-tokens).
 *
 * @param placesClient The [PlacesClient] used to communicate with the Google Places API.
 *   Provided by [com.google.android.libraries.places.api.Places.createClient].
 */
class PlacesRepository(private val placesClient: PlacesClient) {

    /**
     * Returns autocomplete predictions for [query] using the Google Places Autocomplete API.
     *
     * Results are mapped to [PlaceResult] instances containing the place ID and primary /
     * secondary display texts. An empty list is returned when [query] is blank or when
     * the API call fails (the error is logged at the `ERROR` level with tag `PlacesRepository`).
     *
     * Called by [MapViewModel.onSearchQueryChange] on every
     * keystroke (with debouncing managed by the ViewModel).
     *
     * @param query   The user-typed search string.
     * @param token   An [AutocompleteSessionToken] that groups this request with the subsequent
     *                [fetchLatLng] call into a single billable session.
     * @return A (possibly empty) list of [PlaceResult] predictions, or an empty list on error.
     */
    suspend fun searchPlaces(query: String, token: AutocompleteSessionToken): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token) // token is optional but recommended for billing optimization
            .build()
        return try {
            val response = placesClient.findAutocompletePredictions(request).await()
            response.autocompletePredictions.map { prediction ->
                PlaceResult(
                    placeId = prediction.placeId,
                    primaryText = prediction.getPrimaryText(null).toString(),
                    secondaryText = prediction.getSecondaryText(null).toString(),
                )
            }
        } catch (e: Exception) {
            Log.e("PlacesRepository", "Autocomplete error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches the geographic coordinates ([LatLng]) for the given [placeId].
     *
     * Requests only the [Place.Field.LOCATION] field to minimise data transfer and cost.
     * On any failure the exception is swallowed, logged as `ERROR` with tag `PlacesRepository`,
     * and `null` is returned so callers can handle the missing location gracefully.
     *
     * Called by [MapViewModel.onPlaceSelected] after the user
     * taps a prediction from the search results list. The returned coordinates are forwarded to
     * [MapUiState.navigateToLocation] to animate the map
     * camera to the selected place.
     *
     * @param placeId The unique Google Place ID obtained from a [searchPlaces] prediction.
     * @param token   The same [AutocompleteSessionToken] used in the preceding [searchPlaces]
     *                call, ensuring the two requests are billed as a single session.
     * @return The [LatLng] of the place, or `null` if the request fails or no location is
     *         available.
     */
    suspend fun fetchLatLng(placeId: String, token: AutocompleteSessionToken): LatLng? {
        return try {
            val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LOCATION))
                .setSessionToken(token) // token is optional but recommended for billing optimization
                .build()
            val response = placesClient.fetchPlace(request).await()
            response.place.location
        } catch (e: Exception) {
            Log.e("PlacesRepository", "FetchPlace error for $placeId: ${e.message}")
            null
        }
    }
}

