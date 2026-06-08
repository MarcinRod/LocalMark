# Google Maps

## What is it?

The **Maps SDK for Android** (consumed here through the **Maps Compose** wrapper library) renders an interactive tile map inside a Jetpack Compose UI. The Compose wrapper exposes the map as a `@Composable` function and provides Compose-idiomatic state holders for the camera and markers, replacing the traditional `MapView`/`SupportMapFragment` approach.

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.maps.compose)        // google/maps-android-compose
implementation(libs.play.services.maps)  // underlying Google Play Services Maps SDK
```

The Maps API key is injected through the **Google Secrets Gradle Plugin** and referenced as a Manifest placeholder in `AndroidManifest.xml`:

```xml
<!-- AndroidManifest.xml -->
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

The key itself lives in `local.properties` (never committed to VCS):

```
MAPS_API_KEY=YOUR_KEY_HERE
```

---

## Core Concepts

### `GoogleMap` composable

The root composable that renders the map tile layer and hosts all map content:

```kotlin
GoogleMap(
    modifier = Modifier.fillMaxSize(),
    cameraPositionState = cameraPositionState,   // controls where the camera looks
    properties = mapProperties,                   // map type, my-location dot, style
    uiSettings = MapUiSettings(
        myLocationButtonEnabled = false,          // hide the built-in "my location" FAB
        zoomControlsEnabled = false,
        compassEnabled = false,
        mapToolbarEnabled = false,
    ),
    onMapLoaded = { /* called once tiles are ready */ },
    onMapLongClick = { latLng -> /* user long-pressed the map */ },
) {
    // Content placed here is drawn on top of the map tiles
}
```

### `CameraPositionState`

Holds the current camera position (target `LatLng`, zoom, bearing, tilt) and exposes `animate()` / `move()` to drive the camera programmatically:

```kotlin
val cameraPositionState = rememberCameraPositionState()

// Animate the camera to a specific position
cameraPositionState.animate(
    update = CameraUpdateFactory.newLatLngZoom(latLng, 15f),
    durationMs = 800,
)
```

`CameraUpdateFactory` is the factory for camera movements:

| Factory method | Effect |
|---|---|
| `newLatLngZoom(latLng, zoom)` | Fly to location at given zoom |
| `newCameraPosition(CameraPosition)` | Full control: target, zoom, bearing, tilt |
| `zoomIn()` / `zoomOut()` | Step zoom by 1 |

`rememberCameraPositionState()` persists the position across recompositions. The position also survives configuration changes (rotation) because it is backed by `rememberSaveable` internally.

### `MapProperties`

Controls map-level settings that affect what is rendered:

```kotlin
MapProperties(
    isMyLocationEnabled = locationPermissionGranted, // show the blue "my location" dot
    mapType = MapType.NORMAL,
    mapStyleOptions = if (isDarkTheme)
        MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
    else null,
)
```

`MapStyleOptions` loads a JSON style file from `res/raw/` that overrides the default map colours — used here to provide a dark-themed map matching the app's dark colour scheme.

### `MapUiSettings`

Controls the built-in UI chrome rendered by the Maps SDK:

```kotlin
MapUiSettings(
    myLocationButtonEnabled = false,  // replaced by a custom FAB in the app
    zoomControlsEnabled = false,      // replaced by custom +/- buttons
    compassEnabled = false,
    mapToolbarEnabled = false,        // hides the "open in Google Maps" shortcut
)
```

### `MarkerComposable`

Renders an arbitrary Compose composable as a map marker, instead of the default pin bitmap:

```kotlin
@OptIn(MapsComposeExperimentalApi::class)
MarkerComposable(
    state = rememberMarkerState(position = marker.location),
    onClick = { onMarkerClick(marker); false }, // return false → do not consume click
) {
    // Any composable works here – the app uses a custom LocalMarkMarker
    LocalMarkMarker(
        icon = marker.category.icon,
        accentColor = marker.category.accentColor,
    )
}
```

`rememberMarkerState` holds the `LatLng` position of the marker. Using `key(marker.id) { … }` around each marker ensures Compose reuses the correct state object when the marker list changes.

### `Circle`

Draws a filled circle overlay on the map — used here to visualise the search radius:

