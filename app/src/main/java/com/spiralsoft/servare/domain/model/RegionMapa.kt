package com.spiralsoft.servare.domain.model

// Región del mapa que se puede guardar para usarla sin red.
// El sistema solo opera en estados de México, así que se descarga por estado
data class RegionMapa(
    val nombre: String,
    val norte: Double,
    val este: Double,
    val sur: Double,
    val oeste: Double,
    val zoomMin: Double,
    val zoomMax: Double
) {
    val centroLat get() = (norte + sur) / 2
    val centroLon get() = (este + oeste) / 2

    fun contiene(latitud: Double, longitud: Double) =
        latitud in sur..norte && longitud in oeste..este

    companion object {
        // Caja aproximada de el país, el mapa no se deja mover fuera de ella
        val MEXICO = RegionMapa("México", norte = 32.8, este = -86.5, sur = 14.4, oeste = -118.5, zoomMin = 4.0, zoomMax = 14.0)

        // Estado pequeño, para probar la descarga sin bajar miles de teselas
        val TLAXCALA = RegionMapa("Tlaxcala", norte = 19.73, este = -97.63, sur = 19.10, oeste = -98.72, zoomMin = 5.0, zoomMax = 13.0)

        // Es unas 20 veces más grande que Tlaxcala: con un zoom menos pesa una cuarta parte de lo que pesaría
        val ZACATECAS = RegionMapa("Zacatecas", norte = 25.13, este = -100.71, sur = 21.03, oeste = -104.36, zoomMin = 5.0, zoomMax = 13.0)

        // Los estados que se pueden descargar. El nombre es la identidad de la región guardada: no cambiarlo
        val ESTADOS = listOf(TLAXCALA, ZACATECAS)
    }
}

sealed interface EstadoRegionMapa {
    data object Comprobando : EstadoRegionMapa
    data object NoDescargada : EstadoRegionMapa
    // Se descargó solo una parte (descarga interrumpida): se puede continuar
    data class Parcial(val completados: Long, val requeridos: Long) : EstadoRegionMapa
    data class Descargando(val completados: Long, val requeridos: Long) : EstadoRegionMapa
    data class Completa(val bytes: Long) : EstadoRegionMapa
}
