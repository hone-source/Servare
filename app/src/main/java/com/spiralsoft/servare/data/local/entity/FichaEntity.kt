package com.spiralsoft.servare.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spiralsoft.servare.domain.model.Geometria
import com.spiralsoft.servare.domain.model.PuebloOriginario
import com.spiralsoft.servare.domain.model.RegistroArchivos
import com.spiralsoft.servare.domain.model.TipoEspacio

// Ficha técnica de levantamiento: los campos simples.
// Las tablas de la ficha (poligonal, rituales, elementos y participantes)
// están en sus propias entidades, ligadas por fichaId
@Entity(tableName = "fichas")
data class FichaEntity(
    @PrimaryKey val entityId: String,       // UUID generado en el cliente
    val createdAt: Long,                    // epoch ms
    val version: Int? = null,               // última versión conocida del servidor; null hasta el primer push

    // Registro de levantamiento
    val pueblo: PuebloOriginario? = null,
    val nucleoAgrario: String = "",
    val comunidad: String = "",
    val numeroLevantamiento: String = "",
    val fechaLevantamiento: String? = null,

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

    // 3. Uso social
    val historia: String = "",
    val informantes: String = "",

    // 4. Conformación del espacio sagrado
    val descripcionEspacio: String = "",

    // 5. Cartografía del informante
    val observacionesCartografia: String = "",

    // 6. Estado de conservación
    val danos: String = "",
    val riesgos: String = "",

    // 7. Información técnica del levantamiento
    val fechaTecnica: String? = null,
    val horaInicio: String? = null,
    val horaFin: String? = null,
    @Embedded(prefix = "fotografias_") val fotografias: RegistroArchivos = RegistroArchivos(),
    @Embedded(prefix = "grabaciones_") val grabaciones: RegistroArchivos = RegistroArchivos(),
    val duracionGrabacion: String = "",
    @Embedded(prefix = "georreferencias_") val georreferencias: RegistroArchivos = RegistroArchivos(),
    val integrantesEquipo: String = "",
    val comentariosFinales: String = "",

    // 8. Aval comunitario / ejidal / autoridad local
    val autoridadLocal: String = ""
)