```kotlin
Circle(
    center = currentLocation,
    radius = radiusMetres.toDouble(),
    fillColor = primaryColor.copy(alpha = 0.18f),
    strokeColor = primaryColor.copy(alpha = 0.80f),
    strokeWidth = 4f,
)
```

---

## How the app uses it

### `MapCameraState` — custom camera state holder

The app wraps `CameraPositionState` inside a custom `@Stable` class `MapCameraState` to:
1. Centralise all camera animations in one place (north reset, fly-to-location, zoom in/out).
2. Keep the camera logic out of the composable body.

```kotlin
@Stable
class MapCameraState(
    val cameraPositionState: CameraPositionState,
    private val scope: CoroutineScope,
) {
    fun animateToLocation(location: LatLng) = scope.launch {
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(location, 15f),
            durationMs = 800,
        )
    }

    fun animateToNorth() = scope.launch {
        val cur = cameraPositionState.position
        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(cur.target).zoom(cur.zoom)
                    .bearing(0f).tilt(0f).build()
            ),
            durationMs = 500,
        )
    }
}
```

### `rememberMapCameraState` — wiring `LaunchedEffect`s

The companion factory function sets up four side effects that drive the camera automatically:

| `LaunchedEffect` key | Behaviour |
|---|---|
| `state.isMapLoaded && currentLocation` | One-time initial fly-in to the user's location once map tiles are ready |
| `cameraPositionState` (snapshot flow) | Calls `onCameraIdle` callback when the camera stops moving |
| `navigateToLocation` | Animates to a place selected from the search bar |
| `searchRadius` | Zooms out if the radius circle would overflow the screen edges |

### Animated radius overlay

The search radius is visualised with an animated `Circle` composable whose `radius` grows from 0 to the actual radius on a 1.8 s `infiniteRepeatable` tween, creating a pulse effect:

```kotlin
val animProgress by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = "radiusProgress",
)
// radius grows proportionally to animProgress
radius = (animProgress * searchRadius * 1_000f).toDouble()
```

### Dark-mode map style

`MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)` reads a JSON file from `res/raw/` to restyle the map. The style is applied conditionally:

```kotlin
mapStyleOptions = if (isDarkTheme)
    MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
else null
```

---

## Getting a Maps API Key

### Step 1 — Create or open a Google Cloud project

