package pl.marrod.localmark.ui.screens.signin

import android.app.Activity
import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.marrod.localmark.R
import pl.marrod.localmark.data.repository.AuthRepository
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.di.AppViewModelProvider
import pl.marrod.localmark.di.AppContainer
import pl.marrod.localmark.data.firebase.FirebaseAuthentication
/**
 * ViewModel for [SignInScreen].
 *
 * Handles five authentication flows via [AuthRepository]:
 * 1. **Session restore** (`init`) — checks [AuthRepository.isUserLoggedIn]
 *    and fetches the username if already signed in, setting [SignInState.isSignInSuccess] to skip
 *    the sign-in form entirely.
 * 2. **Pending OAuth recovery** (`init`) — if no session exists, checks for a pending OAuth result
 *    left by an interrupted Chrome Custom Tab flow (e.g. Activity killed during GitHub sign-in).
 * 3. **Email/password sign-in** ([onSignInClick]) — validates fields, calls
 *    [AuthRepository.signIn], stores the returned username.
 * 4. **Registration** ([onCreateClick]) — validates all three fields, calls
 *    [AuthRepository.register].
 * 5. **Google sign-in** ([onGoogleSignInClick]) — delegates to
 *    [AuthRepository.signInWithGoogle] via Credential Manager.
 * 6. **GitHub sign-in** ([onGitHubSignInClick]) — delegates to
 *    [AuthRepository.signInWithGitHub] via Chrome Custom Tab.
 *
 * Additionally, supports **password reset** ([onPasswordReset]).
 *
 * ### Field validation
 * All validation is performed client-side before any network call:
 * - [validateEmail] — blank check + [android.util.Patterns.EMAIL_ADDRESS] regex.
 * - [validatePassword] — blank check, minimum 6 characters, at least one uppercase letter,
 *   and at least one special character.
 * - [validateUsername] — blank check + minimum 3 characters.
 *
 * Errors are written into the corresponding [SignInState] error properties and cleared
 * as soon as the user modifies the respective field.
 *
 * Instantiated by [AppViewModelProvider.factory] with
 * [AppContainer.authRepository].
 *
 * @param authRepository The repository used for all authentication operations.
 *
 * @see SignInScreen
 * @see AuthRepository
 */
class SignInViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInState())
    val uiState = _uiState.asStateFlow()

    private val _authErrorEvent = MutableSharedFlow<UiText>()
    val authErrorEvent = _authErrorEvent.asSharedFlow()

    /**
     * On first creation, runs three checks in order:
     * 1. **Active session** — if [AuthRepository.isUserLoggedIn]
     *    returns `true`, fetches the username and sets [SignInState.isSignInSuccess] after a short
     *    delay to allow the UI animation to complete before navigating away.
     * 2. **Pending OAuth result** — if no session exists, checks for an unfinished OAuth flow
     *    (e.g. the Activity was killed while a GitHub Chrome Custom Tab was open). If found,
     *    the result is completed automatically via [handleAuthResult].
     * 3. **Fresh state** — if neither condition applies, sets [SignInState.isLoading] to `false`
     *    so the sign-in form is displayed.
     */
    init {
        _uiState.update { it.copy(isLoading = true) }
        // On launch, check for an existing Firebase session to skip the sign-in form.
        // A 1-second delay lets the UI finish its animation before navigating away.
        viewModelScope.launch {
            val isSignedIn = authRepository.isUserLoggedIn()
            if (isSignedIn) {
                delay(1000) // Simulate loading
                val result = authRepository.getCurrentUserProfile()
                handleAuthResult(action = SignInScreenAction.SIGN_IN, result = result)
            } else {
                // Check for a pending OAuth result — happens when the Activity was killed by the OS
                // while a Chrome Custom Tab (e.g. GitHub sign-in) was open and the user returned.
                val pendingResult = authRepository.checkPendingSignIn()
                if (pendingResult != null) {
                    handleAuthResult(
                        action = SignInScreenAction.SIGN_IN_WITH_GOOGLE,
                        result = pendingResult
                    )
                } else {
                    // No active session and no pending result — show the sign-in form.
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /**
     * Validates [email] against a blank check and [android.util.Patterns.EMAIL_ADDRESS].
     *
     * @return A localised [UiText] error, or `null` if the email is valid.
     */
    private fun validateEmail(email: String): UiText? = when {
        email.isBlank() -> UiText.Resource(R.string.error_email_required)
        !Patterns.EMAIL_ADDRESS.matcher(email)
            .matches() -> UiText.Resource(R.string.error_email_invalid)

        else -> null
    }

    /**
     * Validates [password] against a blank check and a minimum length of 6 characters.
     *
     * @return A localised [UiText] error, or `null` if the password is valid.
     */
    private fun validatePassword(password: String): UiText? = when {
        password.isBlank() -> UiText.Resource(R.string.error_password_required)
        password.length < 6 -> UiText.Resource(R.string.error_password_too_short)
        !Regex("[A-Z]").containsMatchIn(password) -> UiText.Resource(R.string.error_password_no_uppercase)
        !Regex("[^A-Za-z0-9]").containsMatchIn(password) -> UiText.Resource(R.string.error_password_no_special_char)
        else -> null
    }

    /**
     * Validates [username] against a blank check and a minimum length of 3 characters.
     *
     * @return A localised [UiText] error, or `null` if the username is valid.
     */
    private fun validateUsername(username: String): UiText? = when {
        username.isBlank() -> UiText.Resource(R.string.error_username_required)
        username.length < 3 -> UiText.Resource(R.string.error_username_too_short)
        else -> null
    }


    /**
     * Updates [SignInState.email] and clears [SignInState.emailError].
     * Called on every keystroke in the email text field.
     * @param email The new email field value.
     */
    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null) }
    }

    /**
     * Updates [SignInState.password] and clears [SignInState.passwordError].
     * Called on every keystroke in the password text field.
     * @param password The new password field value.
     */
    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
    }

    /**
     * Updates [SignInState.username] and clears [SignInState.usernameError].
     * Called on every keystroke in the username text field (registration mode only).
     * @param username The new username field value.
     */
    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, usernameError = null) }
    }

    /**
     * Switches between [SignInScreenAction.SIGN_IN] and [SignInScreenAction.REGISTER] and clears
     * all field-level validation errors so stale errors don't carry over between modes.
     *
     * @param option The newly selected [SignInScreenAction].
     */
    fun onModeChange(option: SignInScreenAction) {
        _uiState.update {
            it.copy(mode = option, emailError = null, passwordError = null, usernameError = null)
        }
    }

    /**
     * Validates the email field and, if valid, sends a password-reset email via
     * [AuthRepository.resetPassword].
     *
     * Sets [SignInState.isProcessing] during the network call.
     * On success/error emits a localised message via [authErrorEvent].
     */
    fun onPasswordReset() {
        val emailError = validateEmail(_uiState.value.email)
        _uiState.update { it.copy(emailError = emailError) }
        if (emailError != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = authRepository.resetPassword(_uiState.value.email)
            _uiState.update { it.copy(isProcessing = false) }
            if (result.isFailure) {
                Log.w(
                    "SignIn",
                    "Password reset failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }


    /**
     * Validates the email and password fields and, if valid, calls
     * [AuthRepository.signIn].
     *
     * On success stores the returned username in [SignInState.username] and sets
     * [SignInState.isSignInSuccess] to `true`, which the screen observes to invoke
     * `onAuthenticated` and navigate to the main screen.
     * On failure emits a localised error via [authErrorEvent].
     */
    fun onSignInClick() {
        val emailError = validateEmail(_uiState.value.email)
        val passwordError = validatePassword(_uiState.value.password)
        _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
        if (emailError != null || passwordError != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            handleAuthResult(
                action = SignInScreenAction.SIGN_IN,
                result = authRepository.signIn(_uiState.value.email, _uiState.value.password)
            )
        }
    }

    /**
     * Validates all three fields (username, email, password) and, if all pass, calls
     * [AuthRepository.register].
     *
     * On success sets [SignInState.isSignInSuccess] to `true`, triggering navigation.
     * On failure emits a localised error via [authErrorEvent]. If Firestore profile
     * creation fails after the Firebase Auth account is created,
     * [AuthRepository.register] rolls back the Auth
     * account automatically.
     */
    fun onCreateClick() {
        val usernameError = validateUsername(_uiState.value.username)
        val emailError = validateEmail(_uiState.value.email)
        val passwordError = validatePassword(_uiState.value.password)
        _uiState.update {
            it.copy(
                usernameError = usernameError,
                emailError = emailError,
                passwordError = passwordError
            )
        }
        if (usernameError != null || emailError != null || passwordError != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            handleAuthResult(
                action = SignInScreenAction.REGISTER, result =
                    authRepository.register(
                        username = _uiState.value.username,
                        email = _uiState.value.email,
                        password = _uiState.value.password
                    )
            )
        }
    }

    /**
     * Initiates the Google Sign-In flow via [AuthRepository.signInWithGoogle].
     *
     * Uses the Credential Manager API to present the "Sign in with Google" bottom sheet.
     * The Web Client ID is read from `BuildConfig.GOOGLE_WEB_CLIENT_ID` (auto-generated from
     * `google-services.json`).
     *
     * On success, [handleAuthResult] stores the username and triggers navigation.
     * On failure, an error message is emitted via [authErrorEvent].
     *
     * @param context An Activity context required by the Credential Manager API.
     */
    private fun onGoogleSignInClick(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            handleAuthResult(
                action = SignInScreenAction.SIGN_IN_WITH_GOOGLE,
                result = authRepository.signInWithGoogle(context)
            )
        }
    }

    /**
     * Initiates the GitHub Sign-In flow via [AuthRepository.signInWithGitHub].
     *
     * Opens a Chrome Custom Tab pointing to the Firebase-hosted GitHub OAuth page.
     * If the Activity is killed while the tab is open, the `init` block will detect and complete
     * the pending result when the user returns to the app.
     *
     * @param context An Activity context required by [FirebaseAuthentication.signInWithGitHub].
     */
    private fun onGitHubSignInClick(context: Context) {
        val activity = context as? Activity ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            handleAuthResult(
                action = SignInScreenAction.SIGN_IN_WITH_GITHUB,
                result = authRepository.signInWithGitHub(activity)
            )
        }
    }

    /**
     * Shared result handler for any sign-in method and registration.
     *
     * On success sets [SignInState.isSignInSuccess] and stores the returned username.
     * On failure emits a localised error via [authErrorEvent].
     *
     * @param result The [Result] returned by the repository auth call.
     */
    private fun handleAuthResult(action: SignInScreenAction, result: Result<String>) {
        if (result.isFailure) {
            _uiState.update { it.copy(isProcessing = false) }
            Log.w(
                "SignIn",
                "Authentication failed for action $action: ${result.exceptionOrNull()?.message}"
            )
            _authErrorEvent.tryEmit(
                UiText.Resource(action.failMessage)
            )
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isProcessing = false,
                    isSignInSuccess = true,
                    username = result.getOrNull() ?: ""
                )
            }
        }
    }

    /**
     * Routes a [SignInScreenAction] dispatched from the UI to the appropriate handler.
     *
     * Acts as the single public entry point for all user-initiated actions on the sign-in
     * screen, keeping the composable layer free of flow-dispatch logic.
     *
     * @param action  The action triggered by the user (sign-in, register, or an OAuth flow).
     * @param context An [Activity] context forwarded to OAuth handlers that require it.
     */
    fun onActionClick(action: SignInScreenAction, context: Context) {
        when (action) {
            SignInScreenAction.SIGN_IN -> onSignInClick()
            SignInScreenAction.REGISTER -> onCreateClick()
            SignInScreenAction.SIGN_IN_WITH_GOOGLE -> onGoogleSignInClick(context)
            SignInScreenAction.SIGN_IN_WITH_GITHUB -> onGitHubSignInClick(context)
            SignInScreenAction.RESET_PASSWORD -> { /* reset is handled by a separate button, not routed through this function */
            }
        }
    }


}
