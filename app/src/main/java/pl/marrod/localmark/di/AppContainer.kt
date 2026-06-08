package pl.marrod.localmark.di

import android.content.Context
import com.google.android.libraries.places.api.Places
import pl.marrod.localmark.BuildConfig
import pl.marrod.localmark.data.firebase.UsersDataSource
import pl.marrod.localmark.data.repository.AuthRepository
import pl.marrod.localmark.data.repository.GeocodingRepository
import pl.marrod.localmark.data.repository.PlacesRepository
import pl.marrod.localmark.location.LocationProvider
import pl.marrod.localmark.data.firebase.FirebaseAuthentication
import pl.marrod.localmark.data.firebase.FirebaseProvider
import pl.marrod.localmark.data.firebase.MarkersDataSource
import pl.marrod.localmark.data.firebase.MarkersImageSource
import pl.marrod.localmark.data.repository.MarkersRepository
import pl.marrod.localmark.LocalMarkApplication
import pl.marrod.localmark.ui.screens.signin.SignInViewModel
import pl.marrod.localmark.ui.screens.map.MapViewModel
/**
 * Manual dependency-injection container for the LocalMark application.
 *
 * A single instance is held by [LocalMarkApplication] as a lazy property,
 * ensuring it is created once and shared across the entire process lifetime. All repositories
 * exposed here are also lazy so their underlying resources (Firestore, Retrofit) are
 * initialised only when first accessed.
 *
 * The container is consumed exclusively by [AppViewModelProvider], which reads the
 * repository properties to construct each ViewModel:
 *
 * | Repository / Provider | Injected into |
 * |---|---|
 * | [authRepository] | [SignInViewModel], [MapViewModel] |
 * | [markersRepository] | [MapViewModel] |
 * | [placesRepository] | [MapViewModel] |
 * | [geocodingRepository] | [MapViewModel] |
 * | [locationProvider] | [MapViewModel] |
 *
 * ### Dependency graph
 * ```
 * AppContainer
 * ├── locationProvider ──────────────────────────────► MapViewModel
 * ├── placesRepository (Places SDK) ─────────────────► MapViewModel
 * ├── geocodingRepository (Geocoding REST API) ──────► MapViewModel
 * ├── firebaseAuthentication (FirebaseProvider.auth)
 * │   └── authRepository ────────────────────────────► SignInViewModel
 * │                       ────────────────────────────► MapViewModel
 * ├── usersDataSource (FirebaseProvider.firestore)
 * │   └── authRepository (shared, see above)
 * ├── markersDataSource (FirebaseProvider.firestore)
 * │   └── markersRepository ─────────────────────────► MapViewModel
 * └── markersImageSource (FirebaseProvider.storage)
 *     └── markersRepository (shared, see above)
 * ```
 *
 * @see LocalMarkApplication
 * @see AppViewModelProvider
 */
class AppContainer(context: Context) {

    /**
     * Initialises the Places SDK with the API key from [BuildConfig] if it hasn't been
     * initialised already. This ensures that the SDK is ready to use when [placesRepository] is
     * first accessed, without requiring the caller to worry about SDK initialisation.
     */
    init {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                context.applicationContext,
                BuildConfig.MAPS_API_KEY
            )
        }
    }

    /**
     * Shared [FirebaseAuthentication] instance wrapping [FirebaseProvider.auth].
     * Injected into both [authRepository] and [markersRepository].
     */
    private val firebaseAuthentication = FirebaseAuthentication(
        firebaseAuth = FirebaseProvider.auth,
        googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    )

    /**
     * Shared [UsersDataSource] instance wrapping [FirebaseProvider.firestore].
     * Injected into [authRepository] for user profile read/write operations.
     */
    private val usersDataSource = UsersDataSource(FirebaseProvider.firestore)

    /**
     * Shared [MarkersDataSource] instance wrapping [FirebaseProvider.firestore].
     * Injected into [markersRepository] for CRUD operations on marker documents.
     */
    private val markersDataSource = MarkersDataSource(FirebaseProvider.firestore)

    /**
     * Shared [MarkersImageSource] instance wrapping [FirebaseProvider.firebaseStorage].
     * Injected into [markersRepository] for uploading and downloading marker images.
     */
    private val markersImageSource = MarkersImageSource(FirebaseProvider.firebaseStorage)

    /**
     * Shared [LocationProvider] instance wrapping the fused location provider client.
     * Injected into [MapViewModel] via [AppViewModelProvider] to provide location updates for map centering and nearby place queries.
     */
    val locationProvider by lazy { LocationProvider(context) }

    /**
     * Shared [AuthRepository] instance wrapping [firebaseAuthentication] and [usersDataSource].
     * Injected into [SignInViewModel] and [MapViewModel] via [AppViewModelProvider] to provide authentication and user profile functionality.
     */
    val authRepository by lazy { AuthRepository(firebaseAuthentication, usersDataSource) }

    /**
     * Shared [MarkersRepository] instance wrapping [markersDataSource] and [markersImageSource].
     * Injected into [MapViewModel] via [AppViewModelProvider] to provide CRUD operations for map markers, including image upload and download support.
     */
    val markersRepository by lazy { MarkersRepository(markersDataSource = markersDataSource,markersImageSource = markersImageSource) }

    /**
     * Shared [PlacesRepository] instance wrapping the Places SDK client.
     * Injected into [MapViewModel] via [AppViewModelProvider] to provide nearby place queries and place details.
     */
    val placesRepository by lazy { PlacesRepository(Places.createClient(context)) }

    /**
     * Shared [GeocodingRepository] instance wrapping the Geocoding REST API client.
     * Injected into [MapViewModel] via [AppViewModelProvider] to provide geocoding and reverse geocoding functionality.
     */
    val geocodingRepository by lazy { GeocodingRepository() }


}