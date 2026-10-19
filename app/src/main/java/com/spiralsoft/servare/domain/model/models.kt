package com.spiralsoft.servare.domain.model

data class SacredSiteForm(
    val entityId: String? = null,       // null = registro nuevo
    val name: String,
    val description: String,
    val type: String,
    val latitude: Double,
    val longitude: Double
)

data class SacredSite(
    val entityId: String,
    val name: String,
    val description: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val pendingSync: Boolean
)

sealed interface SaveResult {
    data class Saved(val entityId: String) : SaveResult
    data class Invalid(val missing: List<String>) : SaveResult
}

enum class Resolution { KEEP_LOCAL, ACCEPT_SERVER, MERGE }
