# Firebase Cloud Storage

## What is it?

**Firebase Cloud Storage** stores and serves binary files (images, audio, video, etc.) in a Google Cloud Storage bucket. The Android SDK provides a reference-based API for uploading, downloading, and deleting files. LocalMark uses it exclusively to store marker photos: each marker can have one image, stored at a predictable path derived from the marker's UUID.

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.firebase.storage)
```

The `google-services` plugin and `google-services.json` (already required by Firebase Auth) also enable Cloud Storage.

---

## Core Concepts

### `FirebaseStorage` instance

```kotlin
// FirebaseProvider.kt
val firebaseStorage by lazy { Firebase.storage }
```

`Firebase.storage` returns the default `FirebaseStorage` instance connected to the project's default bucket.

### Storage references

A `StorageReference` is like a file-system path. References can be chained:

```kotlin
val storageRef   = firebaseStorage.reference               // root of the bucket
val imagesFolder = storageRef.child("markersImages")       // sub-folder
val imageFile    = imagesFolder.child("$markerId/image.jpg") // file path
```

The path structure used in LocalMark:

```
markersImages/
  └── {markerId}/
        └── image.jpg
```

One fixed filename (`image.jpg`) per marker keeps the path predictable — no need to store the storage path in Firestore separately because it can be reconstructed from the marker's ID.

### Upload

```kotlin
val imageRef = imagesReference.child("$markerId/$defaultImageName")
imageRef.putFile(imageUri).await()              // upload the file
val downloadUrl = imageRef.downloadUrl.await()  // get the public HTTPS URL
```

`putFile(Uri)` uploads the file pointed to by the content URI. The resulting `downloadUrl` is an HTTPS URL that can be used directly in image-loading libraries (Coil, Glide, etc.) and is stored in the Firestore marker document.

### Delete

```kotlin
val imageRef = imagesReference.child("$markerId/$defaultImageName")
imageRef.delete().await()
```

Deletes the file at the reference path. Throws `StorageException` if the file does not exist.

### Error handling

All operations return `Task<T>`; wrapped with `.await()` in a `try/catch`:

```kotlin
return try {
    imageRef.putFile(imageUri).await()
    val downloadUrl = imageRef.downloadUrl.await()
    Result.success(downloadUrl)
} catch (e: Exception) {
    Result.failure(e)
}
```

---

## How the app uses it

### `MarkersImageSource`

A focused class with exactly two operations:

```kotlin
class MarkersImageSource(val firebaseStorage: FirebaseStorage) {
    private val imagesReference = firebaseStorage.reference.child(MARKERS_IMAGES)
    private val defaultImageName = "image.jpg"

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
```

### Integration in `MarkersRepository`

Storage operations are orchestrated alongside Firestore writes in `MarkersRepository` so that the ViewModel never has to manage the two independently:

#### Adding a marker

```kotlin
suspend fun addMarker(marker: MarkerData, imageUri: Uri?): Result<Unit> {
    val finalImageUri = imageUri?.let {
        val uploadResult = markersImageSource.uploadMarkerImage(marker.id, imageUri)
        if (uploadResult.isFailure)
            return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
        uploadResult.getOrNull()  // the public download URL (Uri)
    }
    // Store the download URL (or null) in the Firestore document
    return markersDataSource.addMarker(marker.copy(imageUri = finalImageUri))
}
```

If the upload fails, the Firestore write is skipped entirely — the marker is not added.

#### Deleting a marker

```kotlin
suspend fun deleteMarker(currentUserId: String, marker: MarkerData): Result<Unit> {
    if (currentUserId != marker.authorId)
        return Result.failure(SecurityException("Only the marker author can delete it"))
    if (marker.imageUri != null) {
        val deleteImageResult = markersImageSource.deleteMarkerImage(marker.id)
        if (deleteImageResult.isFailure)
            return Result.failure(deleteImageResult.exceptionOrNull() ?: Exception("Image deletion failed"))
    }
    return markersDataSource.deleteMarker(marker.id)  // delete Firestore doc after image
}
```

Image deletion is attempted first; the Firestore document is only deleted if storage cleanup succeeds, preventing orphaned documents with broken image URLs.

#### Updating a marker

```kotlin
suspend fun updateMarker(currentUserId: String, marker: MarkerData, imageUri: Uri?): Result<Unit> {
    if (currentUserId != marker.authorId)
        return Result.failure(SecurityException("Only the marker author can update it"))
    val finalImageUri = imageUri?.let {
        if (marker.imageUri != null)
            markersImageSource.deleteMarkerImage(marker.id)  // delete old image first
        val uploadResult = markersImageSource.uploadMarkerImage(marker.id, imageUri)
        if (uploadResult.isFailure)
            return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
        uploadResult.getOrNull()
    }
    return markersDataSource.updateMarker(marker.copy(imageUri = finalImageUri))
}
```

### Displaying images from Storage

The download URL stored in Firestore is a standard HTTPS URL, loaded with **Coil**:

```kotlin
// In any composable
AsyncImage(
    model = marker.imageUri,   // the HTTPS download URL stored in Firestore
    contentDescription = null,
)
```

---

## Minimal Setup Checklist

1. Enable Firebase Cloud Storage for your project in the Firebase Console.
2. Set up Storage Security Rules (e.g., allow authenticated users to write their own paths).
3. Add `firebase.storage` to Gradle dependencies.
4. Obtain the `FirebaseStorage` instance via `Firebase.storage`.
5. Build `StorageReference` paths with `reference.child("folder/file.ext")`.
6. Upload with `ref.putFile(uri).await()` and fetch the download URL with `ref.downloadUrl.await()`.
7. Store the download URL (not the storage path) in Firestore so image-loading libraries can use it directly.
8. Delete with `ref.delete().await()` — coordinate with Firestore deletes in the same repository method.

