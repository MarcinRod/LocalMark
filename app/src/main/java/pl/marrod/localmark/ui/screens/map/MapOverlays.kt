package pl.marrod.localmark.ui.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import pl.marrod.localmark.R
import pl.marrod.localmark.data.model.MarkerCategory
import pl.marrod.localmark.data.model.MarkerData
import pl.marrod.localmark.data.model.PlaceResult
import pl.marrod.localmark.ui.components.AppToolbar
import pl.marrod.localmark.ui.components.GlassCard
import pl.marrod.localmark.ui.components.MarkTag
import pl.marrod.localmark.ui.components.PlaceSearchResultsList
import pl.marrod.localmark.ui.components.PlacesSearchBar
import pl.marrod.localmark.ui.components.SearchRadiusCard
import pl.marrod.localmark.ui.components.bottomsheet.AddMarkerBottomSheet
import pl.marrod.localmark.ui.components.bottomsheet.EditMarkerBottomSheet
import pl.marrod.localmark.ui.components.bottomsheet.ViewMarkBottomSheet
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.ui.helpers.asString
import pl.marrod.localmark.ui.theme.ButtonShapes
import pl.marrod.localmark.ui.theme.CardShapes
import pl.marrod.localmark.ui.theme.Spacing




@Composable
fun AddEditBottomSheet(
    uiFlags: MapUiState,
    processingResult: Result<UiText>? = null,
    currentLocation: LatLng?,
    currentLocationName: String?,
    selectedMarker: MarkerData?,
    cameraLauncher: CameraLauncherState,
    onDismiss: () -> Unit,
    onAddMarker: (MarkerData) -> Unit,
    onEdit: (MarkerData) -> Unit,
) {
    if (selectedMarker == null) {
        val markerPosition = uiFlags.pendingMarkerLocation ?: currentLocation
        if (currentLocationName != null && markerPosition != null) {
            AddMarkerBottomSheet(
                author = uiFlags.displayName,
                location = markerPosition,
                locationString = currentLocationName,
                photoUri = cameraLauncher.photoUri,
                isProcessing = uiFlags.isProcessing,
                processingResult = processingResult,
                onTakePhoto = cameraLauncher.onTakePhoto,
                showPermissionRationale = cameraLauncher.showPermissionRationale,
                onDismissPermissionRationale = { cameraLauncher.dismissPermissionRationale() },
                onDismissRequest = onDismiss,
                onPost = onAddMarker,
            )
        }
    } else {
        EditMarkerBottomSheet(
            markerData = selectedMarker,
            onDismissRequest = onDismiss,
            onPost = onEdit,
            photoUri = cameraLauncher.photoUri ?: selectedMarker.imageUri,  // pre-populate only if no new photo taken
            isProcessing = uiFlags.isProcessing,
            processingResult = processingResult,
            onTakePhoto = cameraLauncher.onTakePhoto,
        )

    }
    // Clear the captured photo only after the sheet fully leaves composition,
    // so the preview remains visible throughout the dismiss animation.
    DisposableEffect(Unit) {
        onDispose { cameraLauncher.clear() }
    }
}


@Composable
fun MapOverlayTopBar(
    displayName: String,
    availableCategories: List<MarkerCategory>,
    selectedCategories: Set<MarkerCategory>,
    onSignOut: () -> Unit,
    onCategorySelected: (MarkerCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.stackGap),
    ) {
        AppToolbar(
            title = stringResource(R.string.app_name) + ": $displayName",
            onActionClick = onSignOut,
            modifier = Modifier.padding(horizontal = Spacing.edgeMargin),
        )
        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier.padding(horizontal = Spacing.edgeMargin),
            horizontalArrangement = Arrangement.spacedBy(Spacing.stackGap),
        ) {
            item {
                MarkTag(
                    label = stringResource(R.string.all),
                    icon = null,
                    selected = selectedCategories.isEmpty(),
                    onClick = { onCategorySelected(null) },
                )
            }
            items(availableCategories, key = { it.name }) { category ->
                MarkTag(
                    label = category.label.asString(),
                    icon = category.icon,
                    selected = selectedCategories.contains(category),
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapOverlayBottomBar(
    searchRadius: Float,
    isSearchVisible: Boolean,
    searchQuery: String,
    placeResults: List<PlaceResult>,
    currentLocation: LatLng?,
    onRadiusChange: (Float) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlaceSelected: (String) -> Unit,
    onSpawnDummy: (LatLng) -> Unit,
    onAddMarker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible && isSearchVisible) {
            onSearchToggle()
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.stackGap),
    ) {
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            PlaceSearchResultsList(
                results = placeResults,
                onResultClick = { place ->
                    onPlaceSelected(place.placeId)
                    onSearchToggle()
                },
            )
        }

        PlacesSearchBar(
            isExpanded = isSearchVisible,
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onToggle = onSearchToggle,
            modifier = Modifier.align(Alignment.End).imePadding(),
        )
        AnimatedVisibility(
            visible = !isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.stackGap),
                verticalAlignment = Alignment.Bottom,
            ) {
                SearchRadiusCard(
                    radiusKm = searchRadius,
                    onRadiusChange = onRadiusChange,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { currentLocation?.let { onSpawnDummy(it) } },
                        ),
                )
                AddAlertFab(
                    onClick = onAddMarker,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AddAlertFab
// ─────────────────────────────────────────────────────────────────────────────

/** Square FAB for opening the Add Marker bottom sheet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AddAlertFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shapes = ButtonDefaults.shapes(
            shape = ButtonShapes.normal,
            pressedShape = ButtonShapes.large,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 10.dp,
            focusedElevation = 10.dp,
        ),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add alert",
            modifier = Modifier.size(28.dp),
        )
    }
}


@Composable
fun MapControlsPanel(
    onCompass: () -> Unit,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRadiusOverlayToggle: () -> Unit,
    radiusOverlayActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, cardShape = CardShapes.small) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onCompass) {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = "Rotate to North",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            IconButton(onClick = onMyLocation) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Go to my location",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            IconButton(onClick = onRadiusOverlayToggle) {
                Icon(
                    Icons.Default.Adjust,
                    contentDescription = "Toggle radius overlay",
                    tint = if (radiusOverlayActive) MaterialTheme.colorScheme.primaryFixedDim else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            IconButton(onClick = onZoomIn) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Zoom in",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
            IconButton(onClick = onZoomOut) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Zoom out",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

