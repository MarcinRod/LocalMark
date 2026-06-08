package pl.marrod.localmark.ui.screens.signin

import pl.marrod.localmark.ui.helpers.UiText

/**
 * Full UI state for [SignInScreen].
 *
 * Produced by [SignInViewModel] and consumed by the screen composable to drive
 * every visible element: form fields, loading indicators, error labels, and
 * the success-navigation trigger.
 *
 * @property mode             Whether the screen is in [SignInScreenAction.SIGN_IN] or [SignInScreenAction.REGISTER] mode.
 * @property email            Current value of the email text field.
 * @property username         Current value of the username text field (visible in [SignInScreenAction.REGISTER]
 *                            and populated from the provider display name after a successful OAuth sign-in).
 * @property password         Current value of the password text field.
 * @property isLoading        `true` during the initial session-check in `init`; the screen
 *                            shows a loading indicator until this becomes `false`.
 * @property isProcessing     `true` while a sign-in, register, or password-reset network
 *                            call is in flight; disables submit buttons and shows a spinner.
 * @property emailError       Inline validation error shown beneath the email field. `null` when valid.
 * @property passwordError    Inline validation error shown beneath the password field. `null` when valid.
 * @property usernameError    Inline validation error shown beneath the username field. `null` when valid.
 * @property isSignInSuccess  Set to `true` when authentication succeeds; observed by
 *                            [SignInScreen] to trigger
 *                            the `onAuthenticated` callback and navigate to the main screen.
 */
data class SignInState(
    val mode: SignInScreenAction = SignInScreenAction.SIGN_IN,
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val usernameError: UiText? = null,
    val isSignInSuccess: Boolean = false,
)
