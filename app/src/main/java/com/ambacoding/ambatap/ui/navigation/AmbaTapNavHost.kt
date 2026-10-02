package com.ambacoding.ambatap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ambacoding.ambatap.ui.editor.EditorScreen
import com.ambacoding.ambatap.ui.home.HomeScreen
import com.ambacoding.ambatap.ui.onboarding.OnboardingScreen
import com.ambacoding.ambatap.ui.playground.PlaygroundScreen
import com.ambacoding.ambatap.ui.settings.SettingsScreen

@Composable
fun AmbaTapNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenOnboarding = { navController.navigate(OnboardingRoute) },
                onOpenMacro = { id -> navController.navigate(EditorRoute(id)) },
                onOpenPlayground = { navController.navigate(PlaygroundRoute) },
            )
        }
        composable<OnboardingRoute> {
            OnboardingScreen(onDone = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<PlaygroundRoute> {
            PlaygroundScreen(onBack = { navController.popBackStack() })
        }
        composable<EditorRoute> {
            EditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
