package com.spiralsoft.servare.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.spiralsoft.servare.data.local.entity.ElementoEspacioEntity
import com.spiralsoft.servare.data.local.entity.FichaEntity
import com.spiralsoft.servare.data.local.entity.ParticipanteEntity
import com.spiralsoft.servare.data.local.entity.PuntoPoligonalEntity
import com.spiralsoft.servare.data.local.entity.RitualEntity
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.Ritual

// La ficha con las filas de sus tablas
data class FichaCompleta(
    @Embedded val ficha: FichaEntity,
    @Relation(parentColumn = "entityId", entityColumn = "fichaId")
    val poligonal: List<PuntoPoligonalEntity>,
    @Relation(parentColumn = "entityId", entityColumn = "fichaId")
    val rituales: List<RitualEntity>,
    @Relation(parentColumn = "entityId", entityColumn = "fichaId")
    val elementos: List<ElementoEspacioEntity>,
    @Relation(parentColumn = "entityId", entityColumn = "fichaId")
    val participantes: List<ParticipanteEntity>
)

// Las columnas de la lista (nombre, comunidad...) salen de la misma ficha, así nunca difieren de ella
fun Ficha.aCompleta(entityId: String, createdAt: Long, version: Int?) = FichaCompleta(
    ficha = FichaEntity(
        entityId = entityId,
        createdAt = createdAt,
        version = version,
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
        historia = historia,
        informantes = informantes,
        descripcionEspacio = descripcionEspacio,
        observacionesCartografia = observacionesCartografia,
        danos = danos,
        riesgos = riesgos,
        fechaTecnica = fechaTecnica,
        horaInicio = horaInicio,
        horaFin = horaFin,
        fotografias = fotografias,
        grabaciones = grabaciones,
        duracionGrabacion = duracionGrabacion,
        georreferencias = georreferencias,
        integrantesEquipo = integrantesEquipo,
        comentariosFinales = comentariosFinales,
        autoridadLocal = autoridadLocal
    ),
    poligonal = poligonal.mapIndexed { i, p ->
        PuntoPoligonalEntity(entityId, i, p.puntoReferencia, p.utm, p.margenError, p.altura)
    },
    rituales = rituales.mapIndexed { i, r ->
        RitualEntity(entityId, i, r.tipo, r.tipoOtro, r.nombres, r.temporalidad, r.descripcion)
    },
    elementos = elementos.mapIndexed { i, e ->
        ElementoEspacioEntity(
            entityId, i, e.tipo, e.forma, e.ancestro, e.uso, e.caracteristicas, e.elementos,
            e.largo, e.ancho, e.alto, e.profundidad
        )
    },
    participantes = participantes.mapIndexed { i, p -> ParticipanteEntity(entityId, i, p.cargo, p.nombre) }
)

// Las relaciones no vienen ordenadas, la posición en la tabla la da orden
fun FichaCompleta.aFicha() = Ficha(
    pueblo = ficha.pueblo,
    nucleoAgrario = ficha.nucleoAgrario,
    comunidad = ficha.comunidad,
    numeroLevantamiento = ficha.numeroLevantamiento,
    fechaLevantamiento = ficha.fechaLevantamiento,
    tipoEspacio = ficha.tipoEspacio,
    tipoEspacioOtro = ficha.tipoEspacioOtro,
    nombre = ficha.nombre,
    otrosNombres = ficha.otrosNombres,
    comoLlegar = ficha.comoLlegar,
    estado = ficha.estado,
    municipio = ficha.municipio,
    localidad = ficha.localidad,
    tipoPropiedad = ficha.tipoPropiedad,
    lugaresProximos = ficha.lugaresProximos,
    geometria = ficha.geometria,
    puntoGps = ficha.puntoGps,
    altura = ficha.altura,
    area = ficha.area,
    utm = ficha.utm,
    poligonal = poligonal.sortedBy { it.orden }.map {
        PuntoPoligonal(it.puntoReferencia, it.utm, it.margenError, it.altura)
    },
    historia = ficha.historia,
    informantes = ficha.informantes,
    rituales = rituales.sortedBy { it.orden }.map {
        Ritual(it.tipo, it.tipoOtro, it.nombres, it.temporalidad, it.descripcion)
    },
    descripcionEspacio = ficha.descripcionEspacio,
    elementos = elementos.sortedBy { it.orden }.map {
        ElementoEspacio(
            it.tipo, it.forma, it.ancestro, it.uso, it.caracteristicas, it.elementos,
            it.largo, it.ancho, it.alto, it.profundidad
        )
    },
    observacionesCartografia = ficha.observacionesCartografia,
    danos = ficha.danos,
    riesgos = ficha.riesgos,
    fechaTecnica = ficha.fechaTecnica,
    horaInicio = ficha.horaInicio,
    horaFin = ficha.horaFin,
    fotografias = ficha.fotografias,
    grabaciones = ficha.grabaciones,
    duracionGrabacion = ficha.duracionGrabacion,
    georreferencias = ficha.georreferencias,
    integrantesEquipo = ficha.integrantesEquipo,
    comentariosFinales = ficha.comentariosFinales,
    autoridadLocal = ficha.autoridadLocal,
    participantes = participantes.sortedBy { it.orden }.map { Participante(it.cargo, it.nombre) }
)

// Room no ordena las relaciones, así que las listas no se comparan tal cual
// el pull usa esto para no reescribir fichas sin cambios
fun FichaCompleta.mismoContenido(otra: FichaCompleta) = ficha == otra.ficha && aFicha() == otra.aFicha()
