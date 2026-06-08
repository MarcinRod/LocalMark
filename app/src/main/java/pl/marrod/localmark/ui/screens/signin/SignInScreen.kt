package pl.marrod.localmark.ui.screens.signin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import pl.marrod.localmark.R
import pl.marrod.localmark.di.AppViewModelProvider
import pl.marrod.localmark.ui.components.FormTextField
import pl.marrod.localmark.ui.components.GlassCard
import pl.marrod.localmark.ui.components.BaseOutlinedButton
import pl.marrod.localmark.ui.components.BaseProcessButton
import pl.marrod.localmark.ui.components.ambientShadow
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.ui.helpers.UiTextThrowable
import pl.marrod.localmark.ui.helpers.asString
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.Spacing


/**
 * Root sign-in screen composable.
 *
 * Collects [SignInViewModel.uiState] and observes [SignInState.isSignInSuccess] to invoke
 * [onAuthenticated] and trigger navigation to the main screen as soon as any auth flow
 * succeeds (email/password, Google, or GitHub).
 *
 * Delegates all rendering to [SignInScreenContent] and all user actions to [SignInViewModel]
 * via [SignInViewModel.onActionClick], keeping this composable free of business logic.
 *
 * @param viewModel       The [SignInViewModel] instance; defaults to the factory-provided one.
 * @param onAuthenticated Called with the user's display name when sign-in or registration
 *                        succeeds. The caller is responsible for navigating away.
 */
@Composable
fun SignInScreen(
    viewModel: SignInViewModel = viewModel(factory = AppViewModelProvider.factory),
    modifier: Modifier = Modifier,
    onAuthenticated: (userName: String) -> Unit = {},
) {
    val screenState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var signInResult by remember { mutableStateOf<Result<UiText>?>(null) }

    // Clear the result text whenever a new operation starts
    LaunchedEffect(screenState.isProcessing) {
        if (screenState.isProcessing) signInResult = null
    }

    // Collect one-shot result events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.authErrorEvent.collect { authError ->
            signInResult = Result.failure(UiTextThrowable(authError))


        }
    }

    LaunchedEffect(screenState.isSignInSuccess) {
        if (screenState.isSignInSuccess) {
            onAuthenticated(screenState.username)
        }
    }
    SignInScreenContent(
        screenState = screenState,
        signInResult = signInResult,
        onEmailChange = { viewModel.onEmailChange(it) },
        onPasswordChange = { viewModel.onPasswordChange(it) },
        onDisplayNameChange = { viewModel.onUsernameChange(it) },
        onActionClick = { viewModel.onActionClick(it, context) },
        onForgotPassword = { viewModel.onPasswordReset() },
        onModeChange = { viewModel.onModeChange(it) },
    )
}

/**
 * Stateless content layer for the sign-in screen.
 *
 * Renders a [Box] containing the [LogoBrand] header and a bottom-anchored card stack.
 * [SignInCard] and [RegisterCard] occupy the same slot and animate in/out when [SignInState.mode]
 * changes — only one is visible at a time.
 *
 * @param screenState        The current [SignInState] driving all visible elements.
 * @param onEmailChange      Called on every keystroke in the email field.
 * @param onPasswordChange   Called on every keystroke in the password field.
 * @param onDisplayNameChange Called on every keystroke in the display name field (register mode).
 * @param onActionClick      Dispatches a [SignInScreenAction] to the ViewModel.
 * @param onForgotPassword   Called when the "Forgot Password?" label is tapped.
 * @param onModeChange       Called when the user switches between sign-in and register cards.
 */
