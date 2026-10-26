package com.spiralsoft.servare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spiralsoft.servare.domain.model.EstadoSesion
import com.spiralsoft.servare.ui.screen.FormPruebaScreen
import com.spiralsoft.servare.ui.screen.ListPruebaScreen
import com.spiralsoft.servare.ui.screen.LoginScreen
import com.spiralsoft.servare.ui.screen.RegistroScreen
import com.spiralsoft.servare.ui.viewModel.SesionViewModel

private const val MENSAJE = "mensaje"

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sesionViewModel: SesionViewModel = hiltViewModel()
) {
    val sesion by sesionViewModel.estado.collectAsState()

    // Mientras se lee la sesión guardada no se dibuja nada, evita mostrar el registro un instante a quien ya lo hizo
    if (sesion is EstadoSesion.Cargando) {
        Box(modifier.fillMaxSize())
        return
    }

    // Sin sesión lo primero que se ve es el registro, que es obligatorio para entrar.
    // Con la sesión expirada se entra a la app igual: sigue funcionando con lo guardado
    val startDestination = remember {
        if (sesion is EstadoSesion.ConSesion || sesion is EstadoSesion.Expirada) Screen.PruebaList.route
        else Screen.Registro.route
    }

    // La sesión cambia con la pantalla ya abierta: al registrarse o iniciar sesión se entra a la app
    // y "atrás" no vuelve a esas pantallas; sin sesión se vuelve al registro
    LaunchedEffect(sesion) {
        val ruta = navController.currentDestination?.route ?: return@LaunchedEffect
        val enAcceso = ruta == Screen.Registro.route || ruta == Screen.Login.route
        when (sesion) {
            is EstadoSesion.ConSesion -> if (enAcceso) {
                // Si venía de la lista (sesión expirada) vuelve a ella; si venía del registro, entra a la lista
                if (!navController.popBackStack(Screen.PruebaList.route, inclusive = false)) {
                    navController.navigate(Screen.PruebaList.route) {
                        popUpTo(Screen.Registro.route) { inclusive = true }
                    }
                }
            }
            // La app sigue en la pantalla en la que estaba: el aviso de la lista ofrece iniciar sesión
            is EstadoSesion.Expirada -> Unit
            EstadoSesion.SinSesion -> if (!enAcceso) {
                navController.navigate(Screen.Registro.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
            EstadoSesion.Cargando -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Registro.route) {
            RegistroScreen(onIniciarSesion = { navController.navigate(Screen.Login.route) })
        }
        composable(Screen.Login.route) {
            LoginScreen(onVolver = { navController.popBackStack() })
        }

        // Pantallas de prueba: formulario simple
        composable(Screen.PruebaList.route) { entry ->
            val mensaje by entry.savedStateHandle.getStateFlow<String?>(MENSAJE, null).collectAsState()
            ListPruebaScreen(
                onVolver = { navController.popBackStack() },
                onNuevo = { navController.navigate(Screen.PruebaForm.createRoute()) },
                onEditar = { entityId -> navController.navigate(Screen.PruebaForm.createRoute(entityId)) },
                onIniciarSesion = { navController.navigate(Screen.Login.route) },
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
