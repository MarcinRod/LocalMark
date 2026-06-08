package pl.marrod.localmark.data.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.data.repository.AuthRepository
/**
 * A wrapper around [FirebaseAuth] that provides coroutine-friendly authentication operations
 * for the LocalMark application.
 *
 * Supported operations:
 * - **Email/password sign-in** ([signIn]) and **registration** ([signUp])
 * - **Password reset** ([resetPassword])
 * - **Google sign-in** ([signInWithGoogle]) via the Credential Manager API
 * - **GitHub sign-in** ([signInWithGitHub]) via Firebase OAuthProvider + Chrome Custom Tab
 * - **Pending OAuth result recovery** ([checkPendingSignIn]) for interrupted Custom Tab flows
 * - **Sign-out** ([signOut]) and **account deletion** ([deleteCurrentUser])
 *
 * This class is instantiated in [AppContainer] using
 * [FirebaseProvider]'s `auth` instance, and is injected as a dependency into
 * [AuthRepository].
 *
 * @param firebaseAuth      The [FirebaseAuth] instance used for all authentication operations.
 * @param googleWebClientId The OAuth 2.0 Web Client ID from `google-services.json`, required
 *                          by [GetSignInWithGoogleOption] to identify the app to Google's servers.
 *
 * @see AuthRepository
 * @see AppContainer
 */
