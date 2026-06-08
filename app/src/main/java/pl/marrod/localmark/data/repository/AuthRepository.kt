package pl.marrod.localmark.data.repository

import android.app.Activity
import android.content.Context
import pl.marrod.localmark.data.firebase.FirebaseAuthentication
import pl.marrod.localmark.data.firebase.UsersDataSource
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.ui.screens.signin.SignInViewModel
import pl.marrod.localmark.ui.screens.map.MapViewModel
/**
 * Repository for all authentication and user-profile operations in LocalMark.
 *
 * Acts as the single source of truth for authentication state, coordinating between two
 * data sources:
 * - [FirebaseAuthentication] — wraps [com.google.firebase.auth.FirebaseAuth] for all
 *   sign-in, sign-up, sign-out, and OAuth operations.
 * - [UsersDataSource] — wraps Firestore for reading and writing user profile documents.
 *
 * All public methods return [Result] so callers never need to catch exceptions directly.
 * OAuth flows (Google, GitHub) share a common profile-creation path via the private
 * [handleOAuthUser] helper, which handles both returning and first-time users and performs
 * an automatic Auth rollback if Firestore profile creation fails.
 *
 * Instantiated as a lazy singleton in [AppContainer] and injected into:
 * - [SignInViewModel] — for all sign-in, registration,
 *   password reset, and OAuth flows.
 * - [MapViewModel] — for signing the user out.
 *
 * @param authService   The [FirebaseAuthentication] instance used for auth operations.
 * @param userDataStore The [UsersDataSource] instance used for Firestore profile operations.
 *
 * @see FirebaseAuthentication
 * @see UsersDataSource
 * @see AppContainer
 */
class AuthRepository(val authService: FirebaseAuthentication, val userDataStore: UsersDataSource) {

    /**
     * Returns whether a user is currently signed in by delegating directly to
     * [FirebaseAuthentication.isUserLoggedIn].
     *
     * Called on launch by [SignInViewModel]'s `init`
     * block to decide whether to skip the sign-in screen and restore the active session.
     *
     * @return `true` if a Firebase Auth session is active, `false` otherwise.
     */
    fun isUserLoggedIn(): Boolean = authService.isUserLoggedIn()

    /**
     * Returns the Firebase Auth UID of the currently signed-in user, or `null` if no
     * session is active. Prefer this over reading [FirebaseAuthentication] directly from
     * a ViewModel so the ViewModel stays decoupled from the data layer.
     */
    fun currentUserId(): String? = authService.getCurrentUserId()

