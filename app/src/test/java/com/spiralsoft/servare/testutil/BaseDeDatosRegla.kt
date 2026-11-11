package com.spiralsoft.servare.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spiralsoft.servare.data.local.AppDatabase
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Room real y en memoria, sin emulador: las pruebas que la usan corren con Robolectric
// (@RunWith(RobolectricTestRunner::class)). Cada prueba parte de una base vacía
class BaseDeDatosRegla : TestWatcher() {
    lateinit var db: AppDatabase
        private set

    val operaciones get() = db.operationDao()
    val pruebas get() = db.pruebaDao()
    val eliminaciones get() = db.eliminacionDao()
    val syncInicial get() = db.syncInicialDao()

    override fun starting(description: Description) {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    override fun finished(description: Description) = db.close()
}
