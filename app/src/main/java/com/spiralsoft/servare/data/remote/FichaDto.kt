package com.spiralsoft.servare.data.remote

import kotlinx.serialization.Serializable
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.Geometria
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuebloOriginario
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.RegistroArchivos
import com.spiralsoft.servare.domain.model.Ritual
import com.spiralsoft.servare.domain.model.TipoEspacio
import com.spiralsoft.servare.domain.model.TipoLugar
import com.spiralsoft.servare.domain.model.TipoRitual

// Contenido de la ficha tal como se envía, se lee de la tabla en el momento del envío.
// Los campos que valen su valor por defecto no viajan: el servidor los completa
@Serializable
data class FichaRequest(
    val datos: FichaDatosDto,
    val createdAt: Long,
    val baseVersion: Int? = null            // vacía en CREAR
)

// Ficha tal como la devuelve el pull y el campo "actual" del 409
@Serializable
data class FichaRemota(
    val entityId: String,
    val datos: FichaDatosDto,
    val createdAt: Long,
    val version: Int? = null
)

// Espejo de Ficha para el cable: mismos nombres y valores por defecto, así el JSON es el que espera el servidor.
// El modelo de dominio no depende de kotlinx.serialization
@Serializable
data class FichaDatosDto(
    val pueblo: PuebloOriginario? = null,
    val nucleoAgrario: String = "",
    val comunidad: String = "",
    val numeroLevantamiento: String = "",
    val fechaLevantamiento: String? = null,
    val tipoEspacio: TipoEspacio? = null,
    val tipoEspacioOtro: String = "",
    val nombre: String = "",
    val otrosNombres: String = "",
    val comoLlegar: String = "",
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
    val poligonal: List<PuntoPoligonalDto> = emptyList(),
    val historia: String = "",
    val informantes: String = "",
    val rituales: List<RitualDto> = emptyList(),
    val descripcionEspacio: String = "",
    val elementos: List<ElementoEspacioDto> = emptyList(),
    val observacionesCartografia: String = "",
    val danos: String = "",
    val riesgos: String = "",
    val fechaTecnica: String? = null,
    val horaInicio: String? = null,
    val horaFin: String? = null,
    val fotografias: RegistroArchivosDto = RegistroArchivosDto(),
    val grabaciones: RegistroArchivosDto = RegistroArchivosDto(),
    val duracionGrabacion: String = "",
    val georreferencias: RegistroArchivosDto = RegistroArchivosDto(),
    val integrantesEquipo: String = "",
    val comentariosFinales: String = "",
    val autoridadLocal: String = "",
    val participantes: List<ParticipanteDto> = emptyList()
)

@Serializable
data class PuntoPoligonalDto(
    val puntoReferencia: String = "",
    val utm: String = "",
    val margenError: String = "",
    val altura: String = ""
)

@Serializable
data class RitualDto(
    val tipo: TipoRitual? = null,
    val tipoOtro: String = "",
    val nombres: String = "",
    val temporalidad: String = "",
    val descripcion: String = ""
)

@Serializable
data class ElementoEspacioDto(
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

@Serializable
data class RegistroArchivosDto(
    val cantidad: String = "",
    val desde: String = "",
    val hasta: String = ""
)

@Serializable
data class ParticipanteDto(
    val cargo: String = "",
    val nombre: String = ""
)

fun Ficha.aDto() = FichaDatosDto(
    pueblo = pueblo,
    nucleoAgrario = nucleoAgrario,
    comunidad = comunidad,
    numeroLevantamiento = numeroLevantamiento,
    fechaLevantamiento = fechaLevantamiento,
    tipoEspacio = tipoEspacio,
    tipoEspacioOtro = tipoEspacioOtro,
    nombre = nombre,
    otrosNombres = otrosNombres,
    comoLlegar = comoLlegar,
    estado = estado,
    municipio = municipio,
    localidad = localidad,
    tipoPropiedad = tipoPropiedad,
    lugaresProximos = lugaresProximos,
    geometria = geometria,
    puntoGps = puntoGps,
    altura = altura,
    area = area,
    utm = utm,
    poligonal = poligonal.map { PuntoPoligonalDto(it.puntoReferencia, it.utm, it.margenError, it.altura) },
    historia = historia,
    informantes = informantes,
    rituales = rituales.map { RitualDto(it.tipo, it.tipoOtro, it.nombres, it.temporalidad, it.descripcion) },
    descripcionEspacio = descripcionEspacio,
    elementos = elementos.map {
        ElementoEspacioDto(
            it.tipo, it.forma, it.ancestro, it.uso, it.caracteristicas, it.elementos,
            it.largo, it.ancho, it.alto, it.profundidad
        )
    },
    observacionesCartografia = observacionesCartografia,
    danos = danos,
    riesgos = riesgos,
    fechaTecnica = fechaTecnica,
    horaInicio = horaInicio,
    horaFin = horaFin,
    fotografias = fotografias.aDto(),
    grabaciones = grabaciones.aDto(),
    duracionGrabacion = duracionGrabacion,
    georreferencias = georreferencias.aDto(),
    integrantesEquipo = integrantesEquipo,
    comentariosFinales = comentariosFinales,
    autoridadLocal = autoridadLocal,
    participantes = participantes.map { ParticipanteDto(it.cargo, it.nombre) }
)

fun FichaDatosDto.aFicha() = Ficha(
    pueblo = pueblo,
    nucleoAgrario = nucleoAgrario,
    comunidad = comunidad,
    numeroLevantamiento = numeroLevantamiento,
    fechaLevantamiento = fechaLevantamiento,
    tipoEspacio = tipoEspacio,
    tipoEspacioOtro = tipoEspacioOtro,
    nombre = nombre,
    otrosNombres = otrosNombres,
    comoLlegar = comoLlegar,
    estado = estado,
    municipio = municipio,
    localidad = localidad,
    tipoPropiedad = tipoPropiedad,
    lugaresProximos = lugaresProximos,
    geometria = geometria,
    puntoGps = puntoGps,
    altura = altura,
    area = area,
    utm = utm,
    poligonal = poligonal.map { PuntoPoligonal(it.puntoReferencia, it.utm, it.margenError, it.altura) },
    historia = historia,
    informantes = informantes,
    rituales = rituales.map { Ritual(it.tipo, it.tipoOtro, it.nombres, it.temporalidad, it.descripcion) },
    descripcionEspacio = descripcionEspacio,
    elementos = elementos.map {
        ElementoEspacio(
            it.tipo, it.forma, it.ancestro, it.uso, it.caracteristicas, it.elementos,
            it.largo, it.ancho, it.alto, it.profundidad
        )
    },
    observacionesCartografia = observacionesCartografia,
    danos = danos,
    riesgos = riesgos,
    fechaTecnica = fechaTecnica,
    horaInicio = horaInicio,
    horaFin = horaFin,
    fotografias = fotografias.aDominio(),
    grabaciones = grabaciones.aDominio(),
    duracionGrabacion = duracionGrabacion,
    georreferencias = georreferencias.aDominio(),
    integrantesEquipo = integrantesEquipo,
    comentariosFinales = comentariosFinales,
    autoridadLocal = autoridadLocal,
    participantes = participantes.map { Participante(it.cargo, it.nombre) }
)

private fun RegistroArchivos.aDto() = RegistroArchivosDto(cantidad, desde, hasta)

private fun RegistroArchivosDto.aDominio() = RegistroArchivos(cantidad, desde, hasta)
