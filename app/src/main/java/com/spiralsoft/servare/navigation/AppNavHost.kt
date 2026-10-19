package com.spiralsoft.servare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spiralsoft.servare.ui.screen.FormFichaScreen
import com.spiralsoft.servare.ui.screen.ListFichaScreen

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
            ListFichaScreen(onNuevaFicha = { navController.navigate(Screen.Form.createRoute()) })
        }
        composable(
            Screen.Form.route,
            arguments = listOf(navArgument("entityId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            FormFichaScreen(onVolver = { navController.popBackStack() })
        }
    }
}
