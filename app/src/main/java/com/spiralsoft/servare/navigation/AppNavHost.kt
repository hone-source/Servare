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
import com.spiralsoft.servare.ui.screen.FormFichaScreen
import com.spiralsoft.servare.ui.screen.InicioScreen
import com.spiralsoft.servare.ui.screen.ListFichaScreen
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

    // Mientras se lee la sesión guardada no se dibuja nada
    // para evita mostrar el inicio un instante a quien ya tiene sesión
    if (sesion is EstadoSesion.Cargando) {
        Box(modifier.fillMaxSize())
        return
    }

    // Sin sesión lo primero que se ve es el inicio, que ofrece crear cuenta o iniciar sesión.
    // Con la sesión expirada se entra a la app igual, sigue funcionando con lo guardado
    val startDestination = remember {
        if (sesion is EstadoSesion.ConSesion || sesion is EstadoSesion.Expirada) Screen.FichaList.route
        else Screen.Inicio.route
    }

    // La sesión cambia con la pantalla ya abierta, al registrarse o iniciar sesión se entra a la app
    // y "atrás" no vuelve a esas pantallas; sin sesión se vuelve al inicio
    LaunchedEffect(sesion) {
        val ruta = navController.currentDestination?.route ?: return@LaunchedEffect
        val enAcceso = ruta == Screen.Inicio.route || ruta == Screen.Registro.route || ruta == Screen.Login.route
        when (sesion) {
            is EstadoSesion.ConSesion -> if (enAcceso) {
                // Si venía de la app (sesión expirada) vuelve a la pantalla en la que estaba,
                // si venía del inicio, entra a la lista de fichas
                val dentroDeLaApp = runCatching { navController.getBackStackEntry(Screen.FichaList.route) }.isSuccess
                if (dentroDeLaApp) {
                    navController.popBackStack()
                } else {
                    navController.navigate(Screen.FichaList.route) {
                        popUpTo(Screen.Inicio.route) { inclusive = true }
                    }
                }
            }
            // La app sigue en la pantalla en la que estaba: el aviso de la lista ofrece iniciar sesión
            is EstadoSesion.Expirada -> Unit
            EstadoSesion.SinSesion -> if (!enAcceso) {
                navController.navigate(Screen.Inicio.route) {
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
        composable(Screen.Inicio.route) {
            InicioScreen(
                onRegistro = { navController.navigate(Screen.Registro.route) },
                onLogin = { navController.navigate(Screen.Login.route) }
            )
        }
        composable(Screen.Registro.route) {
            RegistroScreen(onVolver = { navController.popBackStack() })
        }
        composable(Screen.Login.route) {
            LoginScreen(onVolver = { navController.popBackStack() })
        }

        // Fichas técnicas de levantamiento: es la raíz de la app con sesión
        composable(Screen.FichaList.route) { entry ->
            val mensaje by entry.savedStateHandle.getStateFlow<String?>(MENSAJE, null).collectAsState()
            ListFichaScreen(
                onNueva = { navController.navigate(Screen.FichaForm.createRoute()) },
                onEditar = { entityId -> navController.navigate(Screen.FichaForm.createRoute(entityId)) },
                onIniciarSesion = { navController.navigate(Screen.Login.route) },
                mensaje = mensaje,
                onMensajeMostrado = { entry.savedStateHandle[MENSAJE] = null }
            )
        }
        composable(
            Screen.FichaForm.route,
            arguments = listOf(navArgument("entityId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            FormFichaScreen(
                onVolver = { navController.popBackStack() },
                onGuardada = { mensaje ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(MENSAJE, mensaje)
                    navController.popBackStack()
                }
            )
        }
    }
}
