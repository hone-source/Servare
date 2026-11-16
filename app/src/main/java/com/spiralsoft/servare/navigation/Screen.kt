package com.spiralsoft.servare.navigation

sealed class Screen(val route: String) {
    // Solo se llega desde el menú del build debug,
    // sirve para ejercitar una segunda entidad sincronizable
    data object PruebaList : Screen("pruebas")
    data object PruebaForm : Screen("prueba-form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "prueba-form?entityId=$entityId" else "prueba-form"
        }
    }
    data object Inicio : Screen("inicio")
    data object Registro : Screen("registro")
    data object Login : Screen("login")
    data object FichaList : Screen("fichas")
    data object Mapa : Screen("mapa")  // Mapa con MapLibre
    data object FichaForm : Screen("ficha-form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "ficha-form?entityId=$entityId" else "ficha-form"
        }
    }
}
