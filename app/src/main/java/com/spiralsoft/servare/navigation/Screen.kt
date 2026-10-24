package com.spiralsoft.servare.navigation

sealed class Screen(val route: String) {
    data object List : Screen("list")
    data object Form : Screen("form?entityId={entityId}") {
        fun createRoute(entityId: String? = null): String {
            return if (entityId != null) "form?entityId=$entityId" else "form"
        }
    }
    data object PruebaList : Screen("list")
    data object PruebaForm : Screen("form")
}
