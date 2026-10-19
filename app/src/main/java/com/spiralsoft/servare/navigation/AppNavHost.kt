package com.spiralsoft.servare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spiralsoft.servare.ui.composable.SiteFormScreen
import com.spiralsoft.servare.ui.composable.SiteListScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.List.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.List.route) {
            SiteListScreen(
                onAdd = { navController.navigate(Screen.Form.createRoute()) },
                onEdit = { id -> navController.navigate(Screen.Form.createRoute(id)) }
            )
        }
        composable(
            route = Screen.Form.route,
            arguments = listOf(
                navArgument("entityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            SiteFormScreen(
                entityId = entry.arguments?.getString("entityId"),
                onDone = { navController.popBackStack() }
            )
        }
    }
}
