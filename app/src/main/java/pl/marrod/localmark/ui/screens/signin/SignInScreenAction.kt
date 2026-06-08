package pl.marrod.localmark.ui.screens.signin

import androidx.annotation.StringRes
import pl.marrod.localmark.R

/**
 * Controls whether the sign-in/register screen is in sign-in or registration mode.
 *
 * Rendered as a segmented control in [pl.marrod.localmark.ui.screens.signin.SignInScreen].
 * Switching mode clears all field-level validation errors via [SignInViewModel.onModeChange].
 *
 * @property label String resource ID for the segment label.
 */
enum class SignInScreenAction(@param:StringRes val label: Int, @param:StringRes val failMessage: Int) {
    /** The screen shows email + password fields. */
    SIGN_IN(R.string.sign_in, failMessage = R.string.sign_in_failed),

    /** The screen shows email + username + password fields. */
    REGISTER(R.string.create_account, failMessage = R.string.sign_up_failed),

    /** Triggers the Google Sign-In flow via Credential Manager. */
    SIGN_IN_WITH_GOOGLE(R.string.sign_in_with_google, failMessage = R.string.sign_in_with_google_failed),

    /** Triggers the GitHub OAuth flow via a Chrome Custom Tab. */
    SIGN_IN_WITH_GITHUB(R.string.sign_in_with_github, failMessage = R.string.sign_in_with_github_failed),

    /** Triggers tha password reset flow*/
    RESET_PASSWORD(R.string.password_reset_email_sent, failMessage = R.string.password_reset_failed),
}