package pl.marrod.localmark.ui.screens.map

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.log2

/**
 * Stable holder for map camera state and camera-control actions.
 *
 * Created and remembered by [rememberMapCameraState], which also wires up all
 * [LaunchedEffect]s that drive the camera (initial fly-in, idle detection,
 * place navigation, and auto-zoom to search radius).
 *
 * All animate* / zoom methods launch their animations on the [CoroutineScope]
 * provided at construction time, so callers do not need to manage coroutines.
 *
 */
@Stable
class MapCameraState(
    val cameraPositionState: CameraPositionState,
    private val scope: CoroutineScope,
) {
    /** `true` once [onMapLoaded] has been called by the [com.google.maps.android.compose.GoogleMap] callback. */
    var isMapLoaded by mutableStateOf(false)
        private set

    fun onMapLoaded() {
        isMapLoaded = true
    }

    /** Resets bearing and tilt to north-up, preserving zoom and target. */
    fun animateToNorth() = scope.launch {
        val cur = cameraPositionState.position
        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(cur.target)
                    .zoom(cur.zoom)
                    .bearing(0f)
                    .tilt(0f)
                    .build()
            ),
            durationMs = 500,
        )
    }

    /** Flies the camera to [location] at zoom 15. */
    fun animateToLocation(location: LatLng) = scope.launch {
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(location, 15f),
            durationMs = 800,
        )
    }

    fun zoomIn() = scope.launch {
        cameraPositionState.animate(CameraUpdateFactory.zoomIn(), durationMs = 300)
    }

    fun zoomOut() = scope.launch {
        cameraPositionState.animate(CameraUpdateFactory.zoomOut(), durationMs = 300)
    }
}

/**
 * Creates and remembers a [MapCameraState], wiring up all camera-driving
 * side effects as [LaunchedEffect]s:
 *
 * - **Initial fly-in** — animates to [currentLocation] once the map is loaded.
 * - **Idle detection** — calls [onCameraIdle] when the camera stops moving.
 * - **Place navigation** — animates to [navigateToLocation] and calls [onNavigationConsumed].
 * - **Auto-zoom** — zooms out to fit [searchRadius] inside the visible bounds.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun rememberMapCameraState(
    currentLocation: LatLng?,
    searchRadius: Float,
    navigateToLocation: LatLng?,
    onCameraIdle: (LatLng, Float) -> Unit,
    onNavigationConsumed: () -> Unit,
): MapCameraState {
    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()
    val state = remember { MapCameraState(cameraPositionState, scope) }

    // ── Initial fly-in ────────────────────────────────────────────────────────
    // rememberSaveable ensures this flag survives configuration changes (rotation).
    // Without it, hasInitializedCamera resets to false on rotation and the fly-in
    // fires again, overriding the camera position that rememberCameraPositionState
    // already restored from its own rememberSaveable bundle.
    var hasInitializedCamera by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isMapLoaded, currentLocation) {
        if (state.isMapLoaded && currentLocation != null && !hasInitializedCamera) {
            hasInitializedCamera = true
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(currentLocation, 15f),
                durationMs = 1_200,
            )
        }
    }

    // ── Idle detection ────────────────────────────────────────────────────────
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collect { isMoving ->
                if (!isMoving) {
                    val position = cameraPositionState.position
                    onCameraIdle(position.target, position.zoom)
                }
            }
    }

    // ── Navigate to selected place ────────────────────────────────────────────
    LaunchedEffect(navigateToLocation) {
        navigateToLocation?.let { latLng ->
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(latLng, 15f),
                durationMs = 900,
            )
            onNavigationConsumed()
        }
    }

    // ── Auto-zoom out to fit search radius ────────────────────────────────────────
    // Computes the distance from the current location to each visible edge and
    // zooms out if the radius circle would extend beyond the screen.
    LaunchedEffect(searchRadius ) {
        if (!state.isMapLoaded || currentLocation == null) return@LaunchedEffect
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
            ?: return@LaunchedEffect

        val dist = FloatArray(1)
        Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, bounds.northeast.latitude, currentLocation.longitude, dist)
        val distNorth = dist[0]
        Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, bounds.southwest.latitude, currentLocation.longitude, dist)
        val distSouth = dist[0]
        Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, currentLocation.latitude, bounds.northeast.longitude, dist)
        val distEast = dist[0]
        Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, currentLocation.latitude, bounds.southwest.longitude, dist)
        val distWest = dist[0]

        val minEdgeDistM = minOf(distNorth, distSouth, distEast, distWest)
        val radiusM = searchRadius * 1_000f

        if (radiusM > minEdgeDistM) {
            val zoomDelta = log2(minEdgeDistM.toDouble() / (radiusM * 1.15))
            val targetZoom = (cameraPositionState.position.zoom + zoomDelta).toFloat().coerceIn(1f, 21f)
            cameraPositionState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(cameraPositionState.position.target)
                        .zoom(targetZoom)
                        .bearing(cameraPositionState.position.bearing)
                        .tilt(cameraPositionState.position.tilt)
                        .build()
                ),
                durationMs = 700,
            )
        }
    }

    return state
}