@Composable
fun SignInScreenContent(
    screenState: SignInState,
    signInResult: Result<UiText>?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onActionClick: (action: SignInScreenAction) -> Unit,
    onForgotPassword: () -> Unit,
    onModeChange: (action: SignInScreenAction) -> Unit,
) {

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        val availableHeight = maxHeight

        // ── Main content ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = availableHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.edgeMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            // ── Logo section ─────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                LogoBrand()
            }

            // ── Card stack – sign-in and register share the same slot ─────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {

                RegisterCard(
                    displayName = screenState.username,
                    email = screenState.email,
                    password = screenState.password,
                    displayNameError = screenState.usernameError,
                    emailError = screenState.emailError,
                    passwordError = screenState.passwordError,
                    isProcessing = screenState.isProcessing,
                    result = signInResult,
                    onDisplayNameChange = onDisplayNameChange,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onRegisterClick = { onActionClick(SignInScreenAction.REGISTER) },
                    onBackToSignIn = { onModeChange(SignInScreenAction.SIGN_IN) },
                    modifier = Modifier.fillMaxWidth(),
                    visible = screenState.mode == SignInScreenAction.REGISTER,
                )
                SignInCard(
                    email = screenState.email,
                    password = screenState.password,
                    emailError = screenState.emailError,
                    passwordError = screenState.passwordError,
                    onEmailChange = onEmailChange,
                    isProcessing = screenState.isProcessing,
                    isLoading = screenState.isLoading,
                    result = signInResult,
                    onPasswordChange = onPasswordChange,
                    onSignInClick = { onActionClick(SignInScreenAction.SIGN_IN) },
                    onGoogleSignIn = { onActionClick(SignInScreenAction.SIGN_IN_WITH_GOOGLE) },
                    onGitHubSignIn = { onActionClick(SignInScreenAction.SIGN_IN_WITH_GITHUB) },
                    onForgotPassword = onForgotPassword,
                    onCreateAccount = { onModeChange(SignInScreenAction.REGISTER) },
                    modifier = Modifier.fillMaxWidth(),
                    visible = screenState.mode == SignInScreenAction.SIGN_IN,
                )

            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared card-animation helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Produces animated `(rotationZ, alpha, isRendered)` values for a card that flips in/out.
 *
 * - **Enter** (`visible = true`): waits [enterDelayMillis], then animates rotation from
 *   [enterRotation] → `0°` (spring) and alpha `0 → 1` (tween). `isRendered` is set to `true`
 *   before the animation begins so the composable is in the tree during the enter transition.
 * - **Exit** (`visible = false`): animates rotation from `0°` → [exitRotation] and alpha
 *   `1 → 0`. `isRendered` is set to `false` only after the alpha reaches `0`, removing the
 *   composable from the tree so its offscreen layer does not composite over the card below.
 *
 * @param visible          Whether the card should be visible.
 * @param enterRotation    Starting rotation angle for the enter animation (degrees).
 * @param exitRotation     Target rotation angle for the exit animation (degrees).
 * @param enterDelayMillis Delay before the enter animation starts, in milliseconds.
 * @return A [Triple] of `(rotationZ, alpha, isRendered)`.
 */
