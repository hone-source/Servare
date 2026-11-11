package com.spiralsoft.servare.data.sesion

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.spiralsoft.servare.testutil.KeystoreFalso
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.Security

// DataStore real en el almacenamiento de la prueba y cifrado AES-GCM real
// solo el Keystore es el de KeystoreFalso
@RunWith(RobolectricTestRunner::class)
class AlmacenSesionUnitTest {
    @get:Rule val keystore = KeystoreFalso()

    // DataStore no suelta el archivo mientras viva el proceso: cada prueba usa su propia carpeta
    @get:Rule val carpeta = TemporaryFolder()

    private val contexto: Context by lazy {
        object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir() = carpeta.root
            override fun getApplicationContext(): Context = this
        }
    }
    private val almacen by lazy { AlmacenSesion(contexto) }
    private val sesion = SesionGuardada("token-secreto-123", "a@b.com", expiraEn = 99L)

    @Test
    fun sin_nada_guardado_no_hay_sesion() = runBlocking {
        assertNull(almacen.leer())
    }

    @Test
    fun lo_guardado_se_lee_igual() = runBlocking {
        assertTrue(almacen.guardar(sesion))

        assertEquals(sesion, almacen.leer())
    }

    @Test
    fun guardar_de_nuevo_reemplaza_la_sesion_anterior() = runBlocking {
        almacen.guardar(sesion)
        val otra = SesionGuardada("otro-token", "x@y.com", 5L)

        almacen.guardar(otra)

        assertEquals(otra, almacen.leer())
    }

    @Test
    fun el_token_no_queda_en_claro_en_el_archivo() = runBlocking {
        almacen.guardar(sesion)

        val bytes = contexto.preferencesDataStoreFile("sesion").readBytes().toString(Charsets.ISO_8859_1)

        assertFalse("el token está legible en el dispositivo", "token-secreto-123" in bytes)
        assertTrue("el correo sí se guarda en claro", "a@b.com" in bytes)
    }

    @Test
    fun cada_guardado_cifra_con_un_iv_distinto() = runBlocking {
        almacen.guardar(sesion)
        val primero = contexto.preferencesDataStoreFile("sesion").readBytes()
        almacen.guardar(sesion)
        val segundo = contexto.preferencesDataStoreFile("sesion").readBytes()

        assertFalse("el mismo token no produce el mismo texto cifrado", primero.contentEquals(segundo))
    }

    @Test
    fun la_clave_del_keystore_se_crea_una_vez_y_se_reutiliza() = runBlocking {
        almacen.guardar(sesion)
        val clave = keystore.claves.values.single()

        almacen.guardar(sesion.copy(token = "otro"))
        almacen.borrar()
        almacen.guardar(sesion)

        assertEquals(1, keystore.claves.size)
        assertEquals("se conserva al borrar la sesión", clave, keystore.claves.values.single())
    }

    @Test
    fun borrar_quita_la_sesion_y_borrar_sin_sesion_tambien_funciona() = runBlocking {
        assertTrue(almacen.borrar())

        almacen.guardar(sesion)
        assertTrue(almacen.borrar())
        assertNull(almacen.leer())
    }

    @Test
    fun si_la_clave_del_keystore_se_invalida_la_sesion_no_se_puede_recuperar() = runBlocking {
        almacen.guardar(sesion)
        keystore.claves.clear() // p. ej. el usuario cambió su bloqueo de pantalla y Android invalidó la clave

        assertNull("no devuelve un token corrupto: pide iniciar sesión de nuevo", almacen.leer())
    }

    @Test
    fun si_el_keystore_no_esta_disponible_no_se_guarda_nada_y_se_dice() = runBlocking {
        Security.removeProvider("AndroidKeyStore")

        assertFalse(almacen.guardar(sesion))
        assertNull(almacen.leer())
    }
}
