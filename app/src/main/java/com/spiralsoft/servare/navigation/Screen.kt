package com.spiralsoft.servare.navigation

sealed class Screen(val route: String) {
    data object Inicio : Screen("inicio")
    data object Registro : Screen("registro")
    data object Login : Screen("login")
    data object PruebaList : Screen("list")
    data object PruebaForm : Screen("form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "form?entityId=$entityId" else "form"
        }
    }
}
