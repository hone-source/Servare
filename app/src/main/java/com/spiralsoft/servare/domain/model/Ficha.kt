@file:UseContextualSerialization(LocalDate::class, LocalTime::class)

package com.spiralsoft.servare.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate
import java.time.LocalTime

// Campos de la ficha técnica de levantamiento
@Serializable
data class Ficha(
    // Registro de levantamiento
    val pueblo: PuebloOriginario? = null,
    val nucleoAgrario: String = "",
    val comunidad: String = "",
    val numeroLevantamiento: String = "",
    val fechaLevantamiento: LocalDate? = null,

    // 1. Datos generales
    val tipoEspacio: TipoEspacio? = null,
    val tipoEspacioOtro: String = "",
    val nombre: String = "",
    val otrosNombres: String = "",
    val comoLlegar: String = "",

    // 2. Datos geográficos y de ubicación
    val estado: String = "",
    val municipio: String = "",
    val localidad: String = "",
    val tipoPropiedad: String = "",
    val lugaresProximos: String = "",
    val geometria: Geometria? = null,
    val puntoGps: String = "",
    val altura: String = "",
    val area: String = "",
    val utm: String = "",
    val poligonal: List<PuntoPoligonal> = emptyList(),

    // 3. Uso social
    val historia: String = "",
    val informantes: String = "",
    val rituales: List<Ritual> = emptyList(),

    // 4. Conformación del espacio sagrado
    val descripcionEspacio: String = "",
    val elementos: List<ElementoEspacio> = emptyList(),

    // 5. Cartografía del informante
    val observacionesCartografia: String = "",

    // 6. Estado de conservación
    val danos: String = "",
    val riesgos: String = "",

    // 7. Información técnica del levantamiento
    val fechaTecnica: LocalDate? = null,
    val horaInicio: LocalTime? = null,
    val horaFin: LocalTime? = null,
    val fotografias: RegistroArchivos = RegistroArchivos(),
    val grabaciones: RegistroArchivos = RegistroArchivos(),
    val duracionGrabacion: String = "",
    val georreferencias: RegistroArchivos = RegistroArchivos(),
    val integrantesEquipo: String = "",
    val comentariosFinales: String = "",

    // 8. Aval comunitario / ejidal / autoridad local
    val autoridadLocal: String = "",
    val participantes: List<Participante> = emptyList()
)

// Lo mínimo para identificar una ficha en la lista
data class FichaResumen(
    val entityId: String,
    val nombre: String,
    val tipoEspacio: TipoEspacio?,
    val comunidad: String,
    val fechaLevantamiento: LocalDate?,
    val sincronizacion: EstadoSincronizacion
)

@Serializable
data class PuntoPoligonal(
    val puntoReferencia: String = "",
    val utm: String = "",
    val margenError: String = "",
    val altura: String = ""
)

@Serializable
data class Ritual(
    val tipo: TipoRitual? = null,
    val tipoOtro: String = "",
    val nombres: String = "",
    val temporalidad: String = "",
    val descripcion: String = ""
)

@Serializable
data class ElementoEspacio(
    val tipo: TipoLugar? = null,
    val forma: String = "",
    val ancestro: String = "",
    val uso: String = "",
    val caracteristicas: String = "",
    val elementos: String = "",
    val largo: String = "",
    val ancho: String = "",
    val alto: String = "",
    val profundidad: String = ""
)

// "Realizadas: __ del archivo __ al __"
@Serializable
data class RegistroArchivos(
    val cantidad: String = "",
    val desde: String = "",
    val hasta: String = ""
)

@Serializable
data class Participante(
    val cargo: String = "",
    val nombre: String = ""
)

enum class PuebloOriginario(val etiqueta: String) {
    WIXARIKA("Wixárika"),
    NAAYERI("Náayeri"),
    ODAM_AUDAM("O’dam – Au’dam"),
    MEXIKAN("Mexikan")
}

enum class TipoEspacio(val etiqueta: String) {
    SITIO_SAGRADO("Sitio sagrado"),
    LUGAR_SAGRADO("Lugar sagrado"),
    CENTRO_CEREMONIAL("Centro ceremonial"),
    TUKIPA("Tukipa"),
    RUTA_SAGRADA("Ruta sagrada"),
    PATIO_FAMILIAR("Patio familiar"),
    CASA_DE_REUNION("Casa de reunión"),
    CAPILLA("Capilla"),
    TEYUPANI("Teyupani"),
    OTRO("Otro")
}

enum class Geometria(val etiqueta: String) {
    PUNTO("Punto"),
    LINEA("Línea"),
    POLIGONO("Polígono")
}

enum class TipoRitual(val etiqueta: String) {
    PEREGRINACION("Peregrinación"),
    FIESTA("Fiesta"),
    CEREMONIA("Ceremonia"),
    RITO("Rito"),
    OFRENDA("Ofrenda"),
    RECOLECCION("Recolección"),
    CACERIA("Cacería"),
    CAMPAMENTO("Campamento"),
    OTRO("Otro")
}

enum class TipoLugar(val etiqueta: String) {
    NATURAL("Natural"),
    NATURAL_TRANSFORMADO("Natural transformado")
}
