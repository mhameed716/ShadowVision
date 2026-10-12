package com.shadow306.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shadow306.app.ui.faceswap.FaceSwapScreen
import com.shadow306.app.ui.home.HomeScreen
import com.shadow306.app.ui.removal.RemovalScreen
import com.shadow306.app.ui.settings.SettingsScreen
import com.shadow306.app.ui.setup.SetupScreen
import com.shadow306.app.ui.templates.TemplatesScreen
import com.shadow306.app.ui.tryon.TryOnScreen
import com.shadow306.app.ui.video.VideoScreen

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val TRYON = "tryon"
    const val FACESWAP = "faceswap"
    const val VIDEO = "video"
    const val TEMPLATES = "templates"
    const val REMOVAL = "removal"
    const val SETTINGS = "settings"
}

@Composable
fun ShadowNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            SetupScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.TRYON) { TryOnScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.FACESWAP) { FaceSwapScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.VIDEO) { VideoScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.TEMPLATES) {
            TemplatesScreen(
                onBack = { navController.popBackStack() },
                onUseTemplate = { _ ->
                    navController.navigate(Routes.TRYON)
                }
            )
        }
        composable(Routes.REMOVAL) { RemovalScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