@Composable
private fun rememberCardAnimation(
    visible: Boolean,
    enterRotation: Float = 90f,
    exitRotation: Float = -90f,
    enterDelayMillis: Long = 0,
): Triple<Float, Float, Boolean> {
    val rotation = remember { Animatable(if (visible) 0f else enterRotation) }
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val isRendered = remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            isRendered.value = true          // put in tree BEFORE animating in
            delay(enterDelayMillis)
            rotation.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f))
        } else {
            rotation.animateTo(
                exitRotation,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(enterDelayMillis)
            alpha.animateTo(
                1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        } else {
            alpha.animateTo(
                0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
            isRendered.value = false         // remove from tree AFTER fully faded out
        }
    }

    return Triple(rotation.value, alpha.value, isRendered.value)
}

// ─────────────────────────────────────────────────────────────────────────────
// SignInCard
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Animated glass card containing the email/password sign-in form.
 *
 * Displays the email and password fields, the primary sign-in button, an OR divider,
 * Google and GitHub sign-in buttons, and a link to switch to the registration card.
 * Flips in/out using [rememberCardAnimation] when [visible] changes.
 *
 * @param email           Current value of the email field.
 * @param password        Current value of the password field.
 * @param emailError      Inline validation error for the email field, or `null` if valid.
 * @param passwordError   Inline validation error for the password field, or `null` if valid.
 * @param result          Outcome of the last auth operation; drives the [BaseProcessButton] state.
 * @param onEmailChange   Called on every keystroke in the email field.
 * @param onPasswordChange Called on every keystroke in the password field.
 * @param onSignInClick   Called when the sign-in button is tapped.
 * @param onGoogleSignIn  Called when the "Sign in with Google" button is tapped.
 * @param onGitHubSignIn  Called when the "Sign in with GitHub" button is tapped.
 * @param onForgotPassword Called when the "Forgot Password?" trailing label is tapped.
 * @param onCreateAccount Called when the "Create a profile" link is tapped.
 * @param visible         Whether this card is currently the active card in the stack.
 * @param isProcessing    `true` while an auth call is in flight; disables buttons.
 * @param isLoading       `true` during the initial session check; shows a loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInCard(
    email: String,
    password: String,
    emailError: UiText?,
    passwordError: UiText?,
    result: Result<UiText>?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGitHubSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    isProcessing: Boolean,
    isLoading: Boolean,
) {
    val (rotation, alpha, isRendered) = rememberCardAnimation(
        visible = visible,
        enterRotation = -90f,
        exitRotation = 90f,
        enterDelayMillis = 300,
    )

    if (!isRendered) return

    GlassCard(
        cardShape = RoundedCornerShape(32.dp),
        modifier = modifier
            .ambientShadow(offsetY = 12.dp, blurRadius = 40.dp)
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
                transformOrigin = TransformOrigin(0.5f, 1.0f)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.edgeMargin, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.stackGap * 2f),
        ) {
            // ── Title ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = Spacing.unit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.welcome_back),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.04.em,
                    ),
                    color = MaterialTheme.colorScheme.primaryFixed,
                )
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primaryFixed,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.30f),
                    )
                }
            }
            // ── Input fields ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                FormTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    isError = emailError != null,
                    errorMessage = emailError?.asString(),
                    label = stringResource(R.string.email),
                    leadingIcon = Icons.TwoTone.Email,
                    placeholder = stringResource(R.string.email_placeholder),
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )
                FormTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    isError = passwordError != null,
                    errorMessage = passwordError?.asString(),
                    label = stringResource(R.string.password),
                    leadingIcon = Icons.TwoTone.Lock,
                    placeholder = "••••••••",
                    isPassword = true,
                    trailingLabelText = stringResource(R.string.forgot_password),
                    onTrailingLabelClick = onForgotPassword,
                    imeAction = ImeAction.Done,
                )
            }

            // ── Sign-in button ────────────────────────────────────────────
            BaseProcessButton(
                text = stringResource(R.string.sign_in),
                onClick = { onSignInClick() },
                icon = Icons.AutoMirrored.TwoTone.Login,
                modifier = Modifier.fillMaxWidth(),
                result = result,
                isProcessing = isProcessing,
                enabled = !isProcessing && !isLoading,
                height = 48.dp,
            )

            // ── OR divider ────────────────────────────────────────────
            OrDivider()

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                // ── Google button ─────────────────────────────────────────
                GoogleSignInButton(
                    onClick = onGoogleSignIn,
                    height = 48.dp,
                    enabled = !isProcessing && !isLoading
                )

                // ── GitHub button ─────────────────────────────────────────
                GitHubSignInButton(
                    onClick = onGitHubSignIn,
                    height = 48.dp,
                    enabled = !isProcessing && !isLoading
                )
            }

            // ── Registration link ─────────────────────────────────────────────
            TextWithLink(
                text = stringResource(R.string.new_to_grid),
                linkText = stringResource(R.string.create_a_profile),
                onLinkClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth()
            )

        }
    }
}

/**
 * Animated glass card containing the registration form.
 *
 * Displays display name, email, and password fields, the create-profile button, and a
 * link to switch back to the sign-in card. Flips in/out using [rememberCardAnimation]
 * when [visible] changes.
 *
 * @param displayName          Current value of the display name field.
 * @param email                Current value of the email field.
 * @param password             Current value of the password field.
 * @param displayNameError     Inline validation error for the display name field, or `null` if valid.
 * @param emailError           Inline validation error for the email field, or `null` if valid.
 * @param passwordError        Inline validation error for the password field, or `null` if valid.
 * @param result               Outcome of the last auth operation; drives the [BaseProcessButton] state.
 * @param onDisplayNameChange  Called on every keystroke in the display name field.
 * @param onEmailChange        Called on every keystroke in the email field.
 * @param onPasswordChange     Called on every keystroke in the password field.
 * @param onRegisterClick      Called when the create-profile button is tapped.
 * @param onBackToSignIn       Called when the "Already have an account?" link is tapped.
 * @param visible              Whether this card is currently the active card in the stack.
 * @param isProcessing         `true` while a registration call is in flight; disables the button.
 */
