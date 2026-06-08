package pl.marrod.localmark.data.firebase

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import pl.marrod.localmark.data.firebase.FirebaseProvider.MARKERS_COLLECTION
import pl.marrod.localmark.data.model.MarkerData
import pl.marrod.localmark.data.model.MarkerDocument
import pl.marrod.localmark.data.model.toDocument
import pl.marrod.localmark.data.model.toDomain
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.data.repository.MarkersRepository
/**
 * Firestore data source responsible for all read and write operations against the
 * `markers` collection (see [MARKERS_COLLECTION]).
 *
 * Each document in the collection is keyed by [MarkerData.id] and stored as a
 * [MarkerDocument] DTO. This class is the single point of truth for marker
 * persistence; image storage is managed separately by [MarkersImageSource].
 *
 * Both real-time (Flow-based) and one-shot suspending operations are provided:
 *
 * | Method | Type | Description |
 * |---|---|---|
 * | [observeMarkers] | Flow | Streams the full marker collection |
 * | [observeMarker] | Flow | Streams a single marker by ID |
 * | [addMarker] | suspend | Writes a new marker document |
 * | [deleteMarker] | suspend | Removes a marker document by ID |
 * | [updateMarker] | suspend | Partially updates mutable fields of an existing marker |
 * | [confirmMarker] | suspend | Atomically appends a user ID to the confirmations list |
 *
 * This class is instantiated in [AppContainer] using
 * [FirebaseProvider.firestore], and is injected as `markersDataSource` into
 * [MarkersRepository], which is the sole
 * consumer of all methods defined here.
 *
 * @param firestore The [FirebaseFirestore] instance used for all Firestore operations.
 *
 * @see MarkersRepository
 * @see AppContainer
 * @see FirebaseProvider
 * @see MarkersImageSource
 */
class MarkersDataSource(val firestore: FirebaseFirestore) {


    /**
     * Reference to the Firestore collection where marker documents are stored.
     * Each document's ID matches the corresponding [MarkerData.id].
     */
    private val markersCollection = firestore.collection(MARKERS_COLLECTION)

    /**
     * Returns a cold [Flow] that emits the full list of markers and re-emits on every
     * Firestore snapshot update.
     *
     * Called by [MarkersRepository.observeMarkers].
     * Parsing exceptions per-document are swallowed and logged at `ERROR` level with tag
     * `MarkersDataSource`, so a malformed document does not prevent the rest of the list
     * from being emitted.
     *
     * @return A [Flow] emitting the full [MarkerData] list; never completes unless
     *         collection is cancelled.
     */
    fun observeMarkers(): Flow<List<MarkerData>> =
        markersCollection.snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(MarkerDocument::class.java)?.toDomain()
                } catch (e: Exception) {
                    Log.e(
                        "MarkersDataSource",
                        "Error parsing marker document ${document.id}: ${e.message}"
                    )
                    null
                }
            }
        }

    /**
     * Returns a cold [Flow] that emits the marker document with the given [markerId]
     * and re-emits on every Firestore snapshot update. If the document is deleted, emits `null`.
     *
     * Called by [MarkersRepository.observeMarker].
     *
     * @param markerId The Firestore document ID of the marker to observe.
     * @return A [Flow] emitting the [MarkerData], or `null` if the document does not exist.
     */
    fun observeMarker(markerId: String): Flow<MarkerData?> =
        markersCollection.document(markerId).snapshots().map { snapshot ->
            try {
                snapshot.toObject(MarkerDocument::class.java)?.toDomain()
            } catch (e: Exception) {
                Log.e(
                    "MarkersDataSource",
                    "Error parsing marker document ${snapshot.id}: ${e.message}"
                )
                null
            }
        }

    /**
     * Unconditionally adds a new marker document (with [MarkerData.id] as the document path)
     * to Firestore with the provided data.
     *
     * Called by [MarkersRepository.addMarker].
     *
     * @param marker The [MarkerData] to persist; its [MarkerData.id] is used as the document key.
     * @return [Result.success] with [Unit] on success, or [Result.failure] with the thrown
     *         exception on Firestore error.
     */
    suspend fun addMarker(marker: MarkerData): Result<Unit> {
        return try {
            markersCollection.document(marker.id).set(marker.toDocument()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unconditionally deletes the marker document by its ID.
     *
     * Ownership must be verified **before** this call — see
     * [MarkersRepository.deleteMarker].
     *
     * @param markerId The Firestore document ID of the marker to delete.
     * @return [Result.success] with [Unit] on success, or [Result.failure] with the thrown
     *         exception on Firestore error.
     */
    suspend fun deleteMarker(markerId: String): Result<Unit> {
        return try {
            markersCollection.document(markerId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates only the mutable fields of an existing marker document.
     *
     * Uses `update()` with explicit field names instead of `set()` so that
     * fields not included here (e.g. [MarkerDocument.confirmations] written
     * concurrently by other users) are **never overwritten** by an edit.
     *
     * Ownership must be verified **before** this call — see
     * [MarkersRepository.updateMarker].
     *
     * @param marker The [MarkerData] whose mutable fields should be written; its
     *               [MarkerData.id] is used to locate the document.
     * @return [Result.success] with [Unit] on success, or [Result.failure] with the thrown
     *         exception on Firestore error.
     */
    suspend fun updateMarker(marker: MarkerData): Result<Unit> {
        return try {
            val doc = marker.toDocument()
            markersCollection.document(marker.id).update(
                mapOf(
                    MarkerDocument::message.name to doc.message,
                    MarkerDocument::categoryName.name to doc.categoryName,
                    MarkerDocument::imageUri.name to doc.imageUri,
                    MarkerDocument::timestamp.name to System.currentTimeMillis(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically adds [userId] to the marker's confirmations list.
     *
     * **`FieldValue.arrayUnion()`** is a server-side atomic operation — Firestore
     * merges the value directly without any client read, making it safe under
     * any number of concurrent confirmations.  It also acts as a set, so a user
     * who taps "confirm" twice will only appear once in the list.
     *
     * Called by [MarkersRepository.confirmMarker].
     *
     * @param userId The Firebase Auth UID of the user confirming the marker.
     * @param marker The [MarkerData] whose confirmations list should be updated.
     * @return [Result.success] with [Unit] on success, or [Result.failure] with the thrown
     *         exception on Firestore error.
     */
    suspend fun confirmMarker(userId: String, marker: MarkerData): Result<Unit> {
        return try {
            markersCollection.document(marker.id)
                .update(MarkerDocument::confirmations.name, FieldValue.arrayUnion(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}