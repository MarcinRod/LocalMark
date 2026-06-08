# Geocoding

## What is it?

**Geocoding** converts between human-readable addresses and geographic coordinates (`LatLng`). The reverse direction — coordinates → address — is called **reverse geocoding** and is what LocalMark uses.

The app calls the **Google Maps Geocoding REST API** directly via **Retrofit** + **kotlinx.serialization**, rather than using the Android `Geocoder` class or the Places SDK, because the REST API gives more control over which result types are returned and works independently of device locale.

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.retrofit)
implementation(libs.retrofit.converter.kotlinx.serialization)
implementation(libs.okhttp)
implementation(libs.kotlinx.serialization.json)
```

### Why a separate, unrestricted API key?

The Geocoding API key is a **separate** key from the Maps display key, and it must be left **unrestricted** (no Android app restriction). Here is why:

The Maps display key is called from inside the Maps SDK, which runs natively on the device and automatically attaches your app's package name and SHA-1 certificate fingerprint to every request. Google's servers can verify those values and enforce the Android app restriction.

The Geocoding key, however, is sent inside a plain **HTTPS REST request** made by Retrofit. There is no native SDK involved — the request is just an HTTP call with the key in the query string. Google's servers receive no package name or certificate fingerprint alongside it, so if you apply an Android app restriction to this key, every request will be rejected with a `REQUEST_DENIED` status.

The practical consequence is:

| Key | How it is used | Restriction type |
|---|---|---|
| `MAPS_API_KEY` | Maps SDK (native, attaches app identity) | Android app (package + SHA-1) |
| `GEOCODING_API_KEY` | Retrofit HTTP request (no app identity attached) | None (or IP address for server use) |

> **Security note:** An unrestricted key is more exposed if it leaks. Mitigate this by enabling only the **Geocoding API** for that key (API restriction), setting a quota limit in Google Cloud Console, and never committing the key to version control (see the Secrets Gradle Plugin section in [`01_google_maps.md`](./01_google_maps.md)).

The key is stored in `local.properties` and exposed through `BuildConfig`:

```
GEOCODING_API_KEY=YOUR_KEY_HERE
```

For full instructions on storing keys safely with the Secrets Gradle Plugin, see **[`01_google_maps.md → The Google Secrets Gradle Plugin`](./01_google_maps.md#the-google-secrets-gradle-plugin)**.

---

## Core Concepts

### The REST endpoint

The Geocoding API endpoint used for reverse geocoding is:

```
GET https://maps.googleapis.com/maps/api/geocode/json
    ?latlng={lat},{lng}
    &result_type={type1}|{type2}|...
    &key={API_KEY}
```

| Query parameter | Purpose |
|---|---|
| `latlng` | The coordinate to reverse-geocode, formatted as `"lat,lng"` |
| `result_type` | Pipe-separated filter; the API returns only results matching at least one type |
| `key` | The Geocoding API key |

### Retrofit service interface

```kotlin
private interface GeocodingApi {
    @GET("json")
    suspend fun reverseGeocode(
        @Query("latlng")      latlng: String,      // "52.2297,21.0122"
        @Query("result_type") resultType: String,  // "street_address|route"
        @Query("key")         key: String,
    ): GeocodingResponse
}
```

Because the interface is `private`, it is an implementation detail of `GeocodingRepository` and is never exposed outside that class.

### Building the Retrofit client

```kotlin
private val json = Json { ignoreUnknownKeys = true }

