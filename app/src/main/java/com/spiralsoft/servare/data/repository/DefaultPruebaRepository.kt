package com.spiralsoft.servare.data.repository

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import com.spiralsoft.servare.data.local.dao.PruebaDao
import com.spiralsoft.servare.data.local.entity.PruebaEntity
import com.spiralsoft.servare.domain.model.Prueba
import com.spiralsoft.servare.domain.repository.PruebaRepository
import com.spiralsoft.servare.domain.repository.ResultadoGuardadoPrueba

class DefaultPruebaRepository @Inject constructor(
    private val pruebaDao: PruebaDao,
    private val clock: Clock
) : PruebaRepository {

    override fun observarTodas(): Flow<List<Prueba>> = pruebaDao.observarTodas().map { lista ->
        lista.map { Prueba(it.entityId, it.titulo, it.texto, it.numero, it.createdAt) }
    }

    override suspend fun guardar(titulo: String, texto: String, numero: Int): ResultadoGuardadoPrueba {
        if (titulo.isBlank()) return ResultadoGuardadoPrueba.FaltaTitulo

        val id = UUID.randomUUID().toString()
        return try {
            // Sin outbox: el registro de prueba no se sincroniza
            pruebaDao.insert(
                PruebaEntity(
                    entityId = id,
                    titulo = titulo.trim(),
                    texto = texto,
                    numero = numero,
                    createdAt = clock.millis()
                )
            )
            ResultadoGuardadoPrueba.Guardada(id)
        } catch (_: SQLiteFullException) {
            ResultadoGuardadoPrueba.SinEspacio
        } catch (_: SQLiteException) {
            ResultadoGuardadoPrueba.ErrorAlmacenamiento
        }
    }
}
