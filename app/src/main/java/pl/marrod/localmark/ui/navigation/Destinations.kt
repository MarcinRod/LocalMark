package pl.marrod.localmark.ui.navigation

import kotlinx.serialization.Serializable


/**
 * Sealed interface representing the different navigation destinations in the app.
 * Used in [LocalMarkNav].
 *
 * Each destination is a data class or object that implements [Destinations]. This allows for
 * type-safe navigation and passing of arguments between screens.
 *
 * @see Destinations.Login
 * @see Destinations.Map
 */
@Serializable
sealed interface Destinations{

    @Serializable
    data object Login : Destinations
    @Serializable
    data class Map(val displayName: String? = null) : Destinations

}