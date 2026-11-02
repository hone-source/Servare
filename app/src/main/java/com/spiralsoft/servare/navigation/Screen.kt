package com.spiralsoft.servare.navigation

sealed class Screen(val route: String) {
    data object Inicio : Screen("inicio")
    data object Registro : Screen("registro")
    data object Login : Screen("login")
    data object FichaList : Screen("fichas")
    data object FichaForm : Screen("ficha-form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "ficha-form?entityId=$entityId" else "ficha-form"
        }
    }
}
