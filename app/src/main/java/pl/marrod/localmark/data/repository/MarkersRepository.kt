package pl.marrod.localmark.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import pl.marrod.localmark.data.firebase.MarkersDataSource
import pl.marrod.localmark.data.firebase.MarkersImageSource
import pl.marrod.localmark.data.model.MarkerData

/**
 * Repository for all map-marker operations in LocalMark.
 *
 * Business rules (ownership, duplicate confirmation) live here; the data source
 * only performs raw Firestore operations.
 *
 * The caller (ViewModel) is responsible for supplying the current user's UID —
 * the repository does **not** reach into the auth layer itself, which keeps it
 * easy to unit-test without an auth mock.
 *
 * @param markersDataSource Firestore data source for marker documents.
 */
class MarkersRepository(
    private val markersDataSource: MarkersDataSource,
    private val markersImageSource: MarkersImageSource
) {

    /**
     * Returns a cold [Flow] that emits the full marker list and re-emits on every
     * Firestore snapshot update. Delegates directly to [MarkersDataSource.observeMarkers].
     */
    fun observeMarkers(): Flow<List<MarkerData>> = markersDataSource.observeMarkers()


    /**
     * Returns a cold [Flow] that emits the marker with ID [markerId] and re-emits on
     * every Firestore snapshot update. Delegates directly to [MarkersDataSource.observeMarker].
     */
    fun observeMarker(markerId: String): Flow<MarkerData?> =
        markersDataSource.observeMarker(markerId)

    /**
     * Persists a new [marker] document to Firestore.
     *
     * The caller is responsible for setting [MarkerData.authorId] to the current
     * user's UID before calling this method.
     *
     * @return [Result.success] on success, [Result.failure] on Firestore error.
     */
    suspend fun addMarker(marker: MarkerData, imageUri: Uri?): Result<Unit> {
        val finalImageUri = imageUri?.let {
            // if the image URI is not null, attempt to upload it to Firebase Storage and get the download URL
            val uploadImageResult = markersImageSource.uploadMarkerImage(marker.id, imageUri)

            if (uploadImageResult.isFailure) {
                // If the upload failed, return the error without adding the marker document to Firestore
                return Result.failure(
                    uploadImageResult.exceptionOrNull()
                        ?: Exception("Unknown error during image upload")
                )
            }
            // If the upload succeeded, use the download URL in the marker document
            uploadImageResult.getOrNull()
        }
        // Even if the image URI provided in the call is null, we still want to add the marker document to Firestore with a null imageUri field
        return markersDataSource.addMarker(marker.copy(imageUri = finalImageUri))

    }


    /**
     * Deletes [marker] if and only if [currentUserId] matches the marker's author.
     *
     * The ownership check is a simple equality comparison against [MarkerData.authorId],
     * which was written to Firestore when the marker was created — no extra read needed.
     *
     * Note: back this up with a Firestore Security Rule for server-side enforcement:
     * `allow delete: if request.auth.uid == resource.data.userId;`
     *
     * @param currentUserId The Firebase Auth UID of the requesting user, supplied by the ViewModel.
     */
    suspend fun deleteMarker(currentUserId: String, marker: MarkerData): Result<Unit> {
        if (currentUserId != marker.authorId) {
            return Result.failure(SecurityException("Only the marker author can delete it"))
        }
        if (marker.imageUri != null) {
            val deleteImageResult = markersImageSource.deleteMarkerImage(marker.id)
            if (deleteImageResult.isFailure) {
                return Result.failure(
                    deleteImageResult.exceptionOrNull()
                        ?: Exception("Unknown error during image deletion")
                )
            }
        }
        return markersDataSource.deleteMarker(marker.id)
    }

      /**
     * Updates the mutable fields of [marker] if and only if [currentUserId] matches
     * the marker's author.
     *
     * Uses `update()` under the hood (see [MarkersDataSource.updateMarker]) so that
     * concurrent [confirmMarker] writes on the same document are never overwritten.
     *
     * Note: back this up with a Firestore Security Rule for server-side enforcement:
     * `allow update: if request.auth.uid == resource.data.userId;`
     *
     * @param currentUserId The Firebase Auth UID of the requesting user, supplied by the ViewModel.
     */
    suspend fun updateMarker(
        currentUserId: String,
        marker: MarkerData,
        imageUri: Uri?
    ): Result<Unit> {
        if (currentUserId != marker.authorId) {
            return Result.failure(SecurityException("Only the marker author can update it"))
        }
        val finalImageUri = imageUri?.let {
            // delete the old image if it exists before uploading the new one
            if (marker.imageUri != null)
                 markersImageSource.deleteMarkerImage(marker.id)
            // if the image URI is not null, attempt to upload it to Firebase Storage and get the download URL
            val uploadImageResult = markersImageSource.uploadMarkerImage(marker.id, imageUri)

            if (uploadImageResult.isFailure) {
                // If the upload failed, return the error without adding the marker document to Firestore
                return Result.failure(
                    uploadImageResult.exceptionOrNull()
                        ?: Exception("Unknown error during image upload")
                )
            }
            // If the upload succeeded, use the download URL in the marker document
            uploadImageResult.getOrNull()
        }


        return markersDataSource.updateMarker(marker.copy(imageUri = finalImageUri))
    }


    /**
     * Records that [currentUserId] confirms [marker] as accurate.
     *
     * Guards:
     * - [currentUserId] is blank → [IllegalStateException] (caller passed an empty string).
     * - Author confirming own marker → [IllegalArgumentException].
     * - Already confirmed → [IllegalStateException] (fast-fail before any network call).
     *
     * The actual Firestore write uses `FieldValue.arrayUnion`, which is a server-side
     * atomic operation safe under any number of concurrent confirmations.
     *
     * @param currentUserId The Firebase Auth UID of the requesting user, supplied by the ViewModel.
     */
    suspend fun confirmMarker(currentUserId: String, marker: MarkerData): Result<Unit> {
        if (currentUserId.isBlank()) {
            return Result.failure(IllegalStateException("No authenticated user"))
        }
        if (currentUserId == marker.authorId) {
            return Result.failure(IllegalArgumentException("Authors cannot confirm their own marker"))
        }
        if (currentUserId in marker.confirmations) {
            return Result.failure(IllegalStateException("Marker already confirmed by this user"))
        }
        return markersDataSource.confirmMarker(userId = currentUserId, marker = marker)
    }
}

