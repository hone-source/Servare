package com.spiralsoft.servare.testutil

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

// Ficha con valores en todas las tablas hijas
// el orden de cada lista importa
fun fichaLlena(nombre: String = "Cerro") = Ficha(
    pueblo = PuebloOriginario.WIXARIKA,
    comunidad = "Tuapurie",
    fechaLevantamiento = "2026-10-01",
    tipoEspacio = TipoEspacio.CENTRO_CEREMONIAL,
    nombre = nombre,
    estado = "Jalisco",
    geometria = Geometria.POLIGONO,
    poligonal = listOf(PuntoPoligonal("P1", "13Q 2", "3 m", "2100"), PuntoPoligonal("P2", "13Q 3", "", "")),
    rituales = listOf(
        Ritual(TipoRitual.OFRENDA, "", "Primero", "Anual", "Descripción 1"),
        Ritual(TipoRitual.FIESTA, "", "Segundo", "Mensual", "Descripción 2"),
        Ritual(TipoRitual.OTRO, "Especial", "Tercero", "", ""),
    ),
    elementos = listOf(ElementoEspacio(TipoLugar.NATURAL, "redonda", "ancestro", "uso", "carac", "elem", "1", "2", "3", "4")),
    fotografias = RegistroArchivos("10", "IMG_1", "IMG_10"),
    participantes = listOf(Participante("Comisario", "Ana"), Participante("Secretario", "Luis"))
)
