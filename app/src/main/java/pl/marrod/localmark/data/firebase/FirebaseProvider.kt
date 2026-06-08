package pl.marrod.localmark.data.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlin.getValue

import pl.marrod.localmark.di.AppContainer
/**
 * Application-wide singleton that centralises all Firebase service instances and
 * Firestore collection/document name constants for the LocalMark app.
 *
 * Both lazy properties are initialised on first access and reused for the lifetime of the
 * process, guaranteeing that every data-layer class shares the same [FirebaseAuth] and
 * [com.google.firebase.firestore.FirebaseFirestore] instances.
 *
 * **Service instances**
 *
 * All instances are consumed exclusively by [AppContainer], which wires
 * them into the data-layer classes:
 * - [auth] → [FirebaseAuthentication]
 * - [firestore] → [UsersDataSource], [MarkersDataSource]
 * - [firebaseStorage] → [MarkersImageSource]
 *
 * **Collection / document constants**
 *
 * Centralising the Firestore path strings here ensures a single source of truth and avoids
 * magic strings being scattered across the codebase:
 *
 * | Constant | Value | Used by |
 * |---|---|---|
 * | [USERS_COLLECTION] | `"users"` | [UsersDataSource] |
 * | [MARKERS_COLLECTION] | `"markers"` | [MarkersDataSource] |
 * | [MARKERS_IMAGES] | `"markersImages"` | [MarkersImageSource] (Firebase Storage path) |
 *
 * @see AppContainer
 * @see FirebaseAuthentication
 * @see UsersDataSource
 * @see MarkersDataSource
 * @see MarkersImageSource
 */
object FirebaseProvider {

    /**
     * Firestore top-level collection that stores user profile documents, each keyed by the
     * Firebase Auth UID.
     *
     * Referenced by [UsersDataSource] to read and write user
     * profiles.
     */
    const val USERS_COLLECTION = "users"

    const val MARKERS_COLLECTION = "markers"

    const val MARKERS_IMAGES = "markersImages"
    /**
     * Lazily initialised [FirebaseAuth] instance shared across the app.
     *
     * Passed to [FirebaseAuthentication] by
     * [AppContainer] to perform sign-in, sign-up, sign-out,
     * password reset, and auth-state observation operations.
     */
    val auth by lazy { FirebaseAuth.getInstance() }

    /**
     * Lazily initialised [com.google.firebase.firestore.FirebaseFirestore] instance shared
     * across the app, configured with persistent local caching so that reads succeed even
     * when the device is offline.
     *
     * Passed to [UsersDataSource] and [MarkersDataSource] by
     * [AppContainer].
     */
    val firestore by lazy {
        Firebase.firestore.apply {
            // Override the default Firestore settings to enable offline persistence.
            // Without this, Firestore only works when the device has an active network connection.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                // PersistentCacheSettings instructs the Firestore Android SDK to persist a local
                // copy of all documents the app has ever fetched to the device's disk.
                // Note: Firestore is a NoSQL document database on the server side, but the
                // Android SDK internally uses SQLite as the on-device storage format for the cache.
                // This means:
                //   - Reads return cached data instantly while a network call is in flight (latency compensation).
                //   - Reads succeed even when the device is fully offline.
                //   - Writes made offline are queued and automatically synced when connectivity is restored.
                // The alternative, MemoryCacheSettings, keeps data only in RAM and is lost on process death.
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        // newBuilder() uses the default cache size limit (104 MB).
                        // You can call setCacheSizeBytes(bytes) here to raise or lower the limit,
                        // or pass CACHE_SIZE_UNLIMITED to remove it entirely.
                        .build()
                )
                .build() // Finalise and apply the settings to this FirebaseFirestore instance.

        }
    }

    /**
     * Lazily initialised [com.google.firebase.storage.FirebaseStorage] instance shared across the app.
     *
     * Passed to [MarkersImageSource] by
     * [AppContainer] to perform image upload and download operations for marker images.
     */
    val firebaseStorage by lazy { Firebase.storage }
}