class FirebaseAuthentication(
    private val firebaseAuth: FirebaseAuth,
    private val googleWebClientId: String,
) {

    /**
     * Signs in an existing user with the given email and password.
     *
     * Called by [AuthRepository.signIn] to authenticate a user
     * and subsequently fetch their profile from Firestore.
     *
     * @param email The user's email address.
     * @param password The user's password.
     * @return [Result.success] containing the authenticated [FirebaseUser] on success,
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun signIn(
        email: String,
        password: String
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            // Suspend until Firebase completes the sign-in network request
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            // Unwrap the user from AuthResult (variable result); a non-null user means authentication succeeded
            result?.user?.let { user ->
                Result.success(user) // Wrap the authenticated FirebaseUser in a success Result
            } ?: Result.failure(Exception("Authentication failed")) // AuthResult or user was null – treat as failure
        } catch (e: Exception) {
            Result.failure(e)
        }

    }

    /**
     * Creates a new Firebase user with the given email and password.
     *
     * Called by [AuthRepository.register]. On success the
     * repository creates a Firestore user profile; if that step fails it calls
     * [deleteCurrentUser] to roll back the newly created Firebase account.
     *
     * @param email The email address for the new account.
     * @param password The password for the new account.
     * @return [Result.success] containing the newly created [FirebaseUser] on success,
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun signUp(
        email: String,
        password: String,
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            // Suspend until Firebase completes the sign-up network request
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            // Unwrap the user from the AuthResult (variable result); a non-null user means authentication succeeded
            result?.user?.let { user ->
                Result.success(user) // Wrap the authenticated FirebaseUser in a success Result
            } ?: Result.failure(Exception("Registration failed")) // AuthResult or user was null – treat as failure
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a password-reset email to the specified address.
     *
     * Delegated to by [AuthRepository.resetPassword].
     *
     * @param email The email address of the account whose password should be reset.
     * @return [Result.success] with [Unit] if the email was sent successfully,
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the currently authenticated user.
     *
     * Delegated to by [AuthRepository.signOut].
     * After this call [isUserLoggedIn] returns `false` and [userIdFlow] emits `null`.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Signs in the user with a Google account using the Credential Manager API.
     *
     * Presents the "Sign in with Google" bottom sheet ([GetSignInWithGoogleOption]), extracts
     * the ID token from the returned [GoogleIdTokenCredential], exchanges it for a Firebase
     * credential via [GoogleAuthProvider.getCredential], and completes sign-in with
     * [FirebaseAuth.signInWithCredential].
     *
     * Unlike the older One Tap flow, this approach works reliably on emulators provided a
     * Google account is added in the device Settings.
     *
     * Called by [AuthRepository.signInWithGoogle].
     *
     * @param activityContext An [Activity] context required by [CredentialManager].
     * @return [Result.success] containing the authenticated [FirebaseUser] on success,
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(activityContext)

            val googleIdOption = GetSignInWithGoogleOption.Builder(googleWebClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialResponse = credentialManager.getCredential(activityContext, request)
            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(credentialResponse.credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

            authResult?.user?.let { Result.success(it) }
                ?: Result.failure(Exception("Google sign-in failed: null user"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in the user with a GitHub account using Firebase's OAuthProvider and a
     * Chrome Custom Tab. No GitHub Client ID is needed in the app — Firebase handles
     * the OAuth exchange server-side.
     *
     * @param activity The current [Activity], required by [FirebaseAuth.startActivityForSignInWithProvider].
     * @return [Result.success] containing the authenticated [FirebaseUser] on success,
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun signInWithGitHub(activity: Activity): Result<FirebaseUser> {
        return try {
            val provider = OAuthProvider.newBuilder("github.com").build()
            val authResult = firebaseAuth
                .startActivityForSignInWithProvider(activity, provider)
                .await()
            authResult?.user?.let { Result.success(it) }
                ?: Result.failure(Exception("GitHub sign-in failed: null user"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks whether a sign-in with provider (e.g. GitHub) was started but not completed
     * because the Activity was killed by the OS while the Chrome Custom Tab was open.
     *
     * Should be called once when the ViewModel initialises (before showing the sign-in form)
     * so that returning users are signed in automatically without re-tapping the button.
     *
     * @return [Result.success] with the signed-in [FirebaseUser] if a pending result existed
     *         and completed successfully, [Result.failure] if the pending task threw an error,
     *         or `null` if there was no pending result.
     */
    suspend fun checkPendingSignIn(): Result<FirebaseUser>? {
        val pending = firebaseAuth.pendingAuthResult ?: return null
        return try {
            val authResult = pending.await()
            authResult?.user?.let { Result.success(it) }
                ?: Result.failure(Exception("Pending sign-in completed but user is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes the currently authenticated Firebase user account.     *
     * Used by [AuthRepository.register] as a rollback mechanism
     * when Firestore profile creation fails after a successful [signUp].
     *
     * @return [Result.success] with [Unit] if the account was deleted (or no user was signed in),
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun deleteCurrentUser(): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns whether a user is currently signed in.
     *
     * Used by [AuthRepository.isUserLoggedIn] and
     * [AuthRepository.getCurrentUserProfile] to guard
     * operations that require an authenticated session.
     *
     * @return `true` if a user is signed in, `false` otherwise.
     */
    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Returns the email address of the currently signed-in user, or `null` if no user is signed in.
     *
     * Available for use in UI layers or repositories that need to display or validate the
     * current user's email without performing a full Firestore profile fetch.
     *
     * @return The current user's email string, or `null` if no session is active.
     */
    fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    /**
     * Returns the UID of the currently signed-in user, or `null` if no user is signed in.
     *
     * Used by [AuthRepository.getCurrentUserProfile] to
     * identify which Firestore document to fetch after confirming a session is active via
     * [isUserLoggedIn].
     *
     * @return The current user's UID string, or `null` if no session is active.
     */
    fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    /**
     * Returns a cold [Flow] that emits the current user's UID whenever the Firebase
     * authentication state changes, and emits `null` when no user is signed in.
     *
     * Intended for use by repositories that need to reactively switch their data streams
     * (e.g. Firestore listeners) whenever the authenticated user changes. Collect with
     * `flatMapLatest` to automatically cancel and re-subscribe to user-scoped data sources.
     *
     * The underlying [FirebaseAuth.AuthStateListener] is registered when the flow is collected
     * and removed when collection is cancelled via [awaitClose], preventing memory leaks.
     *
     * @return A [Flow] of nullable UID strings driven by Firebase auth-state events.
     */
    fun userIdFlow(): Flow<String?> = callbackFlow {
        // callbackFlow is a Flow builder designed specifically for wrapping callback-based APIs into a Kotlin Flow.
        // The auth state listener is a callback-based API that notifies us whenever the authentication state changes (e.g., user signs in or out).

        // Define a listener that runs every time the auth state changes (sign-in, sign-out, token refresh)
        val listener = FirebaseAuth.AuthStateListener { auth ->
            // Emit the current UID, or null if no user is signed in
            trySend(auth.currentUser?.uid)
        }
        // Register the listener – it fires immediately with the current auth state, then on every change
        firebaseAuth.addAuthStateListener(listener)
        // When the Flow collection is cancelled, unregister the listener to prevent memory leaks
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }
}