private val api: GeocodingApi = Retrofit.Builder()
    .baseUrl("https://maps.googleapis.com/maps/api/geocode/")
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(GeocodingApi::class.java)
```

`ignoreUnknownKeys = true` is important: the Geocoding API response contains many fields that the app does not use; without this flag, kotlinx.serialization would throw on every unknown field.

### `ResultType` enum

The app restricts which kind of address components the API should return, from most specific to least specific:

```kotlin
enum class ResultType(val value: String) {
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
```

Multiple types are joined with `|` when sent to the API:

```kotlin
resultTypes.joinToString("|") { it.value }
// → "street_address|route|neighborhood|locality"
```

### Response model

```kotlin
@Serializable
data class GeocodingResponse(
    val status: String,            // "OK", "ZERO_RESULTS", "OVER_QUERY_LIMIT", …
    val results: List<GeocodingResult> = emptyList(),
)

@Serializable
data class GeocodingResult(
    @SerialName("formatted_address") val formattedAddress: String,
    @SerialName("address_components") val addressComponents: List<AddressComponent>,
    // …
)
```

---

## How the app uses it

### `GeocodingRepository.reverseGeocode`

The single public method; called from `MapViewModel.showAddBottomDialog` to label a marker's location:

```kotlin
suspend fun reverseGeocode(
    latLng: LatLng,
    resultTypes: List<ResultType> = listOf(
        ResultType.STREET_ADDRESS,
        ResultType.ROUTE,
        ResultType.NEIGHBORHOOD,
        ResultType.LOCALITY,
    ),
): String? {
    return try {
        val response = api.reverseGeocode(
            latlng = "${latLng.latitude},${latLng.longitude}",
            resultType = resultTypes.joinToString("|") { it.value },
            key = BuildConfig.GEOCODING_API_KEY,
        )
        if (response.status != "OK") {
            Log.w(TAG, "Geocoding API returned status: ${response.status}")
            return null
        }
        response.results.firstOrNull()?.formattedAddress
    } catch (e: Exception) {
        Log.e(TAG, "Reverse geocoding failed for $latLng: ${e.message}")
        null
    }
}
```

Key design decisions:
- **Returns `null` on failure** instead of throwing, so callers can substitute a fallback.
- **Non-`OK` status** is treated as a soft failure (warning log, return `null`).
- **Network/serialization exceptions** are swallowed and logged — the geocoding result is cosmetic; the app must not crash because of it.

### Integration in `MapViewModel`

```kotlin
fun showAddBottomDialog(position: LatLng? = null) {
    _uiFlags.update { it.copy(addBottomSheetVisible = true, locationName = "") }

    val locationToGeocode = position ?: _location.value
    locationToGeocode?.let { latLng ->
        viewModelScope.launch {
            val name = geocodingRepository.reverseGeocode(latLng)
            // Fall back to a formatted coordinate string if geocoding returned nothing
            _uiFlags.update { it.copy(locationName = name ?: latLng.toDegreesString()) }
        }
    }
}
```

The sheet opens immediately while geocoding runs in the background. When the coroutine completes, `locationName` in the UI state is updated and the bottom sheet label refreshes automatically.

### Fallback: `LatLng.toDegreesString()`

If geocoding fails or returns nothing, the coordinates are shown as a degrees string:

```kotlin
fun LatLng.toDegreesString(): String {
    val latDir = if (latitude >= 0) "N" else "S"
    val lngDir = if (longitude >= 0) "E" else "W"
    return "%.2f°%s, %.2f°%s".format(abs(latitude), latDir, abs(longitude), lngDir)
}
// e.g. "52.23°N, 21.01°E"
```

---

## Minimal Setup Checklist

1. Enable the **Geocoding API** in Google Cloud Console for your project.
2. Create a **separate** API key for Geocoding and apply only an **API restriction** (Geocoding API only) — do **not** apply an Android app restriction, as REST calls carry no app identity and will be rejected.
3. Set a quota limit on the key in Google Cloud Console as a safety net against abuse.
4. Store it in `local.properties` as `GEOCODING_API_KEY=…` — see [`01_google_maps.md`](./01_google_maps.md#the-google-secrets-gradle-plugin) for the full Secrets Gradle Plugin setup.
5. Add Retrofit + kotlinx.serialization converter to Gradle dependencies.
6. Create a `@Serializable` response model (mark nested fields with `@SerialName` to match snake_case JSON keys).
7. Build a `Retrofit` instance pointed at `https://maps.googleapis.com/maps/api/geocode/`.
8. Call the suspend function from a coroutine (e.g., inside `viewModelScope.launch`).

