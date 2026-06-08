package pl.marrod.localmark.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Data class representing a user profile in the LocalMark app, stored in Firestore under the `users` collection.
 * Each user document is keyed by the Firebase Auth UID and contains the following fields:
 * @param uid The Firebase Auth UID, which serves as the document ID.
 * @param username The user's display name.
 * @param email The user's email address.
 */
data class UserProfile(
    @DocumentId
    val uid: String = "" ,
    val username: String = "",
    val email: String = "",
)
