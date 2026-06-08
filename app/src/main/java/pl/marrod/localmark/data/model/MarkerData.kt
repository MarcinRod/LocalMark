package pl.marrod.localmark.data.model

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.maps.model.LatLng
import pl.marrod.localmark.R
import pl.marrod.localmark.ui.helpers.UiText
import kotlin.time.Duration.Companion.milliseconds
import pl.marrod.localmark.ui.screens.map.MapDataState
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.ui.screens.map.MapScreen
import pl.marrod.localmark.ui.components.bottomsheet.AddMarkerBottomSheet
import pl.marrod.localmark.ui.components.bottomsheet.ViewMarkBottomSheet
import pl.marrod.localmark.data.DummyMarkerDataSource
import pl.marrod.localmark.data.firebase.MarkersDataSource

/**
 * Unified category that carries every piece of UI metadata needed to render
 * a marker and its bottom-sheet chip in one place.
 *
 * Because this is a sealed class, adding a new category causes a compile error
 * in every exhaustive `when`
 *
 * **Usages**
 *
 * - [MapDataState] holds `availableCategories` and
 *   `selectedCategories` as lists/sets of [MarkerCategory].
 * - [MapViewModel.toggleCategoryFilter] accepts a nullable
 *   [MarkerCategory] — `null` means "All".
 * - [AddMarkerBottomSheet] renders one chip per
 *   entry using [label] and [icon]; defaults to [entries].
 * - [pl.marrod.localmark.data.DummyMarkerDataSource] picks a random category from [entries].
 *
 * @param name       Stable string identifier used for serialization in Firestore and as the `type` field in [MarkerDocument]. Defaults to the class name.
 * @param label       Localized (via [UiText]) Human-readable name shown on the tag chip.
 * @param icon        Material icon used for both the map marker and the chip.
 * @param accentColor Tint applied to the map marker; [androidx.compose.ui.graphics.Color.Unspecified]
 *                    falls back to the theme primary colour.
 */
sealed class MarkerCategory(
    val name: String = "MarkerCategory", // Used for serialization; defaults to the class name
    val label: UiText,
    val icon: ImageVector,
    val accentColor: Color = Color.Unspecified,
) {
    /**
     * Represents a dangerous situation at a location (e.g. road hazard, obstacle).
     * Uses a red accent color to draw immediate attention on the map.
     */
    data object Hazard : MarkerCategory(
        name = "Hazard",
        label = UiText.Resource(R.string.hazard),
        icon = Icons.Default.Warning,
        accentColor = Color(0xFFE91E63),
    )

    /**
     * Represents a traffic-related event (e.g. congestion, road works, accident).
     * Uses a yellow color as accent.
     */
    data object Traffic : MarkerCategory(
        name = "Traffic",
        label = UiText.Resource(R.string.traffic),
        icon = Icons.Default.DirectionsCar,
        accentColor = Color(0xFFFFEB3B),
    )

    /**
     * Represents a local public event (e.g. festivities, concerts, gatherings).
     * Uses a light blue color as accent.
     */
    data object Event : MarkerCategory(
        name = "Event",
        label = UiText.Resource(R.string.event),
        icon = Icons.Default.Celebration,
        accentColor = Color(0xFF00BCD4),
    )

    companion object {
        /**
         * All concrete [MarkerCategory] subclasses in display order
         * (`[Hazard, Traffic, Event]`).
         *
         * Used as the default chip list in [AddMarkerBottomSheet], as the full category pool in
         * [MapScreen] preview data, and as the random-pick source in
         * [DummyMarkerDataSource].
         *
         * Lazily initialised to avoid static-init ordering issues with `data object` subclasses.
         *
         * **NOTE:** Adding a new category requires adding it to this list to be included in the UI.
         */
        val entries: List<MarkerCategory> by lazy { listOf(Hazard, Traffic, Event) }
    }
}