@Composable
fun RegisterCard(
    displayName: String,
    email: String,
    password: String,
    displayNameError: UiText?,
    emailError: UiText?,
    passwordError: UiText?,
    result: Result<UiText>?,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    isProcessing: Boolean,
) {

    val (rotation, alpha, isRendered) = rememberCardAnimation(
        visible = visible,
        enterRotation = 90f,
        exitRotation = -90f,
        enterDelayMillis = 300,
    )

    if (!isRendered) return

    GlassCard(
        cardShape = RoundedCornerShape(32.dp),
        modifier = modifier
            .ambientShadow(offsetY = 12.dp, blurRadius = 40.dp)
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
                transformOrigin = TransformOrigin(0.5f, 1.0f)
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.edgeMargin, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.stackGap * 2),
        ) {
            // ── Title ─────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.create_profile),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.04.em,
                ),
                color = MaterialTheme.colorScheme.primaryFixed,
            )

            // ── Input fields ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                FormTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    isError = displayNameError != null,
                    errorMessage = displayNameError?.asString(),
                    label = stringResource(R.string.display_name),
                    leadingIcon = Icons.TwoTone.Person,
                    placeholder = stringResource(R.string.username_placeholder),
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                )
                FormTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    isError = emailError != null,
                    errorMessage = emailError?.asString(),
                    label = stringResource(R.string.email),
                    leadingIcon = Icons.TwoTone.Email,
                    placeholder = stringResource(R.string.email_placeholder),
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )
                FormTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    isError = passwordError != null,
                    errorMessage = passwordError?.asString(),
                    label = stringResource(R.string.password),
                    leadingIcon = Icons.TwoTone.Lock,
                    placeholder = "••••••••",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                )
            }

            // ── Register button ───────────────────────────────────────────
            BaseProcessButton(
                text = stringResource(R.string.create_profile),
                onClick = { onRegisterClick() },
                icon = Icons.Default.PersonAdd,
                modifier = Modifier.fillMaxWidth(),
                isProcessing = isProcessing,
                result = result,
                height = 48.dp,
            )


            // ── Back to sign-in link ──────────────────────────────────────
            TextWithLink(
                text = stringResource(R.string.have_an_account),
                linkText = stringResource(R.string.sign_in),
                onLinkClick = onBackToSignIn,
                modifier = Modifier.fillMaxWidth()
            )

        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogoBrand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.unit),
    ) {
        // Circle icon badge
        Box(
            modifier = Modifier
                .size(64.dp)
                .ambientShadow(offsetY = 12.dp, blurRadius = 32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    Modifier // border via outline-variant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.localmark),
                contentDescription = "NearbyAlerts",
                tint = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.stackGap))

        // Headline
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.12.em,
            ),
            color = MaterialTheme.colorScheme.primaryFixed,
            textAlign = TextAlign.Center,
        )

        // Subtitle
        Text(
            text = stringResource(R.string.signin_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.05.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.or),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.edgeMargin),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MediumContainerHeight
) {
    BaseOutlinedButton(
        text = stringResource(R.string.sign_in_with_google),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        height = height,
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_google_logo),
            contentDescription = "Google",
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GitHubSignInButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MediumContainerHeight
) {
    BaseOutlinedButton(
        text = stringResource(R.string.sign_in_with_github),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        height = height,
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github_mark),
            contentDescription = "GitHub",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}


/**
 * Inline row displaying a plain text label followed by a tappable [TextButton] link.
 *
 * Used at the bottom of both [SignInCard] ("No account? Create a profile") and
 * [RegisterCard] ("Already have an account? Sign in") to switch between the two cards.
 *
 * @param text        The non-tappable prefix label.
 * @param linkText    The tappable link text rendered in [MaterialTheme.colorScheme.primaryContainer].
 * @param onLinkClick Called when [linkText] is tapped.
 */
@Composable
fun TextWithLink(
    text: String,
    linkText: String,
    onLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onLinkClick,
            contentPadding = PaddingValues(
                horizontal = 2.dp,
                vertical = 0.dp
            ),
        ) {
            Text(
                text = linkText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }
}


