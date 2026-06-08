package pl.marrod.localmark.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.marrod.localmark.LocalMarkApplication
import pl.marrod.localmark.di.AppViewModelProvider.factory
import pl.marrod.localmark.ui.screens.map.MapViewModel
import pl.marrod.localmark.ui.screens.signin.SignInViewModel
import pl.marrod.localmark.ui.screens.signin.SignInScreen
import pl.marrod.localmark.ui.screens.map.MapScreen
/**
 * Application-wide [ViewModelProvider.Factory] that wires each ViewModel with its
 * repository dependencies sourced from [AppContainer].
 *
 * The container itself is obtained via [LocalMarkApplication], which casts the
 * [ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] entry from [CreationExtras]
 * to [LocalMarkApplication] and reads its lazily-initialised
 * [LocalMarkApplication.appContainer].
 *
 * [factory] is passed as the `factory` argument to Jetpack Compose's `viewModel()` call
 * in each screen composable:
 *
 * | ViewModel | Screen | Dependencies injected |
 * |---|---|---|
 * | [SignInViewModel] | [SignInScreen] | `authRepository` |
 * | [MapViewModel] | [MapScreen] | `locationProvider`, `placesRepository`, `geocodingRepository`, `authRepository`, `markersRepository` |
 *
 * @see AppContainer
 * @see LocalMarkApplication
 */
object AppViewModelProvider {
    /**
     * The [ViewModelProvider.Factory] instance built with [viewModelFactory].
     *
     * Each [initializer] block corresponds to one ViewModel type. Compose's `viewModel()`
     * selects the correct initializer at runtime by matching the requested ViewModel class
     * against the registered types.
     */
    val factory = viewModelFactory {

        initializer {
            SignInViewModel(localMarkApplication().appContainer.authRepository)
        }

        initializer {
            val appContainer = localMarkApplication().appContainer
            MapViewModel(
                savedStateHandle = createSavedStateHandle(),
                locationProvider = appContainer.locationProvider,
                placesRepository = appContainer.placesRepository,
                geocodingRepository = appContainer.geocodingRepository,
                authRepository = appContainer.authRepository,
                markersRepository = appContainer.markersRepository,
            )
        }

    }
}

/**
 * Convenience extension on [CreationExtras] that retrieves the [LocalMarkApplication]
 * instance from the [ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] entry.
 *
 * Used by every [initializer] block in [AppViewModelProvider.factory] to access
 * [LocalMarkApplication.appContainer] without holding a static reference
 * to the Application object.
 *
 * @return The application cast to [LocalMarkApplication].
 * @throws ClassCastException if [ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
 *         is not set or refers to a different [android.app.Application] subclass.
 */
fun CreationExtras.localMarkApplication(): LocalMarkApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LocalMarkApplication)