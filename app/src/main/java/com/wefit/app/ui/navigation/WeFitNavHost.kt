package com.wefit.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wefit.app.ui.achievements.AchievementsScreen
import com.wefit.app.ui.activity.AddActivityScreen
import com.wefit.app.ui.goals.GoalsScreen
import com.wefit.app.ui.home.HomeScreen
import com.wefit.app.ui.insights.InsightsScreen
import com.wefit.app.ui.more.MoreScreen
import com.wefit.app.ui.onboarding.OnboardingScreen
import com.wefit.app.ui.reminders.RemindersScreen
import com.wefit.app.ui.settings.AboutScreen
import com.wefit.app.ui.settings.PrivacyScreen
import com.wefit.app.ui.settings.SettingsScreen
import com.wefit.app.ui.setup.ProfileSetupScreen
import com.wefit.app.ui.splash.SplashScreen
import com.wefit.app.ui.steps.StepTrackerScreen
import com.wefit.app.ui.weight.AddWeightScreen
import com.wefit.app.ui.weight.WeightScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TAB_HOME, "Home", Icons.Filled.Home),
    Tab(Routes.TAB_ACTIVITY, "Activity", Icons.AutoMirrored.Filled.DirectionsWalk),
    Tab(Routes.TAB_WEIGHT, "Weight", Icons.Filled.MonitorWeight),
    Tab(Routes.TAB_GOALS, "Goals", Icons.Filled.TrackChanges),
    Tab(Routes.TAB_MORE, "More", Icons.Filled.GridView),
)

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.TAB_HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.resetTo(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}

@Composable
fun WeFitNavHost(
    startRoute: String?,
    pendingDestination: String?,
    onDestinationHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBottomBar = current in Routes.topLevel

    LaunchedEffect(pendingDestination, current) {
        if (pendingDestination != null && current in Routes.topLevel) {
            navController.navigate(pendingDestination) { launchSingleTop = true }
            onDestinationHandled()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { navController.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            val back: () -> Unit = { navController.popBackStack() }
            val go: (String) -> Unit = { route ->
                if (route in Routes.topLevel) navController.navigateTab(route) else navController.navigate(route)
            }

            composable(Routes.SPLASH) {
                SplashScreen(startRoute = startRoute, onFinished = { navController.resetTo(it) })
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = { navController.resetTo(Routes.SETUP) })
            }
            composable(Routes.SETUP) {
                ProfileSetupScreen(isEditing = false, onDone = { navController.resetTo(Routes.TAB_HOME) }, onBack = null)
            }
            composable(Routes.TAB_HOME) { HomeScreen(onNavigate = go) }
            composable(Routes.TAB_ACTIVITY) { StepTrackerScreen(onNavigate = go) }
            composable(Routes.TAB_WEIGHT) { WeightScreen(onNavigate = go) }
            composable(Routes.TAB_GOALS) { GoalsScreen(onNavigate = go) }
            composable(Routes.TAB_MORE) { MoreScreen(onNavigate = go) }
            composable(
                Routes.ADD_WEIGHT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { AddWeightScreen(onBack = back) }
            composable(
                Routes.ADD_ACTIVITY,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                ),
            ) { AddActivityScreen(onBack = back) }
            composable(Routes.INSIGHTS) { InsightsScreen(onBack = back) }
            composable(Routes.REMINDERS) { RemindersScreen(onBack = back) }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onBack = back) }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = back, onNavigate = go, onResetComplete = { navController.resetTo(Routes.ONBOARDING) })
            }
            composable(Routes.EDIT_PROFILE) { ProfileSetupScreen(isEditing = true, onDone = back, onBack = back) }
            composable(Routes.ABOUT) { AboutScreen(onBack = back) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = back) }
        }
    }
}
