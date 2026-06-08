package pl.marrod.localmark.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.di.AppViewModelProvider
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.ui.screens.map.MapUiState
/**
 * Wrapper around the Google Play Services [FusedLocationProviderClient] that provides
 * coroutine-friendly APIs for both continuous location updates and one-shot last-known-location
 * queries.
 *
 * **Instantiation**
 *
 * A single instance is created lazily inside [AppContainer] and
 * injected into [MapViewModel] via
 * [AppViewModelProvider].
 *
 * **Usage in [MapViewModel]**
 *
 * | ViewModel function | Provider call |
 * |---|---|
 * | `startLocationUpdates()` | [locationFlow] — collected into `_location` while the screen is resumed |
 * | `stopLocationUpdates()` | cancels the flow collection, which triggers [FusedLocationProviderClient.removeLocationUpdates] via [kotlinx.coroutines.channels.awaitClose] |
 * | `onPermissionResult(granted = true)` | [getLastKnownLocation] — seeds the camera with a cached fix before the first live update arrives |
 *
 * Both [locationFlow] and [getLastKnownLocation] guard themselves with [hasLocationPermission];
 * they return no data rather than throwing if permission is missing.
 *
 * @param context Application context used to obtain the [FusedLocationProviderClient]
 *                and to check runtime permissions.
 */
class LocationProvider(
    private val context: Context
) {
    /**
     * Entry point to the Google Play Services Fused Location Provider.
     *
     * The fused provider intelligently combines GPS, Wi-Fi, Bluetooth, and cell-network
     * signals to produce the most accurate location possible while minimising battery
     * consumption. Compared to querying GPS directly, it also handles sensor fusion
     * transparently and respects system-level battery-saver restrictions.
     *
     * **NOTE: You can get GPS data directly using the Android [android.location.LocationManager] API.**
     *
     * Obtained once via [LocationServices.getFusedLocationProviderClient] and reused for
     * the lifetime of the [LocationProvider] instance (which is itself a singleton inside
     * [AppContainer]).
     *
     * Used in two ways:
     * - [FusedLocationProviderClient.requestLocationUpdates] / [FusedLocationProviderClient.removeLocationUpdates]
     *   inside [locationFlow] for continuous streaming updates driven by [locationRequest].
     * - [FusedLocationProviderClient.lastLocation] inside [getLastKnownLocation] for a
     *   single cached-position read that requires no active request.
     */
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * [LocationRequest] configuration used for all continuous updates emitted by [locationFlow].
     *
     * ### Priority
     * [Priority.PRIORITY_HIGH_ACCURACY] instructs the fused provider to use GPS as the
     * primary source, supplemented by Wi-Fi and cell-network data. This gives the most
     * precise [LatLng] for map marker placement and radius-filter distance calculations
     * in [MapViewModel], at the cost of higher battery
     * drain compared to [Priority.PRIORITY_BALANCED_POWER_ACCURACY].
     *
     * ### Interval
     * The **target interval** is 10 000 ms (10 s). The system treats this as a hint and
     * may deliver updates less frequently when the device is in battery-saver mode or
     * when no better fix is available. This is intentionally conservative — the app only
     * needs a reasonably fresh position to filter markers and keep the FAB "my location"
     * button accurate; sub-second tracking is not required.
     *
     * ### Min update interval
     * The **fastest interval** is 5 000 ms (5 s). Even if another app is requesting rapid
     * updates and the fused provider produces them more quickly, this floor ensures the
     * app's [locationFlow] collector is not overwhelmed with redundant emissions.
     *
     * ### Granularity
     * [Granularity.GRANULARITY_PERMISSION_LEVEL] tells the provider to automatically cap
     * precision to what the user permitted:
     * - `ACCESS_FINE_LOCATION` granted → full GPS precision (metres).
     * - Only `ACCESS_COARSE_LOCATION` granted → approximate city-block precision (~1 km).
     * This avoids the need to branch on permission level in application code.
     *
     * ### Accurate location
     * `setWaitForAccurateLocation(true)` delays the very first emission until the provider
     * has a high-confidence fix. Without this, the first update often carries a coarse
     * network-based position that could place the initial camera in the wrong neighbourhood.
     */
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10_000L
    ).apply {
        setMinUpdateIntervalMillis(5_000L)
        setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
        setWaitForAccurateLocation(true)
    }.build()

    /**
     * Returns a cold [Flow] that emits a new [LatLng] for every location update delivered
     * by the [FusedLocationProviderClient].
     *
     * The flow is backed by a [kotlinx.coroutines.flow.callbackFlow] wrapping a
     * [LocationCallback]. When the collector is cancelled (e.g. the ViewModel is cleared or
     * [MapViewModel.stopLocationUpdates] is called),
     * [kotlinx.coroutines.channels.awaitClose] removes the callback automatically via
     * [FusedLocationProviderClient.removeLocationUpdates].
     *
     * If location permission is not granted the flow closes immediately with a
     * [SecurityException], which is caught and logged in
     * [MapViewModel.startLocationUpdates].
     *
     * Updates are delivered on [android.os.Looper.getMainLooper] as required by the
     * [FusedLocationProviderClient] API; the resulting [LatLng] values are safe to pass
     * to Compose state from any coroutine.
     *
     * @return A [Flow] of [LatLng] values; never completes unless collection is cancelled
     *         or permission is missing.
     */
    @SuppressLint("MissingPermission")
    fun locationFlow(): Flow<LatLng> {
        val callbackFlow = callbackFlow {
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
            //  location updates are received in the locationCallback, which emits them into the flow with trySend.
            //  The callback is registered with the fusedLocationClient, and awaitClose ensures it is unregistered
            //  when the flow collection is cancelled.
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Called when the Flow is cancelled (e.g. ViewModel cleared)
            awaitClose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
        return callbackFlow
    }

    /**
     * Returns `true` if either [android.Manifest.permission.ACCESS_FINE_LOCATION] or
     * [android.Manifest.permission.ACCESS_COARSE_LOCATION] has been granted at runtime.
     *
     * Used as a pre-flight guard inside both [locationFlow] and [getLastKnownLocation].
     * The result is also surfaced to the UI through
     * [MapUiState.locationPermissionGranted] (set by
     * [MapViewModel.onPermissionResult]).
     *
     * @return `true` if at least one location permission is granted, `false` otherwise.
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns the last known [LatLng] from the fused location provider, or `null` if
     * permission is not granted or no cached location is available yet.
     *
     * This is a one-shot suspending call — use [locationFlow] for continuous updates.
     *
     * Called by [MapViewModel.onPermissionResult]
     * (when permission is just granted) to seed
     * [MapViewModel]'s `_location` with a cached fix,
     * allowing the map camera to jump to the user's vicinity before the first live update
     * from [locationFlow] arrives.
     *
     * @return The last known [LatLng], or `null` if unavailable.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        val location = fusedLocationClient.lastLocation.await()
        return location?.let { LatLng(it.latitude, it.longitude) }
    }
}