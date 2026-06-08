# Firebase Firestore

## What is it?

**Cloud Firestore** is a serverless, NoSQL document database hosted by Google. Data is organised into **collections** of **documents**; each document is a set of key-value pairs and can contain nested sub-collections. The Android SDK supports:

- **Real-time listeners** — a `Flow`/callback that re-emits on every remote change.
- **Offline persistence** — a local SQLite cache that makes reads succeed even with no network; writes are queued and synced automatically when connectivity returns.

LocalMark stores two top-level collections:

| Collection | Key | Contents |
|---|---|---|
| `users` | Firebase Auth UID | `UserProfile` (email, username) |
| `markers` | Random UUID | `MarkerDocument` (location, message, category, author, confirmations, …) |

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.firebase.firestore)
```

The `google-services` plugin and `google-services.json` (already required by Firebase Auth) also enable Firestore.

---

## Core Concepts

### `FirebaseFirestore` instance with offline persistence

```kotlin
val firestore by lazy {
    Firebase.firestore.apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    // Default cache size: 104 MB.
                    // Use .setCacheSizeBytes(bytes) to adjust, or CACHE_SIZE_UNLIMITED.
                    .build()
            )
            .build()
    }
}
```

`PersistentCacheSettings` (disk cache) vs. `MemoryCacheSettings` (RAM only):

| Setting | Persists across process death | Default size |
|---|---|---|
| `PersistentCacheSettings` | Yes (SQLite on device) | 104 MB |
| `MemoryCacheSettings` | No | Unlimited |

### Collection and document references

```kotlin
val markersCollection = firestore.collection("markers")  // CollectionReference
val docRef = markersCollection.document(marker.id)       // DocumentReference
```

### CRUD operations

All `Task`-based Firestore calls are bridged to coroutines via `.await()`:

```kotlin
// Write (create / overwrite)
markersCollection.document(marker.id).set(marker.toDocument()).await()

// Read (one-shot)
val snapshot = usersCollection.document(uid).get().await()
val profile = snapshot.toObject(UserProfile::class.java)  // null if document missing

// Update (partial — only named fields, other fields untouched)
markersCollection.document(marker.id).update(
    mapOf("message" to newMessage, "timestamp" to System.currentTimeMillis())
).await()

// Delete
markersCollection.document(marker.id).delete().await()
```

### Real-time snapshots with `.snapshots()`

The Firestore KTX extension converts a `CollectionReference` or `DocumentReference` into a cold Kotlin `Flow`:

```kotlin
// Collection-level — re-emits the full list on every document change
fun observeMarkers(): Flow<List<MarkerData>> =
    markersCollection.snapshots().map { snapshot ->
        snapshot.documents.mapNotNull { doc ->
            doc.toObject(MarkerDocument::class.java)?.toDomain()
        }
    }

// Document-level — re-emits one marker, or null if it was deleted
fun observeMarker(markerId: String): Flow<MarkerData?> =
    markersCollection.document(markerId).snapshots().map { snapshot ->
        snapshot.toObject(MarkerDocument::class.java)?.toDomain()
    }
```

The SDK registers a listener when the flow is collected and removes it when collection is cancelled — no manual cleanup needed.

### `FieldValue.arrayUnion` — atomic server-side write

When multiple users can concurrently modify the same field, client-read-then-write patterns risk data races. Firestore provides atomic operations via `FieldValue`:

```kotlin
// Atomically adds userId to the confirmations array.
// arrayUnion also acts as a set — adding the same value twice has no effect.
markersCollection.document(marker.id)
    .update(MarkerDocument::confirmations.name, FieldValue.arrayUnion(userId))
    .await()
```

Other useful `FieldValue` helpers: `arrayRemove`, `increment(n)`, `serverTimestamp()`, `delete()`.

### Querying

```kotlin
usersCollection
    .whereEqualTo("username", username)
    .limit(1)       // minimise read cost — one result is enough to confirm uniqueness
    .get()
    .await()
```

---

## How the app uses it

### `FirebaseProvider` — single source of truth for path names

All Firestore path strings are constants to avoid magic strings scattered across the codebase:

```kotlin
object FirebaseProvider {
    const val USERS_COLLECTION   = "users"
    const val MARKERS_COLLECTION = "markers"
    const val MARKERS_IMAGES     = "markersImages"  // Storage path, not Firestore
    val firestore by lazy { /* configured instance */ }
}
```

### `UsersDataSource` — user profiles

| Method | Firestore operation | Called by |
|---|---|---|
| `createUserProfile(uid, email, username)` | `set(UserProfile)` | `AuthRepository.register` / OAuth sign-in |
| `getUserProfile(uid)` | `get()` → `toObject(UserProfile)` | `AuthRepository.signIn`, app launch |
| `isUsernameTaken(username)` | `whereEqualTo + limit(1) + get()` | (reserved for registration validation) |

### `MarkersDataSource` — map markers

| Method | Firestore operation |
|---|---|
| `observeMarkers()` | `collection.snapshots()` → map to domain list |
| `observeMarker(id)` | `document.snapshots()` → map to domain object |
| `addMarker(marker)` | `document.set(marker.toDocument())` |
| `deleteMarker(id)` | `document.delete()` |
| `updateMarker(marker)` | `document.update(mapOf(…))` — partial update only |
| `confirmMarker(userId, marker)` | `document.update(…, FieldValue.arrayUnion(userId))` |

### `MarkersRepository` — business rules on top of the data source

The repository adds ownership checks before delegating to `MarkersDataSource`:

```kotlin
suspend fun deleteMarker(currentUserId: String, marker: MarkerData): Result<Unit> {
    if (currentUserId != marker.authorId)
        return Result.failure(SecurityException("Only the marker author can delete it"))
    return markersDataSource.deleteMarker(marker.id)
}
```

### `selectedMarker` — always-fresh reactive view with `flatMapLatest`

`MapViewModel` uses `flatMapLatest` to switch the Firestore listener whenever the selected marker ID changes:

```kotlin
val selectedMarker: StateFlow<MarkerData?> = _selectedMarkerId
    .flatMapLatest { id ->
        if (id == null) flowOf(null)
        else markersRepository.observeMarker(id)  // opens a new listener for the new ID
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

Any remote change (e.g. a confirmation from another user) is reflected in the UI automatically.

---

## Minimal Setup Checklist

1. Enable Cloud Firestore for your Firebase project in the Firebase Console.
2. Add `firebase.firestore` to Gradle dependencies.
3. Obtain the `FirebaseFirestore` instance via `Firebase.firestore`.
4. Configure `PersistentCacheSettings` for offline support.
5. Define collection-name constants in one central object.
6. Use `.await()` for one-shot reads/writes and `.snapshots()` for real-time flows.
7. Use `FieldValue.arrayUnion` / `arrayRemove` / `increment` for concurrent-safe updates.
8. Add Firestore Security Rules in the Firebase Console to enforce server-side ownership checks.

