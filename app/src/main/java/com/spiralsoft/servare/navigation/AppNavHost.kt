package com.spiralsoft.servare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spiralsoft.servare.ui.screen.FormPruebaScreen
import com.spiralsoft.servare.ui.screen.ListPruebaScreen

private const val MENSAJE = "mensaje"

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.PruebaList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Pantallas de prueba: formulario simple
        composable(Screen.PruebaList.route) { entry ->
            val mensaje by entry.savedStateHandle.getStateFlow<String?>(MENSAJE, null).collectAsState()
            ListPruebaScreen(
                onVolver = { navController.popBackStack() },
                onNuevo = { navController.navigate(Screen.PruebaForm.createRoute()) },
                onEditar = { entityId -> navController.navigate(Screen.PruebaForm.createRoute(entityId)) },
                mensaje = mensaje,
                onMensajeMostrado = { entry.savedStateHandle[MENSAJE] = null }
            )
        }
        composable(
            Screen.PruebaForm.route,
            arguments = listOf(navArgument("entityId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            FormPruebaScreen(
                onVolver = { navController.popBackStack() },
                onSalir = { mensaje ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(MENSAJE, mensaje)
                    navController.popBackStack()
                }
            )
        }
    }
}