1. Go to [https://console.cloud.google.com/](https://console.cloud.google.com/) and sign in with your Google account.
2. Click the project selector dropdown at the top of the page (next to the "Google Cloud" logo).
3. Click **New Project**, give it a name (e.g. `LocalMark`), and click **Create**.
4. Wait for the project to be created, then make sure it is selected in the dropdown.

### Step 2 — Enable the Maps SDK for Android

1. In the left sidebar go to **APIs & Services → Library**.
2. Search for **"Maps SDK for Android"** and click the result.
3. Click **Enable**. Wait for the API to be enabled (the button turns grey and shows a green checkmark).

> If you also need Geocoding (see `02_geocoding.md`), repeat this step for **"Geocoding API"**.

### Step 3 — Create an API key

1. In the left sidebar go to **APIs & Services → Credentials**.
2. Click **+ Create Credentials → API key**.
3. A dialog shows the newly created key — copy it now (you can always view it again later).
4. Click **Edit API key** (or close the dialog and click the key's name in the list).

### Step 4 — Restrict the key (strongly recommended)

Unrestricted keys can be used by anyone who finds them. Restrict the key so it only works from your app:

1. Under **Application restrictions** select **Android apps**.
2. Click **+ Add an item** and enter:
   - **Package name**: the `applicationId` from your `build.gradle.kts` (e.g. `pl.marrod.localmark`).
   - **SHA-1 certificate fingerprint**: run the command below to get it.

     ```powershell
     .\gradlew signingReport
     ```

3. Under **API restrictions** select **Restrict key**, then tick **Maps SDK for Android** (and any other APIs this key should cover).
4. Click **Save**.

> You can have separate keys for debug and release builds with their respective SHA-1 fingerprints. This is best practice so a leaked debug key cannot be used in production.

---

## The Google Secrets Gradle Plugin

Hardcoding API keys in source code (even in `BuildConfig` fields) risks accidental exposure if the code is shared or committed to a public repository. The **Secrets Gradle Plugin** solves this by reading keys from `local.properties` — a file that is excluded from version control by default — and injecting them as both `BuildConfig` fields and Manifest placeholders at build time.

### Step 1 — Add the plugin to your version catalog

```toml
# gradle/libs.versions.toml
[versions]
secretsGradlePlugin = "2.0.1"

[plugins]
secrets-gradle-plugin = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secretsGradlePlugin" }
```

### Step 2 — Apply the plugin in your app module

```kotlin
// app/build.gradle.kts
plugins {
    // ... other plugins
    alias(libs.plugins.secrets.gradle.plugin)
}
```

### Step 3 — Configure the plugin

Add the `secrets` block anywhere in `app/build.gradle.kts` (outside the `android` block):

```kotlin
// app/build.gradle.kts
secrets {
    // The file that holds your real keys — never commit this file.
    propertiesFileName = "local.properties"

    // Fallback file checked into VCS with safe placeholder values.
    // Used on CI machines that don't have local.properties, and by
    // teammates who haven't set up their own key yet.
    defaultPropertiesFileName = "local.defaults.properties"
}
```

### Step 4 — Store your keys in `local.properties`

`local.properties` lives in the **project root** (same level as `settings.gradle.kts`). It is already listed in `.gitignore` by Android Studio.

```properties
# local.properties  ← NEVER commit this file
MAPS_API_KEY=AIzaSy...your_real_key_here...
GEOCODING_API_KEY=AIzaSy...another_key_here...
GOOGLE_WEB_CLIENT_ID=123456789-abc...apps.googleusercontent.com
```

### Step 5 — Add placeholder values for CI / fresh checkouts

`local.defaults.properties` is committed to VCS and provides safe placeholder values so the project compiles even without a real key:

```properties
# local.defaults.properties  ← commit this file
MAPS_API_KEY=DEFAULT_API_KEY
GEOCODING_API_KEY=DEFAULT_API_KEY
GOOGLE_WEB_CLIENT_ID=DEFAULT_WEB_CLIENT_ID
```

### Step 6 — Use the keys

The plugin exposes every property from `local.properties` in two ways automatically:

#### As a Manifest placeholder

Use `${PROPERTY_NAME}` syntax directly in `AndroidManifest.xml`:

```xml
<!-- AndroidManifest.xml -->
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

#### As a `BuildConfig` field

Enable `buildConfig` generation in `build.gradle.kts`, then reference the key in Kotlin code:

```kotlin
// app/build.gradle.kts
android {
    buildFeatures {
        buildConfig = true   // required for BuildConfig fields to be generated
    }
}
```

```kotlin
// Kotlin code — anywhere in the app
val key = BuildConfig.MAPS_API_KEY
val geocodingKey = BuildConfig.GEOCODING_API_KEY
val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
```

> **Why are there two ways?** The Manifest placeholder is needed by the Maps SDK (it reads the key from the manifest at runtime). `BuildConfig` fields are needed for keys used in Kotlin code — like the Geocoding REST API key passed to Retrofit, or the Web Client ID passed to `GetSignInWithGoogleOption`.

### How it works internally

At build time the plugin:
1. Reads all properties from `local.properties` (falling back to `local.defaults.properties` for any missing key).
2. Injects each property as a `resValue` string (making it a Manifest placeholder).
3. Also injects each property as a `buildConfigField` (making it accessible as `BuildConfig.PROPERTY_NAME`).

No secrets ever appear in compiled source files — they are only present in the final APK/AAB in the forms the platform needs them (binary manifest, compiled `BuildConfig` class).

---

## Minimal Setup Checklist

1. Create a Google Cloud project and enable **Maps SDK for Android**.
2. Create an API key and restrict it to your app's package name + SHA-1 fingerprint.
3. Add `play.services.maps` and `maps.compose` to your Gradle dependencies.
4. Add the Secrets Gradle Plugin to your version catalog and apply it in `app/build.gradle.kts`.
5. Configure the `secrets { }` block pointing at `local.properties` and `local.defaults.properties`.
6. Put the real key in `local.properties` (not committed); put a placeholder in `local.defaults.properties` (committed).
7. Enable `buildConfig = true` in `android { buildFeatures { } }`.
8. Reference the key as `${MAPS_API_KEY}` in `AndroidManifest.xml` and as `BuildConfig.MAPS_API_KEY` in Kotlin code.
9. (Optional) Add `@OptIn(MapsComposeExperimentalApi::class)` for `MarkerComposable` and other experimental APIs.

