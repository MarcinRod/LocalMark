package pl.marrod.localmark.ui.screens.map

import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.marrod.localmark.R
import pl.marrod.localmark.data.DummyMarkerDataSource
import pl.marrod.localmark.data.model.MarkerCategory
import pl.marrod.localmark.data.model.MarkerData
import pl.marrod.localmark.data.model.PlaceResult
import pl.marrod.localmark.data.repository.AuthRepository
import pl.marrod.localmark.data.repository.GeocodingRepository
import pl.marrod.localmark.data.repository.MarkersRepository
import pl.marrod.localmark.data.repository.PlacesRepository
import pl.marrod.localmark.location.LocationProvider
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.ui.navigation.Destinations
import pl.marrod.localmark.util.toDegreesString
import java.util.UUID
import com.google.android.gms.location.FusedLocationProviderClient
import android.Manifest
import pl.marrod.localmark.ui.components.bottomsheet.AddMarkerBottomSheet
import pl.marrod.localmark.ui.components.bottomsheet.ViewMarkBottomSheet
import pl.marrod.localmark.ui.components.SearchRadiusCard
import pl.marrod.localmark.ui.components.LocalMarkMarker
import pl.marrod.localmark.ui.components.PlacesSearchBar
import pl.marrod.localmark.ui.components.AppToolbar


/**
 * Immutable snapshot of map **data** state derived from the four backing flows
 * ([MapViewModel.markers], [MapViewModel._thresholdedLocation], [MapViewModel._appliedFilters],
 * [MapViewModel._searchRadiusKm]) via the `combine` operator in [MapViewModel.mapDataState].
 *
 * These fields are **always recomputed** inside the combine lambda and must never
 * be set manually on `_uiFlags`.
 *
 * @property displayedMarkers Filtered, ready-to-render list of [MarkerData].
 * @property availableCategories Distinct [MarkerCategory] values in the raw marker list.
 * @property selectedCategories Mirror of active [MarkerCategory] filter chips.
 */
data class MapDataState(
    val displayedMarkers: List<MarkerData> = emptyList(),
    val availableCategories: List<MarkerCategory> = emptyList(),
    val selectedCategories: Set<MarkerCategory> = emptySet(),
)

/**
 * Immutable snapshot of **pure UI** state that is not derived from any flow.
 *
 * Backed by [MapViewModel._mapUiState] and exposed directly via [MapViewModel.mapUiState]
 * (no combine). Changes propagate instantly to the UI without triggering
 * [MapViewModel.applyFilters].
 *
 * ## Property groups
 * | Group | Properties |
 * |---|---|
 * | User | [displayName], [currentUserId] |
 * | Location / Permission | [locationPermissionGranted], [locationName] |
 * | Bottom sheets | [addBottomSheetVisible], [viewBottomSheetVisible], [pendingMarkerLocation], [isProcessing] |
 * | Search | [isSearchVisible], [searchQuery], [placeResults], [navigateToLocation] |
 * | Map overlay | [showRadiusOverlay] |
 */
data class MapUiState(
    // ── User ──────────────────────────────────────────────────────────────────
    val displayName: String = "",
    val currentUserId: String = "",

    // ── Location / Permission ─────────────────────────────────────────────────
    val locationPermissionGranted: Boolean = false,
    val locationName: String = "",


    // ── Bottom Sheets ─────────────────────────────────────────────────────────
    val addBottomSheetVisible: Boolean = false,
    val viewBottomSheetVisible: Boolean = false,
    val pendingMarkerLocation: LatLng? = null,
    val isProcessing: Boolean = false, // true while waiting for  marker upload to complete

    // ── Search ────────────────────────────────────────────────────────────────
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val placeResults: List<PlaceResult> = emptyList(),
    val navigateToLocation: LatLng? = null,

    // ── Map Overlay ───────────────────────────────────────────────────────────
    val showRadiusOverlay: Boolean = false,
)


