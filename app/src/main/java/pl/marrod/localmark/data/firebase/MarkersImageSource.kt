package pl.marrod.localmark.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import pl.marrod.localmark.data.firebase.FirebaseProvider.MARKERS_IMAGES
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.data.repository.MarkersRepository
import pl.marrod.localmark.data.model.MarkerData
/**
 * Firebase Storage data source responsible for uploading and deleting marker images.
 *
 * Images are stored under the path `[MARKERS_IMAGES]/{markerId}/[defaultImageName]`.
 * After a successful upload, the returned public download URL is stored in the
 * corresponding Firestore document as `MarkerData.imageUri` by
 * [MarkersRepository].
 *
 * This class is instantiated in [AppContainer] using
 * [FirebaseProvider.firebaseStorage], and is injected as `markersImageSource` into
 * [MarkersRepository], which is the sole
 * consumer of all methods defined here.
 *
 * @param firebaseStorage The [FirebaseStorage] instance used for all Storage operations.
 *
 * @see MarkersRepository
 * @see AppContainer
 * @see FirebaseProvider
 * @see MarkersDataSource
 */
class MarkersImageSource(val firebaseStorage: FirebaseStorage) {

    /**
     * Root Storage reference pointing to the [MARKERS_IMAGES] folder.
     * All per-marker image paths are resolved relative to this reference as
     * `{markerId}/[defaultImageName]`.
     */
    private val imagesReference = firebaseStorage.reference.child(MARKERS_IMAGES)

    /**
     * Fixed file name used for every marker image.
     * Combined with [MarkerData.id] it forms the full storage path
     * `[MARKERS_IMAGES]/{markerId}/image.jpg`.
     */
    private val defaultImageName = "image.jpg"

    /**
     * Uploads the image at [imageUri] to Firebase Storage and returns its public download URL.
     *
     * The file is written to `[MARKERS_IMAGES]/[markerId]/[defaultImageName]`, overwriting
     * any previously stored image for that marker. The operation is two-step:
     * `putFile` uploads the bytes, then `downloadUrl` fetches the permanent public URL.
     *
     * Called by [MarkersRepository.addMarker] and
     * [MarkersRepository.updateMarker].
     *
     * @param markerId The unique ID of the marker, used as the storage folder name.
     * @param imageUri The local content [Uri] of the image file to upload.
     * @return [Result.success] with the public download [Uri] on success, or [Result.failure]
     *         with the thrown exception on Storage error.
     */
    suspend fun uploadMarkerImage(markerId: String, imageUri: Uri): Result<Uri> {
        return try {
            val imageRef = imagesReference.child("$markerId/$defaultImageName")
           imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes the image file associated with [markerId] from Firebase Storage.
     *
     * Targets the path `[MARKERS_IMAGES]/[markerId]/[defaultImageName]`.
     *
     * Called by [MarkersRepository.deleteMarker]
     * when a marker with an image is removed, and by
     * [MarkersRepository.updateMarker] before
     * uploading a replacement image.
     *
     * @param markerId The unique ID of the marker whose image should be deleted.
     * @return [Result.success] with [Unit] on success, or [Result.failure] with the thrown
     *         exception on Storage error.
     */
    suspend fun deleteMarkerImage(markerId: String): Result<Unit> {
        return try {
            val imageRef = imagesReference.child("$markerId/$defaultImageName")
            imageRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



}