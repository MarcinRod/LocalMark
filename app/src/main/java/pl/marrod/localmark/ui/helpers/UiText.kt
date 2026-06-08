package pl.marrod.localmark.ui.helpers

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.marrod.localmark.R


sealed class UiText {

    /**
     * A runtime string value, typically an exception message from the data layer.
     *
     * Created by [toUiText] when a [Throwable] has a non-null [Throwable.message].
     * Resolved as-is by both [asString] overloads — no resource lookup is performed.
     *
     * @param value The string to display directly.
     */
    data class Dynamic(val value: String) : UiText()

    /**
     * A string resource reference resolved at display time using the current locale.
     *
     * Use this variant whenever the message text is known at compile time (e.g.
     * validation errors, fixed status messages). Resolves via [stringResource] in
     * Composable contexts or [Context.getString] in non-Composable contexts.
     *
     * @param resId      The string resource ID (e.g. `R.string.error_email_required`).
     * @param formatArgs Optional format arguments passed to the resource string,
     *                   matching the `%s` / `%d` placeholders in `strings.xml`.
     *                   Defaults to an empty list when the string has no placeholders.
     */
    data class Resource(val resId: Int, val formatArgs: List<Any> = emptyList()) : UiText()
}

/**
 * Resolves this [UiText] to a plain [String] inside a Composable function.
 *
 * - [UiText.Dynamic] — returns [UiText.Dynamic.value] directly.
 * - [UiText.Resource] — calls [stringResource] with [UiText.Resource.resId] and
 *   [UiText.Resource.formatArgs], respecting the current composition locale.
 *
 * Used by screen composables (e.g. `SignInScreen`, `DiscoverScreen`) to render
 * `UiText` values held in ViewModel state as text or error labels.
 */
@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> stringResource(id = resId, formatArgs = formatArgs.toTypedArray())
    }
}

/**
 * Resolves this [UiText] to a plain [String] outside of a Composable function,
 * using a [Context] to access string resources.
 *
 * - [UiText.Dynamic] — returns [UiText.Dynamic.value] directly.
 * - [UiText.Resource] — calls [Context.getString] with [UiText.Resource.resId] and
 *   [UiText.Resource.formatArgs].
 *
 * Used in screen composables where a non-Composable scope is needed — for example,
 * `VaultScreen`, `DiscoverScreen`, and `DetailScreen` call this overload inside
 * `LaunchedEffect` blocks to resolve error messages before passing them to
 * `SnackbarHostState.showSnackbar()`.
 *
 * @param context The [Context] used to resolve the string resource.
 */
fun UiText.asString(context: Context): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> context.getString( resId,  formatArgs)
    }
}

/**
 * Converts a nullable [Throwable] to a [UiText] suitable for display in the UI.
 *
 * - If `this` is a [UiTextThrowable], returns its [UiTextThrowable.uiText] directly.
 * - If `this` is non-null and has a non-blank [Throwable.message], returns a
 *   [UiText.Dynamic] wrapping the message string.
 * - Otherwise (null throwable or null/blank message) falls back to
 *   [UiText.Resource] with [R.string.error_unknown], ensuring the UI always
 *   has something informative to show.
 */
fun Throwable?.toUiText(): UiText = when {
    this is UiTextThrowable -> uiText
    this?.message?.isNotBlank() == true -> UiText.Dynamic(message!!)
    else -> UiText.Resource(R.string.error_unknown)
}

/**
 * A [Throwable] that carries a [UiText] payload instead of a plain string message.
 *
 * Use this to wrap localised error messages inside [Result.failure] when the result
 * type parameter is [UiText]:
 *
 * ```kotlin
 * Result.failure<UiText>(UiTextThrowable(UiText.Resource(R.string.error_unknown)))
 * ```
 *
 * Recover the message symmetrically with [Result.uiText] or [toUiText].
 */
class UiTextThrowable(val uiText: UiText) : Throwable()

/**
 * Extracts the [UiText] from either side of a `Result<UiText>`:
 * - On success returns [Result.getOrThrow].
 * - On failure converts the [Throwable] via [toUiText] (handles [UiTextThrowable]
 *   and plain exceptions uniformly).
 */
fun Result<UiText>.uiText(): UiText = getOrElse { it.toUiText() }
