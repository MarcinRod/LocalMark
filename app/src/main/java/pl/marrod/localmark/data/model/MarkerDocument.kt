package pl.marrod.localmark.data.model

import androidx.core.net.toUri
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentId

/**
 * Data class representing a marker document stored in Firestore.
 *
 * This class is used for serializing and deserializing marker data to and from Firestore.
 * It includes all the necessary fields to represent a marker, such as location, author info,
 * message, timestamp, confirmations, and an optional image URI.
 *
 * The `categoryName` field is stored as a string in Firestore, but it corresponds to the
 * [MarkerCategory] name property. When converting to the domain model, the string is mapped back to the appropriate MarkerCategory value.
 *
 * @param id The unique identifier of the marker document (Firestore document ID).
 * @param authorId The UID of the user who created the marker.
 * @param lat The latitude of the marker's location.
 * @param lng The longitude of the marker's location.
 * @param locationName A human-readable name for the marker's location.
 * @param categoryName The category of the marker as a string (e.g., "Event", "Warning").
 * @param authorName The display name of the marker's author.
 * @param message The message or description associated with the marker.
 * @param timestamp The creation time of the marker in milliseconds since epoch.
 * @param confirmations A list of user IDs who have confirmed this marker.
 * @param imageUri An optional URI string pointing to an image associated with the marker.
 */
data class MarkerDocument(
    @DocumentId
    val id: String = "", // Firestore document ID, it is the name of the document in the collection.
    // It is not a field in the document data itself, but is mapped to this property using the @DocumentId annotation.
    val authorId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val locationName: String = "",
    val categoryName: String = MarkerCategory.Event.name, // Default to "Event" category
    val authorName: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val confirmations: List<String> = emptyList(),
    val imageUri: String? = null
)

/**
 * Maps a [MarkerDocument] to a [MarkerData] domain model.
 */
fun MarkerDocument.toDomain(): MarkerData {
    return MarkerData(
        id           = id,
        location     = LatLng(lat, lng),
        locationName = locationName,
        category     = MarkerCategory.entries.find { it.name == categoryName }
            ?: MarkerCategory.Event, // Fallback to "Event" if category is unrecognized
        authorId     = authorId,
        authorName   = authorName,
        message      = message,
        timestamp    = timestamp,
        confirmations = confirmations,
        imageUri     = imageUri?.toUri()
    )
}