/**
 * ViewModel for [MapScreen].
 *
 * Exposes two state flows consumed by the composable layer:
 * - [mapDataState] — map **data** (filtered markers, categories) derived via `combine`.
 * - [mapUiState] — pure **UI** flags (sheets, search, overlays) updated directly.
 *
 * ## State derivation
 * `_uiFlags` is not part of the `combine`, so opening/closing a bottom sheet
 * or toggling the search bar never triggers [applyFilters].
 *
 * @see MapDataState
 * @see MapUiState
 * @see pl.marrod.localmark.di.AppViewModelProvider
 */
class MapViewModel(
    savedStateHandle: SavedStateHandle,
    private val locationProvider: LocationProvider,
    private val placesRepository: PlacesRepository,
    private val geocodingRepository: GeocodingRepository,
    val authRepository: AuthRepository,
    val markersRepository: MarkersRepository
) : ViewModel() {

    // ── Backing Fields ────────────────────────────────────────────────────────

    /** Pure UI flags — bottom sheets, search, overlays. Exposed as [mapUiState]. */
    private val _mapUiState = MutableStateFlow(
        MapUiState(displayName = savedStateHandle.toRoute<Destinations.Map>().displayName ?: "")
    )

    /**
     * Latest device position emitted by [LocationProvider.locationFlow].
     * Exposed as [location] for direct consumption by [MapScreen] (camera animation).
     * Also fed into [mapDataState] via `combine` to recompute [MapDataState.displayedMarkers]
     * whenever the device moves.
     */
    private val _location = MutableStateFlow<LatLng?>(null)

    /**
     * Deduplicated location flow that only emits when the device has moved at least
     * [LOCATION_UPDATE_THRESHOLD_M] metres from the last emitted position.
     *
     * Used instead of the raw [_location] inside the [mapDataState] `combine` to prevent
     * excessive recomputation (and therefore excessive [applyFilters] calls) while the
     * user is walking or driving. [location] (the camera-animation flow) still reads the
     * raw [_location] so the "blue dot" remains smooth.
     */
    private val _thresholdedLocation = _location.distinctUntilChanged { old, new ->
        // Comparator that treats two LatLngs as equal if they are both null or within the threshold distance.
        if (old == null || new == null) old == new // both null → equal; one null → not equal
        else {
            val results = FloatArray(1)
            Location.distanceBetween(
                old.latitude, old.longitude,
                new.latitude, new.longitude,
                results,
            )
            results[0] < LOCATION_UPDATE_THRESHOLD_M
        }
    }

    /**
     * ID of the currently selected marker, or `null` when none is selected.
     * Kept separate from [_mapUiState] so that [selectedMarker] can be derived by
     * combining this with the live [markers] flow — ensuring the composable always
     * sees up-to-date [MarkerData] (e.g. fresh confirmations) without manual copies.
     */
    private val _selectedMarkerId = MutableStateFlow<String?>(null)

    /**
     * An empty set means "All categories" (no filter applied).
     * Toggled by [toggleCategoryFilter] and mirrored into [MapDataState.selectedCategories].
     */
    private val _appliedFilters = MutableStateFlow<Set<MarkerCategory>>(emptySet())

    /**
     * Backing flow for the search radius filter in kilometres.
     * Updated by [onRadiusChange] and exposed read-only via [searchRadiusKm].
     * Fed into the `combine` inside [mapDataState] so that changing the radius
     * immediately re-runs [applyFilters].
     */
    private val _searchRadiusKm = MutableStateFlow(5f)

    /**
     * Backing channel for one-shot error messages produced by repository operations
     * (add, delete, confirm, update). Uses `extraBufferCapacity = 1` so that a fast
     * failure is never silently dropped even if the collector is momentarily slow.
     * Exposed read-only via [errorEvent].
     */
    private val _errorEvent = MutableSharedFlow<UiText>(extraBufferCapacity = 1)

    /**
     * Handle for the coroutine that collects [LocationProvider.locationFlow].
     * Kept as a field so that [stopLocationUpdates] can cancel it without clearing
     * the entire [viewModelScope].
     */
    private var locationJob: Job? = null

    /**
     * Session token that groups a series of [PlacesRepository.searchPlaces] autocomplete
     * requests with the closing [PlacesRepository.fetchLatLng] call into a single billable
     * unit, as recommended by Google's
     * [session-based pricing](https://developers.google.com/maps/documentation/places/android-sdk/autocomplete#get-session-tokens).
     *
     * Created by [toggleSearch] when the bar opens; set to `null` when the bar closes
     * without selection (session abandoned) or after [onPlaceSelected] completes.
     */
    private var sessionToken: AutocompleteSessionToken? = null

    /**
     * Handle for the coroutine that calls [PlacesRepository.searchPlaces].
     * Cancelled in [onSearchQueryChange] before each new request to avoid racing results
     * from stale queries.
     */
    private var searchJob: Job? = null

    /**
     * Data source used exclusively during development / demo to populate the map
     * without a live backend. Constructed with default radii
     * ([DummyMarkerDataSource.MIN_RADIUS_M]..[DummyMarkerDataSource.MAX_RADIUS_M]).
     */
    private val dummyMarkerDataSource = DummyMarkerDataSource()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Resolve the current user's UID once and store it in state so it can be
        // passed explicitly to repository methods rather than having the repository
        // pull it from the auth layer itself.
        authRepository.currentUserId()?.let { uid ->
            _mapUiState.update { it.copy(currentUserId = uid) }
        }
    }

    // ── Exposed State ─────────────────────────────────────────────────────────

    /**
     * Read-only view of [_mapUiState]. Collected in [MapScreen] alongside [mapDataState].
     * Changes propagate instantly without running [applyFilters].
     */
    val mapUiState: StateFlow<MapUiState> = _mapUiState.asStateFlow()

    /**
     * Read-only view of [_location] collected inside [MapScreen] to drive the camera
     * "jump to my position" animation independently of the full [mapDataState] flow.
     */
    val location: StateFlow<LatLng?> = _location.asStateFlow()

    /**
     * Complete, unfiltered list of [MarkerData] observed from the repository.
     * The filtered and radius-trimmed view is exposed via
     * [MapDataState.displayedMarkers] in [mapDataState].
     */
    val markers: StateFlow<List<MarkerData>> = markersRepository.observeMarkers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Always-fresh view of the selected [MarkerData], driven by [MarkersRepository.observeMarker].
     *
     * Whenever [_selectedMarkerId] changes, `flatMapLatest` cancels the previous
     * Firestore listener and opens a new one for the new ID — or emits `null` immediately
     * when nothing is selected.
     *
     * Any remote update to the marker (confirmations, edits) is reflected here
     * automatically without any manual state patching.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMarker: StateFlow<MarkerData?> = _selectedMarkerId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else markersRepository.observeMarker(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /**
     * Convenience string for the `positionString` argument of [pl.marrod.localmark.ui.components.bottomsheet.AddMarkerBottomSheet].
     *
     * Returns [MarkerData.locationName] of [selectedMarker] when editing an existing marker,
     * or the reverse-geocoded [MapUiState.locationName] for a new marker.
     */
    val sheetLocationString: StateFlow<String> =
        combine(selectedMarker, _mapUiState) { marker, flags ->
            marker?.locationName ?: flags.locationName
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "",
        )

    /**
     * Read-only view of [_searchRadiusKm] consumed by [MapScreen] to drive the
     * radius slider UI and the [pl.marrod.localmark.ui.components.SearchRadiusCard] composable.
     */
    val searchRadiusKm: StateFlow<Float> = _searchRadiusKm.asStateFlow()

    /**
     * Read-only stream of [UiText] error events collected in [MapScreen] and displayed
     * as transient snackbar messages. Each emission is consumed once and never replayed.
     */
    val errorEvent: SharedFlow<UiText> = _errorEvent.asSharedFlow()

    /**
     * Derived map-data state combining the four backing flows.
     *
     * Re-emits **only** when markers, location, filters, or radius change —
     * pure UI events (sheet open/close, search toggle) no longer trigger a recompute.
     */
    val mapDataState: StateFlow<MapDataState> = combine(
        _searchRadiusKm, markers, _thresholdedLocation, _appliedFilters
    ) { radius, markers, loc, filters ->
        MapDataState(
            displayedMarkers = applyFilters(markers, filters, radius, loc),
            availableCategories = markers.map { it.category }.distinct(),
            selectedCategories = filters,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MapDataState(),
    )

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Applies [selectedCategories] and radius filters to [markers], returning the
     * subset that should be rendered on the map.
     *
     * - **Category filter**: all markers pass when [selectedCategories] is empty
     *   (the "All" chip is active). Otherwise only markers whose [MarkerData.category]
     *   is in the set are kept.
     * - **Radius filter**: skipped while [location] is `null` (no GPS fix yet — show
     *   everything). Distance is computed with [Location.distanceBetween] using the
     *   [MarkerData.location] coordinate and compared against `radiusKm * 1 000` metres.
     *
     * Called exclusively from the `combine` lambda in [mapDataState].
     */
    private fun applyFilters(
        markers: List<MarkerData>,
        selectedCategories: Set<MarkerCategory>,
        radiusKm: Float,
        location: LatLng?,
    ): List<MarkerData> = markers.filter { marker ->
        val categoryPass = selectedCategories.isEmpty() || marker.category in selectedCategories
        val radiusPass = if (location == null) true else {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                marker.location.latitude, marker.location.longitude,
                results,
            )
            results[0] <= radiusKm * 1_000f
        }
        categoryPass && radiusPass
    }

    // ── Location ──────────────────────────────────────────────────────────────

    /**
     * Begins collecting [LocationProvider.locationFlow] into [_location].
     *
     * Safe to call multiple times — a guard prevents duplicate collection when
     * [locationJob] is still active.
     *
     * Called by the [androidx.lifecycle.LifecycleEventObserver] registered in [MapScreen] on
     * [androidx.lifecycle.Lifecycle.Event.ON_RESUME], and also directly from [onPermissionResult]
     * when the user grants location access for the first time.
     *
     * Any [SecurityException] thrown by [LocationProvider.locationFlow] (permission
     * revoked mid-session) is caught and logged; the flow is not restarted.
     */
    fun startLocationUpdates() {
        // Avoid duplicate collection
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            locationProvider.locationFlow()
                .catch { e -> Log.e("MapViewModel", "Location error: ${e.message}") }
                .collect { latLng -> _location.value = latLng }
        }
    }

    /**
     * Cancels the active [locationJob], stopping [LocationProvider.locationFlow] collection.
     *
     * The cancellation propagates through the coroutine to `awaitClose` inside
     * [LocationProvider.locationFlow], which calls
     * [FusedLocationProviderClient.removeLocationUpdates] automatically — no cleanup
     * is needed here.
     *
     * Called by the [androidx.lifecycle.LifecycleEventObserver] in [MapScreen] on [androidx.lifecycle.Lifecycle.Event.ON_PAUSE].
     */
    fun stopLocationUpdates() {
        locationJob?.cancel() // cancels collection → triggers awaitClose
        locationJob = null
    }

    /**
     * Handles the result of the system location-permission dialog.
     *
     * Called from the `ActivityResultContracts.RequestMultiplePermissions` callback in [MapScreen],
     * passing `permissions.values.any { it }` as [granted].
     *
     * When [granted] is `true`:
     * 1. [MapUiState.locationPermissionGranted] is set to `true`, enabling the system
     *    "my location" dot on the map ([com.google.maps.android.compose.MapProperties.isMyLocationEnabled]).
     * 2. [startLocationUpdates] is called to begin continuous GPS streaming.
     * 3. If no live fix exists yet in [_location], [LocationProvider.getLastKnownLocation]
     *    is called to seed the camera with a cached position before the first
     *    [LocationProvider.locationFlow] emission arrives.
     *
     * @param granted `true` if [Manifest.permission.ACCESS_FINE_LOCATION] or
     *   [Manifest.permission.ACCESS_COARSE_LOCATION] was granted.
     */
    fun onPermissionResult(granted: Boolean) {
        _mapUiState.update { it.copy(locationPermissionGranted = granted) }
        if (granted) {
            // Start continuous updates — covers first composition where ON_RESUME
            // has already fired before the lifecycle observer was added.
            startLocationUpdates()
            if (_location.value == null) {
                viewModelScope.launch {
                    // Seed with cached location for instant camera positioning.
                    // Skipped if a live fix is already known (e.g. screen re-entered).
                    val last = locationProvider.getLastKnownLocation()
                    if (last != null) _location.value = last
                }
            }
        }
    }

    // ── Markers ───────────────────────────────────────────────────────────────

    /**
     * Persists [marker] via [MarkersRepository.addMarker] and closes the **Add Alert** bottom sheet.
     *
     * Sets [MapUiState.isProcessing] to `true` while the repository call is in flight so the UI
     * can show a loading indicator. On success, calls [hideAddBottomDialog]; on failure, emits an
     * error via [errorEvent]. The new marker appears in [MapDataState.displayedMarkers] automatically
     * once [MarkersRepository.observeMarkers] emits the updated list.
     *
     * The marker's [MarkerData.id], [MarkerData.authorName], and [MarkerData.authorId] are filled in
     * here so callers do not need to provide them.
     *
     * Called from [MapScreen]'s `onAddAlert` lambda, which is wired to the `onPost` callback
     * of [AddMarkerBottomSheet].
     *
     * @param marker  Partial [MarkerData] supplied by the form (title, category, location, etc.).
     * @param imageUri Optional URI of a photo to attach to the marker.
     */
    fun addMarker(marker: MarkerData, imageUri: Uri?) {
        _mapUiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = markersRepository.addMarker(
                marker.copy(
                    id = UUID.randomUUID().toString(),
                    authorName = _mapUiState.value.displayName,
                    authorId = _mapUiState.value.currentUserId,
                ),
                imageUri
            )
            _mapUiState.update { it.copy(isProcessing = false) }
            result.onSuccess { hideAddBottomDialog() }
            result.onFailure { e ->
                Log.w("MapViewModel", "addMarker failed: ${e.message}")
                _errorEvent.tryEmit(UiText.Resource(R.string.error_add_marker))
            }
        }
    }

    /**
     * Persists edits to [marker] via [MarkersRepository.updateMarker] and closes the
     * **Add Alert** bottom sheet.
     *
     * Sets [MapUiState.isProcessing] to `true` while the repository call is in flight.
     * On success, calls [hideAddBottomDialog]; on failure, emits an error via [errorEvent].
     * The updated marker is reflected in [selectedMarker] and [MapDataState.displayedMarkers]
     * automatically once the Firestore listener emits the change.
     *
     * Wired to [MapScreen]'s `onUpdateMarker` callback inside [AddMarkerBottomSheet].
     *
     * @param marker The edited [MarkerData]. Must have the same [MarkerData.id] as the
     *               original and belong to the current user.
     * @param uri    Optional URI of a new photo to replace the existing one, or `null` to
     *               keep the current image.
     */
    fun updateMarker(marker: MarkerData, uri: Uri?) {
        _mapUiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val result = markersRepository.updateMarker(
                currentUserId = _mapUiState.value.currentUserId,
                marker = marker,
                imageUri = uri
            )
            _mapUiState.update { it.copy(isProcessing = false) }
            result.onSuccess { hideAddBottomDialog() }
            result.onFailure { e ->
                Log.w("MapViewModel", "updateMarker failed: ${e.message}")
                _errorEvent.tryEmit(UiText.Resource(R.string.error_update_marker))
            }
        }
    }

    /**
     * Removes [marker] from the backend via [MarkersRepository.deleteMarker] and closes
     * the **View Alert** bottom sheet.
     *
     * Filters by [MarkerData.id] so that value-equality issues with [LatLng] do not
     * accidentally leave a stale pin on the map. On success, clears [_selectedMarkerId]
     * and hides the sheet; on failure, emits an error via [errorEvent].
     *
     * Called from [MapScreen]'s `onDeleteMarker` lambda, which is passed down to the
     * `onDelete` callback of [ViewMarkBottomSheet].
     *
     * @param marker The [MarkerData] to delete. Must belong to the current user.
     */
    fun deleteMarker(marker: MarkerData) {
        viewModelScope.launch {
            val result = markersRepository.deleteMarker(
                currentUserId = _mapUiState.value.currentUserId,
                marker = marker
            )
            result.onSuccess {
                _selectedMarkerId.value = null
                _mapUiState.update { it.copy(viewBottomSheetVisible = false) }
            }
            result.onFailure { e ->
                Log.w("MapViewModel", "deleteMarker failed: ${e.message}")
                _errorEvent.tryEmit(UiText.Resource(R.string.error_delete_marker))
            }
        }
    }

    /**
     * Toggles the current user's confirmation on [marker] via [MarkersRepository.confirmMarker].
     *
     * On failure, emits an error via [errorEvent]. No manual state patch is needed on success —
     * [selectedMarker] is derived from [MarkersRepository.observeMarker] and updates automatically
     * when Firestore reflects the new confirmation count.
     *
     * Wired to [MapScreen]'s `onConfirmMarker` callback inside [ViewMarkBottomSheet].
     *
     * @param marker The [MarkerData] to confirm. Must not be authored by the current user.
     */
    fun confirmMarker(marker: MarkerData) {
        val userId = _mapUiState.value.currentUserId
        viewModelScope.launch {
            val result = markersRepository.confirmMarker(currentUserId = userId, marker = marker)
            result.onFailure { e ->
                Log.w("MapViewModel", "confirmMarker failed: ${e.message}")
                _errorEvent.tryEmit(UiText.Resource(R.string.error_confirm_marker))
            }
            // No manual state patch needed — selectedMarker is derived from the
            // repository flow and will update automatically when the marker changes.
        }
    }

    /**
     * Generates [count] random [MarkerData] entries via [DummyMarkerDataSource.generate]
     * and would append them to the marker list.
     *
     * > **No-op since Firebase integration.** The method body is intentionally empty;
     * > real markers are now persisted through [MarkersRepository.addMarker] and observed
     * > via [markers]. This stub is kept for API compatibility with [MapScreen].
     *
     * @param origin Centre point for scatter generation — typically the current device location.
     * @param count  Number of markers to generate (default 5).
     */
    fun spawnDummyMarkers(origin: LatLng, count: Int = 5) {
        //NO OP after adding firebase:  _markers.update { it + dummyMarkerDataSource.generate(origin, count) }
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    /**
     * Updates [searchRadiusKm] when the user moves the radius slider
     * in [pl.marrod.localmark.ui.components.SearchRadiusCard].
     *
     * The new value is picked up immediately by the `combine` inside [mapDataState], which
     * calls [applyFilters] with the updated radius and re-emits [MapDataState.displayedMarkers].
     *
     * Wired to [MapScreen]'s `onRadiusChange` callback.
     *
     * @param km New radius value in kilometres (slider range defined in [SearchRadiusCard]).
     */
    fun onRadiusChange(km: Float) {
        _searchRadiusKm.update { km }
    }

    /**
     * Toggles the [category] chip in the filter bar:
     * - `null` → clears all filters ("All" chip tapped).
     * - Already selected → deselects it (removes from [_appliedFilters]).
     * - Not selected → selects it (adds to [_appliedFilters]).
     *
     * [_appliedFilters] is mirrored into [MapDataState.selectedCategories] and used by
     * [applyFilters] to compute [MapDataState.displayedMarkers].
     *
     * Wired to [MapScreen]'s `onCategorySelected` callback, triggered by tapping a chip
     * in the `LazyRow` of category filters inside [MapScreenContent].
     *
     * @param category The category to toggle, or `null` to reset all filters.
     */
    fun toggleCategoryFilter(category: MarkerCategory?) {
        _appliedFilters.update { current ->
            when (category) {
                null -> emptySet()                        // "All" chip → clear filters
                in current -> current - category          // already selected → deselect
                else -> current + category                // not selected → select
            }
        }
    }

    // ── Bottom Sheets ─────────────────────────────────────────────────────────

    /**
     * Opens the **Add Alert** bottom sheet ([AddMarkerBottomSheet]) for a **new** marker.
     *
     * Sets [MapUiState.addBottomSheetVisible] to `true` immediately so the sheet
     * animates in without waiting for geocoding. Then launches a background coroutine
     * that calls [GeocodingRepository.reverseGeocode] to populate
     * [MapUiState.locationName]:
     * - If [position] is non-null (map long-press), that coordinate is reverse-geocoded.
     * - If [position] is `null` (FAB tap), the current [_location] value is used as fallback.
     * - If geocoding fails or returns nothing, [LatLng.toDegreesString] is used instead.
     *
     * Wired to [MapScreen]'s `onAddDialogToggle(show = true, position)` callback.
     *
     * @param position Map coordinate of the long-press, or `null` when opened via FAB.
     */
    fun showAddBottomDialog(position: LatLng? = null) {
        _selectedMarkerId.value = null
        _mapUiState.update {
            it.copy(
                addBottomSheetVisible = true,
                pendingMarkerLocation = position,
                locationName = "",
            )
        }
        val locationToGeocode = position ?: _location.value
        locationToGeocode?.let { latLng ->
            viewModelScope.launch {
                val name = geocodingRepository.reverseGeocode(latLng)
                _mapUiState.update { it.copy(locationName = name ?: latLng.toDegreesString()) }
            }
        }
    }

    /**
     * Opens the **Add Alert** bottom sheet pre-populated with [marker]'s data for editing.
     *
     * Sets [_selectedMarkerId] to [marker]'s ID so [selectedMarker] streams the live
     * Firestore document, then shows the add sheet ([MapUiState.addBottomSheetVisible] = `true`)
     * and hides the view sheet ([MapUiState.viewBottomSheetVisible] = `false`). The existing
     * [MarkerData.locationName] is placed in [MapUiState.locationName] so the sheet displays
     * the already-resolved address without triggering a new geocoding request.
     *
     * Wired to [MapScreen]'s `onEditMarker` callback inside [ViewMarkBottomSheet].
     *
     * @param marker The [MarkerData] to edit. Must belong to the current user.
     */
    fun showEditBottomSheet(marker: MarkerData) {
        _selectedMarkerId.value = marker.id
        _mapUiState.update {
            it.copy(
                addBottomSheetVisible = true,
                pendingMarkerLocation = null,
                viewBottomSheetVisible = false,
                locationName = marker.locationName,
            )
        }
    }

    /**
     * Closes the **Add Alert** bottom sheet and clears the transient state
     * ([MapUiState.addBottomSheetVisible], [MapUiState.pendingMarkerLocation],
     * [MapUiState.isProcessing]).
     *
     * Called by [addMarker] and [updateMarker] after a successful submission, and by [MapScreen]'s
     * `onAddDialogToggle(show = false, _)` when the user dismisses the sheet.
     */
    fun hideAddBottomDialog() {
        _selectedMarkerId.value = null
        _mapUiState.update {
            it.copy(
                addBottomSheetVisible = false,
                pendingMarkerLocation = null,
                isProcessing = false,
            )
        }
    }

    /**
     * Opens the **View Alert** bottom sheet ([ViewMarkBottomSheet]) for [marker].
     *
     * Sets [MapUiState.viewBottomSheetVisible] to `true` and stores [marker]'s ID in
     * [_selectedMarkerId] so [selectedMarker] begins streaming live updates from
     * [MarkersRepository.observeMarker], ensuring [ViewMarkBottomSheet] always shows
     * the latest data (e.g. updated confirmation count).
     *
     * Called from [MapScreen]'s `onMarkerClick` callback, triggered when the user taps
     * an [LocalMarkMarker] composable on the map.
     *
     * @param marker The [MarkerData] whose details should be displayed.
     */
    fun showViewBottomSheet(marker: MarkerData) {
        _selectedMarkerId.value = marker.id
        _mapUiState.update { it.copy(viewBottomSheetVisible = true) }
    }

    /**
     * Closes the **View Alert** bottom sheet and clears [_selectedMarkerId], causing
     * [selectedMarker] to emit `null` and stop the Firestore listener.
     *
     * Called from [MapScreen]'s `onViewDismiss` callback when the user swipes the sheet
     * down or taps the scrim.
     */
    fun hideViewBottomSheet() {
        _selectedMarkerId.value = null
        _mapUiState.update { it.copy(viewBottomSheetVisible = false) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Toggles the [PlacesSearchBar] visibility and manages the [AutocompleteSessionToken]
     * lifecycle.
     *
     * - **Opening the bar**: a fresh [AutocompleteSessionToken] is created to start a new
     *   billing session.
     * - **Closing the bar**: the token is set to `null` (session abandoned), and
     *   [MapUiState.searchQuery] / [MapUiState.placeResults] are cleared.
     *
     * Wired to [MapScreen]'s `onSearchToggle` callback, triggered by the search icon in
     * [AppToolbar].
     */
    fun toggleSearch() {
        sessionToken = if (!_mapUiState.value.isSearchVisible) {
            AutocompleteSessionToken.newInstance() // new session starts
        } else {
            null // session abandoned
        }
        _mapUiState.update {
            if (it.isSearchVisible) {
                it.copy(isSearchVisible = false, searchQuery = "", placeResults = emptyList())
            } else {
                it.copy(isSearchVisible = true)
            }
        }
    }

    /**
     * Handles a keystroke in the [PlacesSearchBar], updating [MapUiState.searchQuery]
     * and firing a [PlacesRepository.searchPlaces] request.
     *
     * Any in-flight [searchJob] is cancelled first to avoid delivering stale results from
     * a previous query. The results are stored in [MapUiState.placeResults] and rendered
     * by `PlaceSearchResultsList` in [MapScreenContent].
     *
     * Wired to [MapScreen]'s `onSearchQueryChange` callback.
     *
     * @param query The full text currently in the search field.
     */
    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        _mapUiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _mapUiState.update { it.copy(placeResults = emptyList()) }
            return
        } else {
            _mapUiState.update { it.copy(isSearchVisible = true) }
        }
        searchJob = viewModelScope.launch {
            val results = placesRepository.searchPlaces(query, sessionToken!!)
            _mapUiState.update { it.copy(placeResults = results) }
        }
    }

    /**
     * Resolves [placeId] to a [LatLng] via [PlacesRepository.fetchLatLng] and sets
     * [MapUiState.navigateToLocation] so [MapScreen] can animate the camera.
     *
     * The result is observed in [MapScreen] via a `LaunchedEffect` on
     * [MapUiState.navigateToLocation]; once the camera animation is triggered the
     * composable calls [onNavigationConsumed] to reset the field.
     *
     * Wired to [MapScreen]'s `onPlaceSelected` callback, triggered when the user taps a
     * row in `PlaceSearchResultsList`.
     *
     * @param placeId Unique Google Place identifier from [PlaceResult.placeId].
     */
    fun onPlaceSelected(placeId: String) {
        viewModelScope.launch {
            val latLng = placesRepository.fetchLatLng(placeId, sessionToken!!)
            if (latLng != null) {
                _mapUiState.update { it.copy(navigateToLocation = latLng) }
            }
        }
    }

    /**
     * Resets [MapUiState.navigateToLocation] to `null` after the camera animation
     * triggered by [onPlaceSelected] has been initiated in [MapScreen].
     *
     * Must be called from the `onNavigationConsumed` callback in [MapScreen] to prevent
     * the `LaunchedEffect` from re-triggering the animation on subsequent recompositions.
     */
    fun onNavigationConsumed() {
        _mapUiState.update { it.copy(navigateToLocation = null) }
    }

    // ── Map Overlay ───────────────────────────────────────────────────────────

    /**
     * Toggles [MapUiState.showRadiusOverlay], which controls the animated `Circle`
     * composable drawn around the current device location in [MapScreenContent].
     *
     * The circle visualises the active [searchRadiusKm] value so the user can see
     * the filter boundary on the map. Wired to [MapScreen]'s `onRadiusOverlayToggle` callback.
     */
    fun toggleRadiusOverlay() {
        _mapUiState.update { it.copy(showRadiusOverlay = !it.showRadiusOverlay) }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Signs the current user out via [AuthRepository.signOut].
     *
     * After sign-out the navigation host in [MapScreen] should redirect the user to the
     * authentication screen. No state is modified here — the auth state change is observed
     * elsewhere in the nav graph.
     */
    fun signOut() {
        authRepository.signOut()
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        /**
         * Minimum distance in metres the device must travel before [_thresholdedLocation]
         * emits a new value and triggers a [mapDataState] recomputation.
         * 50 m is small enough that filter boundaries stay accurate while eliminating
         * GPS jitter and micro-movements that would otherwise flood the combine pipeline.
         */
        private const val LOCATION_UPDATE_THRESHOLD_M = 50f
    }
}
