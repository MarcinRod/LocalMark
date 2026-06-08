package pl.marrod.localmark.ui.screens.map

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.DefaultMapProperties
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import pl.marrod.localmark.R
import pl.marrod.localmark.data.model.MarkerCategory
import pl.marrod.localmark.data.model.MarkerData
import pl.marrod.localmark.di.AppViewModelProvider
import pl.marrod.localmark.ui.components.LocalMarkMarker
import pl.marrod.localmark.ui.components.bottomsheet.ViewMarkBottomSheet
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.ui.helpers.UiTextThrowable
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.Spacing

/**
 * Main screen displaying the map with markers, search, and filter controls.
 * Orchestrates state from [MapViewModel] and passes it down to stateless composables.
 */
@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = AppViewModelProvider.factory),
    onSignOut: () -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    val mapData by viewModel.mapDataState.collectAsStateWithLifecycle()
    val uiFlags by viewModel.mapUiState.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val sheetLocationName by viewModel.sheetLocationString.collectAsStateWithLifecycle()
    val searchRadius by viewModel.searchRadiusKm.collectAsStateWithLifecycle()
    val selectedMarker by viewModel.selectedMarker.collectAsStateWithLifecycle()

    var processingResult by remember { mutableStateOf<Result<UiText>?>(null) }

    // ── Location permission ───────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onPermissionResult(permissions.values.any { it })
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // ── Lifecycle: start / stop location updates ──────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startLocationUpdates()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopLocationUpdates()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current


    // Clear the result text whenever a new operation starts
    LaunchedEffect(uiFlags.isProcessing) {
        if (uiFlags.isProcessing) processingResult = null
    }

    LaunchedEffect(Unit) {
        // Shared flow of error messages from the ViewModel, shown as snackbars.
        viewModel.errorEvent.collect { errorText ->
            processingResult = Result.failure(UiTextThrowable(errorText))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
    MapScreenContent(
        mapData = mapData,
        uiFlags = uiFlags,
        markerProcessingResult = processingResult,
        selectedMarker = selectedMarker,
        currentLocation = location,
        locationName = sheetLocationName,
        searchRadius = searchRadius,
        modifier = Modifier.fillMaxSize(),
        mapProperties = MapProperties(
            isMyLocationEnabled = uiFlags.locationPermissionGranted,
            mapType = MapType.NORMAL,
            mapStyleOptions = if (isDarkTheme) MapStyleOptions.loadRawResourceStyle(
                LocalContext.current, R.raw.map_style_dark
            ) else null,
        ),
        onSignOut = { viewModel.signOut(); onSignOut() },
        onRadiusChange = { viewModel.onRadiusChange(it) },
        onCategorySelected = { viewModel.toggleCategoryFilter(it) },
        onAddMarker = { markerData, uri -> viewModel.addMarker(markerData, uri) },
        onEditMarker = { markerData, uri -> viewModel.updateMarker(markerData, uri) },
        onShowEditFrom = { viewModel.showEditBottomSheet(it) },
        onAddDialogToggle = { show, position ->
            if (show) viewModel.showAddBottomDialog(position = position)
            else viewModel.hideAddBottomDialog()
        },
        onSpawnDummy = { viewModel.spawnDummyMarkers(it, 5) },
        onSearchToggle = { viewModel.toggleSearch() },
        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
        onPlaceSelected = { viewModel.onPlaceSelected(it) },
        onNavigationConsumed = { viewModel.onNavigationConsumed() },
        onMarkerClick = { viewModel.showViewBottomSheet(it) },
        onViewDismiss = { viewModel.hideViewBottomSheet() },
        onDeleteMarker = { viewModel.deleteMarker(it) },
        onConfirmMarker = { viewModel.confirmMarker(it) },
        onRadiusOverlayToggle = { viewModel.toggleRadiusOverlay() },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MapScreenContent  (stateless orchestrator)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreenContent(
    mapData: MapDataState,
    uiFlags: MapUiState,
    markerProcessingResult: Result<UiText>?,
    selectedMarker: MarkerData?,
    currentLocation: LatLng?,
    locationName: String?,
    searchRadius: Float,
    modifier: Modifier = Modifier,
    mapProperties: MapProperties = DefaultMapProperties,
    onRadiusChange: (Float) -> Unit,
    onSignOut: () -> Unit,
    onCategorySelected: (category: MarkerCategory?) -> Unit,
    onAddDialogToggle: (show: Boolean, position: LatLng?) -> Unit,
    onAddMarker: (MarkerData, Uri?) -> Unit,
    onEditMarker: (MarkerData, Uri?) -> Unit,
    onShowEditFrom: (MarkerData) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlaceSelected: (placeId: String) -> Unit,
    onNavigationConsumed: () -> Unit,
    onMarkerClick: (MarkerData) -> Unit,
    onViewDismiss: () -> Unit,
    onDeleteMarker: (MarkerData) -> Unit,
    onConfirmMarker: (MarkerData) -> Unit,
    onRadiusOverlayToggle: () -> Unit,
    onCameraIdle: (LatLng, Float) -> Unit = { _, _ -> },
    onSpawnDummy: (LatLng) -> Unit = {},
) {
    // ── Hoisted state holders ─────────────────────────────────────────────────
    val cameraState = rememberMapCameraState(
        currentLocation = currentLocation,
        searchRadius = searchRadius,
        navigateToLocation = uiFlags.navigateToLocation,
        onCameraIdle = onCameraIdle,
        onNavigationConsumed = onNavigationConsumed,
    )
    val cameraLauncher = rememberCameraLauncher()

    // ── Animated radius overlay pulse ─────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "radiusOverlay")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radiusProgress",
    )

    // ── Bottom sheets (outside Box so they overlay the entire screen) ─────────
    if (uiFlags.addBottomSheetVisible) {
        AddEditBottomSheet(
            uiFlags = uiFlags,
            processingResult = markerProcessingResult, 
            currentLocation = currentLocation,
            currentLocationName = locationName,
            cameraLauncher = cameraLauncher,
            onDismiss = { if (!uiFlags.isProcessing) onAddDialogToggle(false, null) },
            onAddMarker = { markerData ->
                // Pass the captured photo URI (if any) along with the marker data when the user confirms adding a new marker.
                onAddMarker(markerData, cameraLauncher.photoUri)
            },
            selectedMarker = selectedMarker,
            onEdit = { markerData -> onEditMarker(markerData, cameraLauncher.photoUri) },
        )
    }
    if (uiFlags.viewBottomSheetVisible && selectedMarker != null) {
        ViewMarkBottomSheet(
            marker = selectedMarker,
            viewerId = uiFlags.currentUserId,
            currentLocation = currentLocation,
            onDismissRequest = onViewDismiss,
            onDelete = { onDeleteMarker(selectedMarker) },
            onConfirm = { onConfirmMarker(selectedMarker) },
            onEdit = { onShowEditFrom(selectedMarker) },
        )
    }


    Box(modifier = modifier.fillMaxSize()) {

        // ── Google Map ────────────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState.cameraPositionState,
            properties = mapProperties,
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapLoaded = { cameraState.onMapLoaded() },
            onMapLongClick = { latLng -> onAddDialogToggle(true, latLng) },
        ) {
            mapData.displayedMarkers.forEach { marker ->
                key(marker.id) {
                    val markerState = rememberUpdatedMarkerState(position = marker.location)
                    MarkerComposable(
                        state = markerState,
                        onClick = { onMarkerClick(marker); false },
                    ) {
                        LocalMarkMarker(
                            icon = marker.category.icon,
                            accentColor = marker.category.accentColor,
                        )
                    }
                }
            }

            if (uiFlags.showRadiusOverlay && currentLocation != null) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val alpha = (1f - animProgress).coerceIn(0f, 1f)
                Circle(
                    center = currentLocation,
                    radius = (animProgress * searchRadius * 1_000f).toDouble(),
                    fillColor = primaryColor.copy(alpha = 0.18f * alpha),
                    strokeColor = primaryColor.copy(alpha = 0.80f * alpha),
                    strokeWidth = 4f,
                )
            }
        }

        // ── Right-side controls ───────────────────────────────────────────────
        MapControlsPanel(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(64.dp)
                .padding(end = Spacing.edgeMargin)
                .statusBarsPadding(),
            radiusOverlayActive = uiFlags.showRadiusOverlay,
            onRadiusOverlayToggle = onRadiusOverlayToggle,
            onCompass = { cameraState.animateToNorth() },
            onMyLocation = { currentLocation?.let { cameraState.animateToLocation(it) } },
            onZoomIn = { cameraState.zoomIn() },
            onZoomOut = { cameraState.zoomOut() },
        )

        // ── Top bar ───────────────────────────────────────────────────────────
        MapOverlayTopBar(
            displayName = uiFlags.displayName,
            availableCategories = mapData.availableCategories,
            selectedCategories = mapData.selectedCategories,
            onSignOut = onSignOut,
            onCategorySelected = onCategorySelected,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(vertical = Spacing.edgeMargin)
                .statusBarsPadding(),
        )

        // ── Bottom bar ────────────────────────────────────────────────────────
        MapOverlayBottomBar(
            searchRadius = searchRadius,
            isSearchVisible = uiFlags.isSearchVisible,
            searchQuery = uiFlags.searchQuery,
            placeResults = uiFlags.placeResults,
            currentLocation = currentLocation,
            onRadiusChange = onRadiusChange,
            onSearchToggle = onSearchToggle,
            onSearchQueryChange = onSearchQueryChange,
            onPlaceSelected = onPlaceSelected,
            onSpawnDummy = onSpawnDummy,
            onAddMarker = { onAddDialogToggle(true, null) },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = Spacing.edgeMargin)
                .padding(bottom = Spacing.fabOffset)
                .navigationBarsPadding(),
        )
    }
}

