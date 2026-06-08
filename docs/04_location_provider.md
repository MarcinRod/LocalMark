# Location Provider

## What is it?

The **Fused Location Provider** (part of Google Play Services) intelligently combines GPS, Wi-Fi, Bluetooth, and cell-network signals to produce the most accurate location possible while minimising battery consumption. It is the recommended alternative to querying GPS directly through Android's `LocationManager`.

> **Note:** You _can_ use `android.location.LocationManager` to get GPS data directly, but you would then have to handle sensor fusion, battery trade-offs, and permission differences yourself. The Fused Location Provider does all of that transparently.

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.play.services.location)
```

Required manifest permissions (declared in `AndroidManifest.xml`):

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Both permissions are **runtime permissions** (dangerous), so they must be requested from the user at runtime on Android 6+.

---

## Core Concepts

### `FusedLocationProviderClient`

The entry point to the Fused Location Provider API:

```kotlin
private val fusedLocationClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(context)
```

It is obtained once and reused for the lifetime of `LocationProvider` (which is a singleton inside `AppContainer`).

### `LocationRequest`

Configures the frequency and accuracy of location updates:

```kotlin
private val locationRequest = LocationRequest.Builder(
    Priority.PRIORITY_HIGH_ACCURACY,  // use GPS as primary source
    10_000L                           // target update interval: 10 s
).apply {
    setMinUpdateIntervalMillis(5_000L)                          // fastest allowed: 5 s
    setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)   // auto-cap to user's permission
    setWaitForAccurateLocation(true)                           // skip the first coarse fix
}.build()
```

| Parameter | Value | Reason |
|---|---|---|
| `Priority.PRIORITY_HIGH_ACCURACY` | GPS + Wi-Fi + Cell | Most precise fix for placing markers |
| `10_000L` ms interval | 10 s | Conservative — the app only needs a reasonably fresh position |
| `5_000L` ms min interval | 5 s | Prevents flooding when another app requests faster updates |
| `GRANULARITY_PERMISSION_LEVEL` | automatic | Full precision for fine permission, ~1 km for coarse |
| `setWaitForAccurateLocation(true)` | delay first emission | Avoids jumping to a wrong neighbourhood on startup |

### `LocationCallback`

A callback object whose `onLocationResult` is invoked on the specified `Looper` every time a new location fix arrives:

```kotlin
val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
        result.lastLocation?.let { location ->
            // location.latitude / location.longitude
        }
    }
}
```

### `callbackFlow` — converting callbacks to a `Flow`

`callbackFlow` is the Kotlin coroutines builder designed specifically for wrapping callback-based APIs into a cold `Flow`. The location callback is registered when the flow is collected and automatically removed when collection is cancelled via `awaitClose`:

```kotlin
fun locationFlow(): Flow<LatLng> = callbackFlow {
    if (!hasLocationPermission()) {
        close(SecurityException("Location permission not granted"))
        return@callbackFlow
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                trySend(LatLng(location.latitude, location.longitude))
            }
        }
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()   // deliver callbacks on the main thread
    )

    awaitClose {
        // Called when the Flow collector is cancelled (e.g. ViewModel cleared)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
```

`trySend` is used (instead of `send`) because `LocationCallback.onLocationResult` is a non-suspending callback; `trySend` is non-blocking and drops the emission if the channel buffer is full.

### One-shot last-known location

For a fast initial camera seed (before the first live GPS fix arrives), the cached last-known position can be read without starting continuous updates:

```kotlin
suspend fun getLastKnownLocation(): LatLng? {
    if (!hasLocationPermission()) return null
    val location = fusedLocationClient.lastLocation.await()
    return location?.let { LatLng(it.latitude, it.longitude) }
}
```

`fusedLocationClient.lastLocation` is a `Task<Location>` — `.await()` (from `kotlinx-coroutines-play-services`) suspends the coroutine until the task completes.

---

## How the app uses it

### `LocationProvider` class

A single wrapper class instantiated as a lazy singleton in `AppContainer`:

```kotlin
class LocationProvider(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = …   // configured as above

    fun hasLocationPermission(): Boolean { … }
    fun locationFlow(): Flow<LatLng> { … }
    suspend fun getLastKnownLocation(): LatLng? { … }
}
```

### Lifecycle-aware updates in `MapViewModel`

The ViewModel collects `locationFlow()` only while the screen is resumed, preventing unnecessary battery drain when the app is in the background:

```kotlin
private var locationJob: Job? = null

fun startLocationUpdates() {
    if (locationJob?.isActive == true) return   // no duplicate collection
    locationJob = viewModelScope.launch {
        locationProvider.locationFlow()
            .catch { e -> Log.e("MapViewModel", "Location error: ${e.message}") }
            .collect { latLng -> _location.value = latLng }
    }
}

fun stopLocationUpdates() {
    locationJob?.cancel()  // cancels collection → triggers awaitClose → removeLocationUpdates
    locationJob = null
}
```

`MapScreen` wires these to lifecycle events via `DisposableEffect`:

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> viewModel.startLocationUpdates()
            Lifecycle.Event.ON_PAUSE  -> viewModel.stopLocationUpdates()
            else -> {}
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

### Permission request

Location permissions are requested at screen launch using the Compose `ActivityResultContracts` API:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    viewModel.onPermissionResult(permissions.values.any { it })
}
LaunchedEffect(Unit) {
    permissionLauncher.launch(arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ))
}
```

### Location threshold deduplication

To avoid re-running the marker filter on every tiny GPS jitter, the ViewModel uses `distinctUntilChanged` with a distance threshold:

```kotlin
private val _thresholdedLocation = _location.distinctUntilChanged { old, new ->
    if (old == null || new == null) old == new
    else {
        val results = FloatArray(1)
        Location.distanceBetween(
            old.latitude, old.longitude,
            new.latitude, new.longitude,
            results,
        )
        results[0] < LOCATION_UPDATE_THRESHOLD_M  // 50 m
    }
}
```

The raw `_location` still feeds the camera animation for smooth movement; only `_thresholdedLocation` feeds the `combine` that recomputes the displayed marker list.

---

## Minimal Setup Checklist

1. Add `play.services.location` to Gradle dependencies.
2. Declare `ACCESS_FINE_LOCATION` and/or `ACCESS_COARSE_LOCATION` in `AndroidManifest.xml`.
3. Request the permissions at runtime (use `ActivityResultContracts.RequestMultiplePermissions`).
4. Obtain `FusedLocationProviderClient` via `LocationServices.getFusedLocationProviderClient(context)`.
5. Build a `LocationRequest` with the desired priority and interval.
6. Wrap `requestLocationUpdates`/`removeLocationUpdates` in `callbackFlow { … awaitClose { … } }` to get a Kotlin `Flow`.
7. Start / stop updates based on the screen lifecycle (ON_RESUME / ON_PAUSE).

