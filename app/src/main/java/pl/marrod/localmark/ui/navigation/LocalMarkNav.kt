package pl.marrod.localmark.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.marrod.localmark.R
import pl.marrod.localmark.ui.screens.signin.SignInScreen
import pl.marrod.localmark.ui.screens.map.MapScreen


// ── Navigation host ──────────────────────────────────────────────────────────
@Composable
fun LocalMarkNav(
    isDark: Boolean = true, //isSystemInDarkTheme(),
    navController: NavHostController = rememberNavController(),
) {
    val localMarkApplication = LocalContext.current.applicationContext as pl.marrod.localmark.LocalMarkApplication

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Image(
            painter = painterResource(if(isDark) R.drawable.backdrop_dark_sm else R.drawable.backdrop_light_sm),
            contentDescription = "BackDrop",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.15f
        )
        NavHost(
            navController = navController,
            startDestination = Destinations.Login,
            enterTransition = TransitionStyle.HorizontalSlide.enter(),
            exitTransition = TransitionStyle.HorizontalSlide.exit(),
            popEnterTransition = TransitionStyle.HorizontalSlide.popEnter(),
            popExitTransition = TransitionStyle.HorizontalSlide.popExit(),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            composable<Destinations.Login> {
                SignInScreen{
                    username ->
                    navController.navigate(Destinations.Map(username))
                }
            }

            composable<Destinations.Map> {
                val activity = LocalActivity.current
                BackHandler { activity?.finish() }
                MapScreen(
                    onSignOut = {
                        localMarkApplication.appContainer.authRepository.signOut()
                        navController.navigate(Destinations.Login) {
                            popUpTo(Destinations.Login) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}