    /**
     * Authenticates the user with email and password and returns their display name
     * for immediate use in the UI.
     *
     * Internally performs two sequential steps:
     * 1. Calls [FirebaseAuthentication.signIn] to authenticate with Firebase Auth.
     * 2. Calls [UsersDataSource.getUserProfile] to fetch the Firestore profile and
     *    extract the username.
     *
     * Called by [SignInViewModel.onSignInClick];
     * on success the returned username is stored in `SignInState.username` and the UI
     * navigates away from the sign-in screen.
     *
     * @param email    The user's email address.
     * @param password The user's password.
     * @return [Result.success] containing the user's username on success,
     *         or [Result.failure] with a descriptive exception on any step failing.
     */
    suspend fun signIn(email: String, password: String): Result<String> {
        val signInResult = authService.signIn(email, password)
        if (signInResult.isFailure) {
            return Result.failure(signInResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
        val currentUserUid = signInResult.getOrNull()?.uid
            ?: return Result.failure(Exception("Failed to get user UID after sign in"))

        val currentUserProfile = userDataStore.getUserProfile(currentUserUid).getOrNull()
            ?: return Result.failure(Exception("Failed to fetch user profile"))
        // Fetch user data after successful sign in
        return Result.success(currentUserProfile.username) // Return username along with email for UI display


    }

    /**
     * Fetches the display name of the currently signed-in user from Firestore.
     *
     * Called by [SignInViewModel]'s `init` block when
     * [isUserLoggedIn] returns `true` on app launch, so the username can be pre-populated
     * before navigating to the main screen without requiring the user to sign in again.
     *
     * Guards against the case where [FirebaseAuthentication.getCurrentUserId] returns `null`
     * despite [isUserLoggedIn] being `true` (should never happen in practice).
     *
     * @return [Result.success] containing the username string on success,
     *         or [Result.failure] with a descriptive exception if the user is not signed in
     *         or the Firestore profile cannot be fetched.
     */
    suspend fun getCurrentUserProfile(): Result<String> {
        if(!authService.isUserLoggedIn()) {
            return Result.failure(Exception("No user is currently signed in"))
        }
        val currentUserId = authService.getCurrentUserId()
            ?: return Result.failure(Exception("Failed to get current user ID")) // should never happen since we check isUserLoggedIn() above
        val currentUserProfile = userDataStore.getUserProfile(currentUserId).getOrNull()
            ?: return Result.failure(Exception("Failed to fetch user profile"))

        return Result.success(currentUserProfile.username)
    }

    /**
     * Creates a new Firebase Auth account and a matching Firestore user profile document
     * in a two-step sequence, with automatic rollback on partial failure.
     *
     * Steps:
     * 1. Calls [FirebaseAuthentication.signUp] to create the Firebase Auth account.
     * 2. Calls [UsersDataSource.createUserProfile] to create the Firestore profile.
     *    If step 2 fails, [FirebaseAuthentication.deleteCurrentUser] is called to roll back
     *    the Auth account created in step 1, keeping both sources of truth in sync.
     *
     * Called by [SignInViewModel.onCreateClick];
     * on success `SignInState.isSignInSuccess` is set to `true` and the UI navigates away.
     *
     * @param email    The email address for the new account.
     * @param password The password for the new account.
     * @param username The user's chosen display name, stored in the Firestore profile.
     * @return [Result.success] containing the [username] on success,
     *         or [Result.failure] with a descriptive exception on any step failing.
     */
    suspend fun register(email: String, password: String, username: String): Result<String> {
        val registerResult = authService.signUp(email, password)
        if (registerResult.isFailure) {
            return Result.failure(registerResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
        val uid = registerResult.getOrNull()?.uid ?: return Result.failure(Exception("Failed to get user UID after registration"))
        val createUserProfileResult =
            userDataStore.createUserProfile(uid = uid, username = username, email = email)
        if (createUserProfileResult.isFailure) {
            authService.deleteCurrentUser() // Rollback user creation in Firebase Authentication if profile creation fails
            return Result.failure(
                createUserProfileResult.exceptionOrNull() ?: Exception("Failed to create user profile")
            )
        }
        return Result.success(username)
    }

    /**
     * Sends a password-reset email to the specified address by delegating to
     * [FirebaseAuthentication.resetPassword].
     *
     * Called by [SignInViewModel.onPasswordReset];
     * on success the UI displays a confirmation message to the user.
     *
     * @param email The email address of the account whose password should be reset.
     * @return [Result.success] with [Unit] if the email was dispatched successfully,
     *         or [Result.failure] with the thrown exception on error.
     */
    suspend fun resetPassword(email: String): Result<String> {
        val resetResult = authService.resetPassword(email)
        if (resetResult.isFailure) {
            return Result.failure(resetResult.exceptionOrNull() ?: Exception("Unknown error"))
        }

        return Result.success("Reset email sent successfully") // Return a success message to display in the UI
    }

    /**
     * Signs out the currently authenticated user by delegating directly to
     * [FirebaseAuthentication.signOut].
     *
     */
    fun signOut() = authService.signOut()

    /**
     * Signs in the user with a Google account via the Credential Manager API.
     *
     * After Firebase authentication succeeds this method checks whether a Firestore
     * profile document already exists for the user:
     * - **Returning Google user** — the existing username is returned directly.
     * - **New Google user** — a profile is created from the Firebase display name (or email
     *   prefix as fallback); if profile creation fails the Auth account is deleted to keep
     *   both sources of truth in sync.
     *
     * Called by [SignInViewModel.onGoogleSignInClick].
     *
     * @param activityContext An [Activity] context required by [FirebaseAuthentication.signInWithGoogle].
     * @return [Result.success] containing the user's username on success,
     *         or [Result.failure] with a descriptive exception on any step failing.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<String> {
        return handleOAuthSignIn { authService.signInWithGoogle(activityContext) }
    }

    /**
     * Signs in the user with a GitHub account via Firebase's OAuthProvider (Chrome Custom Tab).
     *
     * Follows the same profile-creation pattern as [signInWithGoogle]:
     * - Returns the existing username for a returning user.
     * - Creates a new Firestore profile for a first-time GitHub user, with automatic Auth
     *   rollback if profile creation fails.
     *
     * @param activity The current [Activity], required by [FirebaseAuthentication.signInWithGitHub].
     * @return [Result.success] containing the user's username on success,
     *         or [Result.failure] with a descriptive exception on any step failing.
     */
    suspend fun signInWithGitHub(activity: Activity): Result<String> {
        return handleOAuthSignIn { authService.signInWithGitHub(activity) }
    }

    /**
     * Checks for a pending OAuth sign-in result (e.g. GitHub) that was started before the
     * Activity was killed by the OS while the Chrome Custom Tab was open.
     *
     * Should be called once on ViewModel init when the user is not yet signed in.
     *
     * @return [Result.success] with the username if a pending sign-in completed, [Result.failure]
     *         on error, or `null` if there was no pending result.
     */
    suspend fun checkPendingSignIn(): Result<String>? {
        val result = authService.checkPendingSignIn() ?: return null
        return handleOAuthUser(result)
    }

    /**
     * Shared entry point for all OAuth sign-in flows (Google, GitHub, etc.).
     *
     * Invokes the provided [signIn] lambda to obtain a [com.google.firebase.auth.FirebaseUser],
     * then delegates to [handleOAuthUser] to resolve or create the Firestore profile.
     *
     * @param signIn A suspending lambda that performs the provider-specific Firebase sign-in
     *               and returns a [Result] wrapping the authenticated [com.google.firebase.auth.FirebaseUser].
     * @return [Result.success] with the user's username, or [Result.failure] on any error.
     */
    private suspend fun handleOAuthSignIn(
        signIn: suspend () -> Result<com.google.firebase.auth.FirebaseUser>
    ): Result<String> = handleOAuthUser(signIn())

    /**
     * Resolves or creates a Firestore profile for a user who has just authenticated via OAuth.
     *
     * Decision logic:
     * - **Returning user** — a Firestore profile already exists for [signInResult]'s UID;
     *   the existing username is returned immediately without touching Firestore.
     * - **New user** — no profile exists; one is created using the Firebase display name,
     *   falling back to the email prefix, then `"User"` if neither is available.
     *
     * **Rollback**: if profile creation fails for a new user, [FirebaseAuthentication.deleteCurrentUser]
     * is called to remove the Firebase Auth account, keeping Auth and Firestore in sync.
     *
     * @param signInResult The [Result] from a provider-specific sign-in operation containing
     *                     the authenticated [com.google.firebase.auth.FirebaseUser] on success.
     * @return [Result.success] with the user's username on success,
     *         or [Result.failure] with a descriptive exception on any step failing.
     */
    private suspend fun handleOAuthUser(
        signInResult: Result<com.google.firebase.auth.FirebaseUser>
    ): Result<String> {
        if (signInResult.isFailure) {
            return Result.failure(signInResult.exceptionOrNull() ?: Exception("OAuth sign-in failed"))
        }
        val user = signInResult.getOrNull()
            ?: return Result.failure(Exception("Failed to get user after OAuth sign-in"))

        val existingProfile = userDataStore.getUserProfile(user.uid).getOrNull()
        if (existingProfile != null) return Result.success(existingProfile.username)

        val username = user.displayName
            ?: user.email?.substringBefore('@')
            ?: "User"
        val email = user.email ?: ""

        val createResult = userDataStore.createUserProfile(uid = user.uid, username = username, email = email)
        if (createResult.isFailure) {
            authService.deleteCurrentUser()
            return Result.failure(createResult.exceptionOrNull() ?: Exception("Failed to create user profile"))
        }
        return Result.success(username)
    }
}