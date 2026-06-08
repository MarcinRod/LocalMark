package pl.marrod.localmark.data.model

import pl.marrod.localmark.ui.screens.map.MapScreen
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.ui.screens.map.MapUiState
import pl.marrod.localmark.data.repository.PlacesRepository
/**
 * Lightweight representation of a single Google Places autocomplete prediction.
 *
 * **Data flow**
 *
 * 1. [PlacesRepository.searchPlaces] maps each
 *    `AutocompletePrediction` from the Places SDK into a [PlaceResult].
 * 2. The list is stored in
 *    [MapUiState.placeResults] by
 *    [MapViewModel.onSearchQueryChange].
 * 3. [MapScreen] passes the list to
 *    `PlacesSearchBar`, which renders one row per result using [primaryText] and
 *    [secondaryText].
 * 4. When the user taps a row, [placeId] is forwarded to
 *    [MapViewModel.onPlaceSelected], which calls
 *    [PlacesRepository.fetchLatLng] to resolve the
 *    coordinates and animate the map camera.
 *
 * @param placeId       Stable Google Place identifier passed to
 *                      [PlacesRepository.fetchLatLng]
 *                      to retrieve the full [com.google.android.gms.maps.model.LatLng].
 * @param primaryText   Main display name shown as the bold first line in the search
 *                      result list, e.g. `"Eiffel Tower"`.
 * @param secondaryText Supplementary context shown as a dimmer second line,
 *                      e.g. `"Paris, France"`.
 */
data class PlaceResult(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)

