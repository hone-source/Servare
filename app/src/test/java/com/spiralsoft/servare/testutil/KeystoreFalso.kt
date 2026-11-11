package com.spiralsoft.servare.testutil

import android.security.keystore.KeyGenParameterSpec
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

// Robolectric no trae "AndroidKeyStore". Este proveedor lo imita en memoria: el almacén guarda las claves AES que
// genera KeyGenerator con un KeyGenParameterSpec (por su alias), y el cifrado lo hace el proveedor normal de la JVM.
// Como regla de JUnit: lo registra antes de cada prueba y lo retira después
class KeystoreFalso : TestWatcher() {
    // Las claves "guardadas en el Keystore", por alias
    val claves = ConcurrentHashMap<String, SecretKey>()

    private val proveedor = object : Provider("AndroidKeyStore", 1.0, "Keystore en memoria para pruebas") {
        init {
            putService(servicio("KeyStore", "AndroidKeyStore") { AlmacenFalso() })
            putService(servicio("KeyGenerator", "AES") { GeneradorFalso() })
        }

        private fun servicio(tipo: String, algoritmo: String, crear: () -> Any) =
            object : Service(this, tipo, algoritmo, algoritmo, null, null) {
                override fun newInstance(constructorParameter: Any?): Any = crear()
            }
    }

    override fun starting(description: Description) {
        claves.clear()
        Security.addProvider(proveedor)
    }

    override fun finished(description: Description) {
        Security.removeProvider(proveedor.name)
    }

    private inner class AlmacenFalso : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? = claves[alias]
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
        override fun engineContainsAlias(alias: String) = claves.containsKey(alias)
        override fun engineIsKeyEntry(alias: String) = claves.containsKey(alias)
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(claves.keys.toList())
        override fun engineSize() = claves.size
        override fun engineDeleteEntry(alias: String) {
            claves.remove(alias)
        }

        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCreationDate(alias: String): Date? = null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) = Unit
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = Unit
        override fun engineIsCertificateEntry(alias: String) = false
        override fun engineGetCertificateAlias(cert: Certificate): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    }

    private inner class GeneradorFalso : KeyGeneratorSpi() {
        private var alias: String? = null

        override fun engineInit(random: SecureRandom?) = throw UnsupportedOperationException("Se necesita un KeyGenParameterSpec")
        override fun engineInit(keysize: Int, random: SecureRandom?) = throw UnsupportedOperationException("Se necesita un KeyGenParameterSpec")
        override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) {
            alias = (params as KeyGenParameterSpec).keystoreAlias
        }

        override fun engineGenerateKey(): SecretKey {
            val clave = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
            claves[checkNotNull(alias)] = clave
            return clave
        }
    }
}
