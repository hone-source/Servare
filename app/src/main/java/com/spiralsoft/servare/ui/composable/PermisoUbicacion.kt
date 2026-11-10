package com.spiralsoft.servare.ui.composable

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class EstadoPermisoUbicacion {
    CONCEDIDO,
    // Todavía no se ha pedido
    SIN_PEDIR,
    // Se negó, pero el sistema todavía deja volver a pedirlo
    DENEGADO,
    // Se negó y el sistema ya no muestra el diálogo: solo se cambia en los ajustes de la app
    BLOQUEADO
}

@Stable
class PermisoUbicacion internal constructor(
    private val _estado: State<EstadoPermisoUbicacion>,
    val solicitar: () -> Unit,
    val abrirAjustes: () -> Unit
) {
    val estado get() = _estado.value
    val concedido get() = estado == EstadoPermisoUbicacion.CONCEDIDO
}

private val PERMISOS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

// Basta con la ubicación aproximada: desde Android 12 la persona puede concederla en vez de la precisa
private fun Context.ubicacionConcedida() = PERMISOS.any {
    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

// Estado del permiso de ubicación y las acciones para pedirlo. Se vuelve a comprobar al regresar a la
// pantalla, porque se puede cambiar desde los ajustes de la app
@Composable
fun rememberPermisoUbicacion(): PermisoUbicacion {
    val context = LocalContext.current
    val estado = remember {
        mutableStateOf(if (context.ubicacionConcedida()) EstadoPermisoUbicacion.CONCEDIDO else EstadoPermisoUbicacion.SIN_PEDIR)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        estado.value = when {
            resultado.values.any { it } -> EstadoPermisoUbicacion.CONCEDIDO
            context.actividad()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, PERMISOS[0]) } == true ->
                EstadoPermisoUbicacion.DENEGADO
            else -> EstadoPermisoUbicacion.BLOQUEADO
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, evento ->
            if (evento != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            when {
                context.ubicacionConcedida() -> estado.value = EstadoPermisoUbicacion.CONCEDIDO
                // Se revocó desde los ajustes
                estado.value == EstadoPermisoUbicacion.CONCEDIDO -> estado.value = EstadoPermisoUbicacion.SIN_PEDIR
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return remember(launcher) {
        PermisoUbicacion(
            estado,
            solicitar = { launcher.launch(PERMISOS) },
            abrirAjustes = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
        )
    }
}
