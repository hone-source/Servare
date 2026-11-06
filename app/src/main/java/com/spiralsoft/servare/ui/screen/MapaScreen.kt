package com.spiralsoft.servare.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlinx.coroutines.launch
import com.spiralsoft.servare.data.mapa.GestorMapaOffline
import com.spiralsoft.servare.domain.model.EstadoRegionMapa
import com.spiralsoft.servare.domain.model.Punto
import com.spiralsoft.servare.domain.model.RegionMapa
import com.spiralsoft.servare.ui.composable.EstadoPermisoUbicacion
import com.spiralsoft.servare.ui.composable.rememberPermisoUbicacion
import com.spiralsoft.servare.ui.viewModel.MapaViewModel

// Pantalla de prueba del mapa, solo en el build debug.
// El mapa ocupa toda la pantalla: la descarga está en el menú de la barra y el centrado en un botón flotante
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(
    onVolver: () -> Unit,
    viewModel: MapaViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permiso = rememberPermisoUbicacion()
    var mapa by remember { mutableStateOf<MapaListo?>(null) }

    // La primera vez que se abre el mapa se pide el permiso, después solo se pide con el botón
    var pedido by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!pedido && permiso.estado == EstadoPermisoUbicacion.SIN_PEDIR) permiso.solicitar()
        pedido = true
    }

    LaunchedEffect(mapa, permiso.concedido) {
        mapa?.let { actualizarUbicacion(context, it, permiso.concedido) }
    }

    // Sin el permiso no hay posición: el aviso explica por qué y ofrece la acción que corresponde
    LaunchedEffect(permiso.estado) {
        when (permiso.estado) {
            EstadoPermisoUbicacion.DENEGADO -> {
                val resultado = snackbar.showSnackbar(
                    "Sin el permiso de ubicación no se puede mostrar tu posición.",
                    actionLabel = "Permitir", duration = SnackbarDuration.Long
                )
                if (resultado == SnackbarResult.ActionPerformed) permiso.solicitar()
            }
            EstadoPermisoUbicacion.BLOQUEADO -> {
                val resultado = snackbar.showSnackbar(
                    "El permiso de ubicación está bloqueado. Actívalo en los ajustes de la app.",
                    actionLabel = "Ajustes", duration = SnackbarDuration.Long
                )
                if (resultado == SnackbarResult.ActionPerformed) permiso.abrirAjustes()
            }
            else -> Unit
        }
    }

    // Mantener pulsado un punto del mapa abre el diálogo para nombrarlo y guardarlo
    DisposableEffect(mapa) {
        val map = mapa?.map
        val oyente = MapLibreMap.OnMapLongClickListener { lugar ->
            viewModel.proponerPunto(lugar.latitude, lugar.longitude)
            true
        }
        map?.addOnMapLongClickListener(oyente)
        onDispose { map?.removeOnMapLongClickListener(oyente) }
    }

    // Los puntos guardados se dibujan en una capa propia sobre el estilo
    LaunchedEffect(mapa) {
        mapa?.let { prepararPuntos(it.estilo) }
    }
    LaunchedEffect(mapa, state.puntos) {
        mapa?.let { mostrarPuntos(it.estilo, state.puntos) }
    }

    state.puntoNuevo?.let { punto ->
        DialogoNuevoPunto(
            latitud = punto.latitud,
            longitud = punto.longitud,
            onGuardar = viewModel::guardarPunto,
            onCancelar = viewModel::cancelarPunto
        )
    }

    LaunchedEffect(state.mensaje) {
        state.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    val centrar = {
        val posicion = mapa?.map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
        if (posicion != null) {
            mapa?.map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(posicion.latitude, posicion.longitude), 14.0)
            )
        } else {
            scope.launch {
                snackbar.showSnackbar(
                    "Aún no se obtiene tu posición. Comprueba que la ubicación del dispositivo esté activada."
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    MenuDescarga(viewModel.region, state.region, viewModel::descargar, viewModel::eliminar)
                }
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = {
                    when (permiso.estado) {
                        EstadoPermisoUbicacion.CONCEDIDO -> centrar()
                        EstadoPermisoUbicacion.BLOQUEADO -> permiso.abrirAjustes()
                        else -> permiso.solicitar()
                    }
                }
            ) {
                Icon(
                    if (permiso.concedido) Icons.Default.MyLocation else Icons.Default.LocationDisabled,
                    contentDescription = "Centrar en mi posición"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MapaMapLibre(viewModel.region, { mapa = it }, Modifier.fillMaxSize())
            (state.region as? EstadoRegionMapa.Descargando)?.let { descarga ->
                if (descarga.requeridos > 0) {
                    LinearProgressIndicator(
                        progress = { descarga.completados.toFloat() / descarga.requeridos },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

// Pide el nombre del lugar mantenido pulsado. Sin nombre no se puede guardar
@Composable
private fun DialogoNuevoPunto(
    latitud: Double,
    longitud: Double,
    onGuardar: (String) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Guardar punto") },
        text = {
            Column {
                Text("%.5f, %.5f".format(latitud, longitud), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(nombre) }, enabled = nombre.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

// Descarga del mapa sin conexión: el estado va como encabezado y las acciones dependen de él
@Composable
private fun MenuDescarga(
    region: RegionMapa,
    estado: EstadoRegionMapa,
    onDescargar: () -> Unit,
    onEliminar: () -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(
                if (estado is EstadoRegionMapa.Completa) Icons.Outlined.CloudDone else Icons.Outlined.CloudDownload,
                contentDescription = "Mapa sin conexión"
            )
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(region.nombre, style = MaterialTheme.typography.labelMedium)
                        Text(descripcion(estado), style = MaterialTheme.typography.bodySmall)
                    }
                },
                onClick = {},
                enabled = false
            )
            val cerrarY = { accion: () -> Unit -> { abierto = false; accion() } }
            when (estado) {
                EstadoRegionMapa.NoDescargada -> ItemMenu("Descargar", cerrarY(onDescargar))
                is EstadoRegionMapa.Parcial -> {
                    ItemMenu("Continuar descarga", cerrarY(onDescargar))
                    ItemMenu("Quitar", cerrarY(onEliminar))
                }
                is EstadoRegionMapa.Completa -> ItemMenu("Quitar", cerrarY(onEliminar))
                is EstadoRegionMapa.Descargando -> ItemMenu("Cancelar descarga", cerrarY(onEliminar))
                EstadoRegionMapa.Comprobando -> Unit
            }
        }
    }
}

@Composable
private fun ItemMenu(texto: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(texto) }, onClick = onClick)
}

private fun descripcion(estado: EstadoRegionMapa): String = when (estado) {
    EstadoRegionMapa.Comprobando -> "Comprobando lo guardado…"
    EstadoRegionMapa.NoDescargada -> "No está guardado en este dispositivo."
    is EstadoRegionMapa.Parcial -> "Descarga incompleta (${estado.completados} de ${estado.requeridos})."
    is EstadoRegionMapa.Descargando ->
        if (estado.requeridos > 0) "Descargando… ${estado.completados} de ${estado.requeridos}" else "Preparando la descarga…"
    is EstadoRegionMapa.Completa -> "Guardado en este dispositivo (${estado.bytes / 1_048_576} MB)."
}

@Composable
private fun MapaMapLibre(region: RegionMapa, onListo: (MapaListo) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = rememberMapView(context, region, onListo)

    AndroidView(factory = { mapView }, modifier = modifier)
}

// El MapView no sigue el ciclo de vida solo: hay que reenviarle los eventos de la pantalla
@Composable
private fun rememberMapView(context: Context, region: RegionMapa, onListo: (MapaListo) -> Unit): MapView {
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(GestorMapaOffline.ESTILO) { estilo -> onListo(MapaListo(map, estilo)) }
                // El sistema solo opera en México: el mapa no se aleja ni se mueve fuera del país
                val mexico = RegionMapa.MEXICO
                map.setLatLngBoundsForCameraTarget(LatLngBounds.from(mexico.norte, mexico.este, mexico.sur, mexico.oeste))
                map.setMinZoomPreference(mexico.zoomMin)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(region.centroLat, region.centroLon))
                    .zoom(9.0)
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

// El mapa con el estilo ya cargado: la capa de ubicación necesita las dos cosas
private class MapaListo(val map: MapLibreMap, val estilo: Style)

// La posición del dispositivo se dibuja con la capa de ubicación de MapLibre.
// Se activa al conceder el permiso y se apaga si se revoca, el motor por defecto usa el GPS del sistema.
// MapView.onStart/onStop (reenviados en rememberMapView) arrancan y detienen las actualizaciones,
// así que solo hay posiciones con el mapa visible.
// Con solo la ubicación aproximada el sistema limita las actualizaciones a una cada 10 minutos
@SuppressLint("MissingPermission")
private fun actualizarUbicacion(context: Context, mapa: MapaListo, permitido: Boolean) {
    val capa = mapa.map.locationComponent
    if (!permitido) {
        if (capa.isLocationComponentActivated) capa.isLocationComponentEnabled = false
        return
    }
    if (!capa.isLocationComponentActivated) {
        val peticion = LocationEngineRequest.Builder(INTERVALO_UBICACION_MS)
            .setFastestInterval(INTERVALO_MINIMO_UBICACION_MS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        capa.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, mapa.estilo)
                .useDefaultLocationEngine(true)
                .locationEngineRequest(peticion)
                .build()
        )
    }
    capa.isLocationComponentEnabled = true
    capa.cameraMode = CameraMode.NONE
    capa.renderMode = RenderMode.COMPASS
}

private const val INTERVALO_UBICACION_MS = 5_000L
private const val INTERVALO_MINIMO_UBICACION_MS = 1000L

private const val FUENTE_PUNTOS = "puntos"
private const val CAPA_PUNTOS = "puntos-circulo"
private const val CAPA_NOMBRES = "puntos-nombre"
private const val PROPIEDAD_NOMBRE = "nombre"

// Fuente y capas de los puntos guardados, una sola vez por estilo
private fun prepararPuntos(estilo: Style) {
    if (estilo.getSource(FUENTE_PUNTOS) != null) return
    estilo.addSource(GeoJsonSource(FUENTE_PUNTOS))
    estilo.addLayer(
        CircleLayer(CAPA_PUNTOS, FUENTE_PUNTOS).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(Color.Red.toArgb()),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.White.toArgb())
        )
    )
    // Si el estilo no trae la fuente de letras en la caché, el nombre no se dibuja pero el círculo sí
    estilo.addLayer(
        SymbolLayer(CAPA_NOMBRES, FUENTE_PUNTOS).withProperties(
            PropertyFactory.textField(Expression.get(PROPIEDAD_NOMBRE)),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textHaloColor(Color.White.toArgb()),
            PropertyFactory.textHaloWidth(1.5f)
        )
    )
}

private fun mostrarPuntos(estilo: Style, puntos: List<Punto>) {
    val fuente = estilo.getSourceAs<GeoJsonSource>(FUENTE_PUNTOS) ?: return
    fuente.setGeoJson(
        FeatureCollection.fromFeatures(
            puntos.map {
                Feature.fromGeometry(Point.fromLngLat(it.longitud, it.latitud)).apply {
                    addStringProperty(PROPIEDAD_NOMBRE, it.nombre)
                }
            }
        )
    )
}
