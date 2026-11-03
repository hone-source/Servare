package com.spiralsoft.servare.domain.model

// Campos de la ficha con el nombre que ve el usuario, en el orden del formulario
private val CAMPOS: List<Pair<String, (Ficha) -> String?>> = listOf(
    "Pueblo originario" to { it.pueblo?.etiqueta },
    "Núcleo agrario / Ejido / Localidad" to { it.nucleoAgrario },
    "Comunidad" to { it.comunidad },
    "Número de levantamiento" to { it.numeroLevantamiento },
    "Fecha de levantamiento" to { it.fechaLevantamiento },
    "Tipo de espacio sagrado" to { it.tipoEspacio?.etiqueta },
    "Otro tipo de espacio" to { it.tipoEspacioOtro },
    "Nombre del lugar" to { it.nombre },
    "Otros nombres con que se conoce" to { it.otrosNombres },
    "Cómo se llega al lugar" to { it.comoLlegar },
    "Estado" to { it.estado },
    "Municipio" to { it.municipio },
    "Localidad" to { it.localidad },
    "Tipo de propiedad" to { it.tipoPropiedad },
    "Poblados, sitios o lugares próximos" to { it.lugaresProximos },
    "Punto, línea, polígono" to { it.geometria?.etiqueta },
    "Punto de referencia en GPS" to { it.puntoGps },
    "Altura" to { it.altura },
    "Área del lugar" to { it.area },
    "U.T.M." to { it.utm },
    "Poligonal" to { f ->
        f.poligonal.lineas { listOf(it.puntoReferencia, it.utm, it.margenError, it.altura) }
    },
    "Historia y relatos del espacio" to { it.historia },
    "Informantes y su procedencia" to { it.informantes },
    "Rituales y actos ceremoniales" to { f ->
        f.rituales.lineas { listOf(it.tipo?.etiqueta, it.tipoOtro, it.nombres, it.temporalidad, it.descripcion) }
    },
    "Descripción del lugar" to { it.descripcionEspacio },
    "Elementos del espacio" to { f ->
        f.elementos.lineas {
            val medidas = listOf(it.largo, it.ancho, it.alto, it.profundidad)
            listOf(
                it.tipo?.etiqueta, it.forma, it.ancestro, it.uso, it.caracteristicas, it.elementos,
                if (medidas.any(String::isNotBlank)) "L×AN×AL×P: " + medidas.joinToString("×") { m -> m.ifBlank { "—" } }
                else null
            )
        }
    },
    "Observaciones de la cartografía" to { it.observacionesCartografia },
    "Daños" to { it.danos },
    "Riesgos" to { it.riesgos },
    "Fecha del levantamiento técnico" to { it.fechaTecnica },
    "Hora de inicio" to { it.horaInicio },
    "Hora de fin" to { it.horaFin },
    "Fotografías realizadas" to { f -> with(f.fotografias) { listOf(cantidad, desde, hasta) }.archivos() },
    "Grabaciones realizadas" to { f -> with(f.grabaciones) { listOf(cantidad, desde, hasta) }.archivos() },
    "Duración de la grabación" to { it.duracionGrabacion },
    "Georreferencias realizadas" to { f -> with(f.georreferencias) { listOf(cantidad, desde, hasta) }.archivos() },
    "Integrantes del equipo" to { it.integrantesEquipo },
    "Comentarios u observaciones finales" to { it.comentariosFinales },
    "Autoridad local" to { it.autoridadLocal },
    "Participantes" to { f -> f.participantes.lineas { listOf(it.cargo, it.nombre) } }
)

// Solo los campos que difieren: la ficha tiene decenas y los iguales harían ilegible la comparación
fun compararFichas(local: Ficha?, servidor: Ficha): List<CampoComparado> {
    val base = local ?: Ficha()
    val distintos = CAMPOS.mapNotNull { (nombre, leer) ->
        val propio = leer(base)
        val remoto = leer(servidor)
        if (propio == remoto) null else CampoComparado(nombre, propio, remoto)
    }
    return distintos.ifEmpty {
        // Solo cambió la versión: se dice, en lugar de mostrar una comparación vacía
        listOf(CampoComparado("Contenido de la ficha", "sin diferencias", "sin diferencias"))
    }
}

// Una línea por fila de la tabla, con sus celdas llenas separadas por " · "
private fun <T> List<T>.lineas(celdas: (T) -> List<String?>): String =
    joinToString("\n") { fila -> celdas(fila).filter { !it.isNullOrBlank() }.joinToString(" · ") }

private fun List<String>.archivos(): String {
    val (cantidad, desde, hasta) = this
    return when {
        cantidad.isBlank() && desde.isBlank() && hasta.isBlank() -> ""
        else -> "${cantidad.ifBlank { "—" }}, del ${desde.ifBlank { "—" }} al ${hasta.ifBlank { "—" }}"
    }
}
