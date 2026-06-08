# Places

## What is it?

The **Google Places SDK for Android** provides place search, autocomplete predictions, and place-detail lookups. LocalMark uses it to power the in-app search bar: as the user types, autocomplete suggestions appear; when a suggestion is tapped, the map camera flies to that place's coordinates.

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.places)  // com.google.android.libraries.places:places
```

The Places SDK reuses the same Maps API key (`MAPS_API_KEY`) stored in `local.properties`.

---

## Core Concepts

### Initialisation

The SDK must be initialised **once** before creating a `PlacesClient`. In LocalMark this happens inside `AppContainer`:

```kotlin
class AppContainer(context: Context) {
    init {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                context.applicationContext,
                BuildConfig.MAPS_API_KEY
            )
        }
    }
    val placesRepository by lazy {
        PlacesRepository(Places.createClient(context))
    }
}
```

`initializeWithNewPlacesApiEnabled` is the **new Places API** variant (recommended; uses the newer Places API v1 backend). The `isInitialized()` guard prevents a crash if `AppContainer` is somehow constructed more than once.

### `PlacesClient`

`Places.createClient(context)` returns a `PlacesClient` — the single entry point for all Places SDK operations. It is created once and injected into `PlacesRepository`.

### `AutocompleteSessionToken`

A session token groups a series of autocomplete keystrokes with the final place-detail lookup into a **single billable session**. Without it, every keystroke is billed individually as a separate Autocomplete request.

```kotlin
// Create a token when the search bar opens
val token = AutocompleteSessionToken.newInstance()

// Use the same token for every keystroke
placesClient.findAutocompletePredictions(request.setSessionToken(token).build())

// Use the same token for the final detail lookup
placesClient.fetchPlace(request.setSessionToken(token).build())

// After the place is selected, discard the token — the session is complete
token = null
```

In LocalMark, `MapViewModel.toggleSearch()` creates a new token when the bar opens and nulls it out when the bar closes without a selection or after a place is selected via `onPlaceSelected`.

### `FindAutocompletePredictionsRequest`

Fetches a list of place predictions for a partial text query:

```kotlin
val request = FindAutocompletePredictionsRequest.builder()
    .setQuery(query)          // user-typed text
    .setSessionToken(token)   // billing optimisation
    // optional: .setLocationBias(…) to bias results toward a region
    // optional: .setTypesFilter(…) to restrict result types
    .build()

val response = placesClient.findAutocompletePredictions(request).await()
response.autocompletePredictions.map { prediction ->
    PlaceResult(
        placeId = prediction.placeId,
        primaryText   = prediction.getPrimaryText(null).toString(),   // e.g. "Eiffel Tower"
        secondaryText = prediction.getSecondaryText(null).toString(), // e.g. "Paris, France"
    )
}
```

`getPrimaryText` / `getSecondaryText` accept an optional `CharacterStyle` for highlighting matched characters (passing `null` returns plain text).

### `FetchPlaceRequest`

Resolves a `placeId` to one or more `Place.Field` values. The app only needs `LAT_LNG`, so it requests only that field to minimise data transfer and cost:

```kotlin
val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG))
    .setSessionToken(token)  // closes the billing session
    .build()

val response = placesClient.fetchPlace(request).await()
val latLng: LatLng? = response.place.location
```

Passing the same token here as in the preceding `findAutocompletePredictions` calls groups them into one session and ends it.

---

## How the app uses it

### `PlacesRepository`

A thin wrapper that converts the callback-based SDK calls into `suspend` functions:

```kotlin
class PlacesRepository(private val placesClient: PlacesClient) {

    suspend fun searchPlaces(query: String, token: AutocompleteSessionToken): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
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
            emptyList()  // degrade gracefully — empty list, not a crash
        }
    }

    suspend fun fetchLatLng(placeId: String, token: AutocompleteSessionToken): LatLng? {
        return try {
            val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG))
                .setSessionToken(token)
                .build()
            val response = placesClient.fetchPlace(request).await()
            response.place.location
        } catch (e: Exception) {
            Log.e("PlacesRepository", "FetchPlace error for $placeId: ${e.message}")
            null
        }
    }
}
```

### Session token lifecycle in `MapViewModel`

```kotlin
private var sessionToken: AutocompleteSessionToken? = null
private var searchJob: Job? = null

fun toggleSearch() {
    sessionToken = if (!_uiFlags.value.isSearchVisible) {
        AutocompleteSessionToken.newInstance()  // new session starts when bar opens
    } else {
        null                                    // session abandoned when bar closes
    }
    // … update UI state …
}

fun onSearchQueryChange(query: String) {
    searchJob?.cancel()           // cancel stale in-flight request
    searchJob = viewModelScope.launch {
        val results = placesRepository.searchPlaces(query, sessionToken!!)
        _uiFlags.update { it.copy(placeResults = results) }
    }
}

fun onPlaceSelected(placeId: String) {
    viewModelScope.launch {
        val latLng = placesRepository.fetchLatLng(placeId, sessionToken!!)
        if (latLng != null) {
            _uiFlags.update { it.copy(navigateToLocation = latLng) }
            // sessionToken is reset in toggleSearch after the bar closes
        }
    }
}
```

Cancelling `searchJob` before each new request prevents an older (slower) network response from overwriting fresher results.

---

## Minimal Setup Checklist

1. Enable the **Places API** in Google Cloud Console.
2. Add `places` to Gradle dependencies.
3. Call `Places.initializeWithNewPlacesApiEnabled(context, apiKey)` once at app startup.
4. Create a `PlacesClient` with `Places.createClient(context)`.
5. Use `AutocompleteSessionToken.newInstance()` when a search session begins; pass it to both `findAutocompletePredictions` and `fetchPlace`; discard it after the place is selected.
6. Wrap SDK calls in `try/catch` — the SDK throws `ApiException` for quota errors, bad tokens, etc.