/**
 * Immutable data that fully describes a single marker rendered on the map.
 *
 * Instances are created in:
 * - [AddMarkerBottomSheet] when the user submits
 *   the "Add Mark" form, then forwarded to
 *   [MapViewModel.addMarker].
 * - [DummyMarkerDataSource] for preview / testing purposes.
 *
 * Consumed by:
 * - [MapDataState.displayedMarkers] — the filtered list
 *   of markers passed to the map composable.
 * - [MapViewModel.selectedMarker] — the marker whose
 *   details are shown in the "View Mark" bottom sheet.
 * - [ViewMarkBottomSheet] — displays [message],
 *   [authorName], [timeAgoLabel], [confirmations], and [category].
 *
 * @param id            Unique stable key used as the Compose `key` for each map marker
 *                      composable, preventing unnecessary recompositions on list updates.
 * @param location      Geographic coordinates of the mark, used for map placement and
 *                      radius-filter distance calculations in
 *                      [MapViewModel].
 * @param locationName  Human-readable address resolved by
 *                      [pl.marrod.localmark.data.repository.GeocodingRepository.reverseGeocode]
 *                      and displayed in both bottom sheets.
 * @param category      Visual and semantic type — drives the marker icon and accent colour
 *                      via [MarkerCategory.icon] and [MarkerCategory.accentColor].
 * @param authorName    Display name of the user who posted the mark, shown in the
 *                      "View Mark" bottom sheet.
 * @param authorId      Firebase Auth UID of the creator — used for ownership checks in edit/delete operations.
 * @param message       Full description of the mark shown in the "View Mark" bottom sheet.
 * @param timestamp     Unix epoch time in milliseconds when the marker was created.
 *                      Used by the [timeAgoLabel] computed property.
 * @param confirmations Number of other users who have confirmed this mark, shown as a
 *                      counter in the "View Mark" bottom sheet.
 * @param imageUri     Optional URL for an image associated with the marker, shown in the "View" bottom sheet if present.
 */
data class MarkerData(
    val message: String,
    val location: LatLng,
    val locationName: String,
    val category: MarkerCategory,
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val confirmations: List<String> = emptyList(),
    val imageUri: Uri? = null, // Optional URL for an image associated with the marker
) {
    /**
     * Computed, locale-independent relative-time label derived from [timestamp].
     *
     * Returns a [UiText.Resource] so the actual
     * string is resolved against the current locale at the call site.
     *
     * | Condition | String resource |
     * |---|---|
     * | `hours > 0` | `R.string.hour_m_ago` (e.g. *"1 h 5 min ago"*) |
     * | `minutes > 1` | `R.string.minutes_ago` (e.g. *"3 minutes ago"*) |
     * | `minutes == 1` | `R.string.minute_ago` (*"1 minute ago"*) |
     * | otherwise | `R.string.just_now` (*"Just now"*) |
     *
     * Consumed by [ViewMarkBottomSheet]
     * via `marker.timeAgoLabel.asString()`.
     */
    val timeAgoLabel: UiText
        get() {
            val timeDiff = System.currentTimeMillis() - timestamp
            timeDiff.milliseconds.toComponents { _, hours, minutes, _, _ ->
                return when {
                    hours > 0 -> UiText.Resource(R.string.hour_m_ago, listOf(hours, minutes))
                    minutes > 1 -> UiText.Resource(R.string.minutes_ago, listOf(minutes))
                    minutes == 1 -> UiText.Resource(R.string.minute_ago)
                    else -> UiText.Resource(R.string.just_now)
                }
            }
        }
}

/**
 * Maps this [MarkerData] to a Firestore document [MarkerDocument] for remote storage.
 * Used by [MarkersDataSource] when adding a new marker to the Firestore.
 */
fun MarkerData.toDocument(): MarkerDocument {
    return MarkerDocument(
        id            = id,
        authorId        = authorId,
        lat           = location.latitude,
        lng           = location.longitude,
        locationName  = locationName,
        categoryName  = category.name,
        authorName    = authorName,
        message       = message,
        timestamp     = timestamp,
        confirmations = confirmations,
        imageUri      = imageUri?.toString(),
    )
}
