package pl.marrod.localmark.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import pl.marrod.localmark.data.model.UserProfile
import pl.marrod.localmark.data.firebase.FirebaseProvider.USERS_COLLECTION
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.data.repository.AuthRepository
/**
 * Firestore data source responsible for all read and write operations against the
 * `users` collection (see [USERS_COLLECTION]).
 *
 * Each document in the collection is keyed by the Firebase Auth UID and stores a
 * [UserProfile] (email + username). This class is the single point of truth for
 * user profile persistence; authentication state itself is managed separately by
 * [FirebaseAuthentication].
 *
 * This class is instantiated in [AppContainer] using
 * [FirebaseProvider.firestore], and is injected as
 * `userDataStore` into [AuthRepository], which is
 * the sole consumer of all methods defined here.
 *
 * @param firestore The [FirebaseFirestore] instance used for all Firestore operations.
 *
 * @see AuthRepository
 * @see AppContainer
 * @see FirebaseProvider
 */
class UsersDataSource(val firestore: FirebaseFirestore) {

    private val usersCollection = firestore.collection(USERS_COLLECTION)

    /**
     * Creates a new user profile document in the `users` collection, keyed by [uid].
     *
     * Called by [AuthRepository.register] immediately
     * after a successful [FirebaseAuthentication.signUp].
     * If this call fails, `AuthRepository` rolls back the Firebase Auth account by calling
     * [FirebaseAuthentication.deleteCurrentUser], ensuring
     * the two sources of truth stay in sync.
     *
     * [FirebaseFirestoreException] is caught separately to provide a more descriptive error
     * message; all other exceptions are propagated as-is.
     *
     * @param uid      The Firebase Auth UID of the newly created user. Used as the Firestore
     *                 document ID so that auth and profile lookups share the same key.
     * @param email    The user's email address, stored in the profile for display purposes.
     * @param username The user's chosen display name.
     * @return [Result.success] with [Unit] if the document was written successfully,
     *         or [Result.failure] with a descriptive exception on error.
     */
    suspend fun createUserProfile(
        uid: String,
        email: String,
        username: String
    ): Result<Unit> {
        return try {
            val profile = UserProfile(
                email = email,
                username = username,
            )
            usersCollection.document(uid)
                .set(profile)
                .await()

            Result.success(Unit)

        } catch (e: FirebaseFirestoreException) {
            Result.failure(Exception("Failed to create user profile: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches a single user profile document by [uid].
     *
     * Called by [AuthRepository.signIn] to load the
     * user's display name after a successful sign-in, and by
     * [AuthRepository.getCurrentUserProfile] to load
     * it on demand (e.g. when the app is relaunched with an existing session).
     *
     * Returns `null` inside a [Result.success] when the Auth account exists but no
     * Firestore profile document has been created yet (edge case: profile creation failed
     * during registration and was never retried).
     *
     * @param uid The Firebase Auth UID whose profile to fetch.
     * @return [Result.success] containing the [UserProfile], or `null` if no document exists.
     *         [Result.failure] with a descriptive exception on Firestore error.
     */
    suspend fun getUserProfile(uid: String): Result<UserProfile?> {
        return try {
            val snapshot = usersCollection.document(uid).get().await()

            if (snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                Result.success(profile)
            } else {
                Result.success(null)  // User exists in Auth but no Firestore profile - this shouldn't happen under normal circumstances but we handle it gracefully just in case
            }

        } catch (e: FirebaseFirestoreException) {
            Result.failure(Exception("Failed to fetch user profile: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks whether a given username is already taken by querying the `users` collection
     * for any document with a matching `username` field.
     *
     * Uses `.limit(1)` to minimise read cost — only one document needs to be found to
     * confirm a collision. Currently **not called** by any repository; intended for use in
     * a registration flow that enforces unique display names (call from
     * [AuthRepository.register] before creating the account).
     *
     * @param username The username string to check for existence.
     * @return [Result.success] with `true` if the username is already taken, `false` if it
     *         is available. [Result.failure] with the thrown exception on Firestore error.
     */
    suspend fun isUsernameTaken(username: String): Result<Boolean> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()

            Result.success(!snapshot.isEmpty)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
