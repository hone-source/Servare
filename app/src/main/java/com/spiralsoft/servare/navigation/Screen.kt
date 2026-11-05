package com.spiralsoft.servare.navigation

sealed class Screen(val route: String) {
    data object Inicio : Screen("inicio")
    data object Registro : Screen("registro")
    data object Login : Screen("login")
    data object FichaList : Screen("fichas")
    // Solo se llega desde el menú del build debug,
    // sirve para ejercitar una segunda entidad sincronizable
    data object PruebaList : Screen("pruebas")
    // Solo se llega desde el menú del build debug,
    // sirve para probar el mapa con MapLibre
    data object Mapa : Screen("mapa")
    data object PruebaForm : Screen("prueba-form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "prueba-form?entityId=$entityId" else "prueba-form"
        }
    }
    data object FichaForm : Screen("ficha-form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "ficha-form?entityId=$entityId" else "ficha-form"
        }
    }
}
