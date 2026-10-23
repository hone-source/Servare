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
import com.spiralsoft.servare.ui.screen.FormFichaScreen
import com.spiralsoft.servare.ui.screen.ListFichaScreen

private const val MENSAJE = "mensaje"

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
        composable(Screen.List.route) { entry ->
            // Resultado que deja el formulario al guardar (se muestra una vez)
            val mensaje by entry.savedStateHandle.getStateFlow<String?>(MENSAJE, null).collectAsState()
            ListFichaScreen(
                onNuevaFicha = { navController.navigate(Screen.Form.createRoute()) },
                onEditarFicha = { navController.navigate(Screen.Form.createRoute(it)) },
                mensaje = mensaje,
                onMensajeMostrado = { entry.savedStateHandle[MENSAJE] = null }
            )
        }
        composable(
            Screen.Form.route,
            arguments = listOf(navArgument("entityId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            FormFichaScreen(
                onVolver = { navController.popBackStack() },
                onGuardada = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(MENSAJE, "Ficha guardada en el dispositivo")
                    navController.popBackStack()
                }
            )
        }
    }
}
