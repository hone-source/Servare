package com.spiralsoft.servare.data.sesion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class SesionGuardada(val token: String, val correo: String, val expiraEn: Long)

// Sesión en el dispositivo: DataStore con el token cifrado (AES-GCM) con una clave que no sale del Keystore.
// Archivo datastore/sesion.preferences_pb, excluido de las copias de seguridad
@Singleton
class AlmacenSesion @Inject constructor(
    @ApplicationContext context: Context
) {
    // Un solo DataStore por archivo: este objeto es singleton
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(ARCHIVO) }

    // null si no hay sesión o no se puede recuperar (archivo ilegible o clave del Keystore invalidada)
    suspend fun leer(): SesionGuardada? = withContext(Dispatchers.IO) {
        try {
            val preferencias = dataStore.data.first()
            val token = preferencias[TOKEN]?.let(::descifrar) ?: return@withContext null
            val correo = preferencias[CORREO] ?: return@withContext null
            val expiraEn = preferencias[EXPIRA_EN] ?: return@withContext null
            SesionGuardada(token, correo, expiraEn)
        } catch (_: IOException) {
            null
        }
    }

    // false si no se pudo cifrar o escribir: la sesión no queda guardada
    suspend fun guardar(sesion: SesionGuardada): Boolean = withContext(Dispatchers.IO) {
        try {
            val cifrado = cifrar(sesion.token)
            dataStore.edit {
                it[TOKEN] = cifrado
                it[CORREO] = sesion.correo
                it[EXPIRA_EN] = sesion.expiraEn
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: ProviderException) {
            // Algunos dispositivos fallan así al usar el Keystore
            false
        }
    }

    // false si no se pudo borrar: la sesión sigue guardada. La clave del Keystore se conserva para la siguiente
    suspend fun borrar(): Boolean = withContext(Dispatchers.IO) {
        try {
            dataStore.edit { it.clear() }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (almacen.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    // Base64 de IV + texto cifrado
    private fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val cifrado = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrador.iv + cifrado, Base64.NO_WRAP)
    }

    private fun descifrar(guardado: String): String? = try {
        val bytes = Base64.decode(guardado, Base64.NO_WRAP)
        val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(BITS_ETIQUETA, bytes, 0, BYTES_IV))
        }
        String(cifrador.doFinal(bytes, BYTES_IV, bytes.size - BYTES_IV), Charsets.UTF_8)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: ProviderException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val ARCHIVO = "sesion"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "servare_sesion"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BYTES_IV = 12
        const val BITS_ETIQUETA = 128

        val TOKEN = stringPreferencesKey("token_cifrado")
        val CORREO = stringPreferencesKey("correo")
        val EXPIRA_EN = longPreferencesKey("expira_en")
    }
}
