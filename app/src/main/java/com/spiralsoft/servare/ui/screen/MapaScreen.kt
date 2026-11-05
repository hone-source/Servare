package com.spiralsoft.servare.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

// Estilo público de MapLibre, sin clave. Necesita red: el mapa offline se prueba después
private const val ESTILO = "https://demotiles.maplibre.org/style.json"

// Pantalla de prueba del mapa, solo en el build debug
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(onVolver: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        MapaMapLibre(Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun MapaMapLibre(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = rememberMapView(context)

    AndroidView(factory = { mapView }, modifier = modifier)
}

// El MapView no sigue el ciclo de vida solo: hay que reenviarle los eventos de la pantalla
@Composable
private fun rememberMapView(context: Context): MapView {
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(ESTILO)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(22.76941204234346, -102.57653555131166))
                    .zoom(4.0)
                    .build()
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Al salir de la pantalla la actividad sigue viva, así que el ON_DESTROY no llega
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    mapView.onPause()
                    mapView.onStop()
                }
                mapView.onDestroy()
            }
        }
    }
    return mapView
}
