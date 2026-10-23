package com.spiralsoft.servare.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.Geometria
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuebloOriginario
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.Ritual
import com.spiralsoft.servare.domain.model.TipoEspacio
import com.spiralsoft.servare.domain.model.TipoLugar
import com.spiralsoft.servare.domain.model.TipoRitual
import com.spiralsoft.servare.ui.composable.formulario.CampoArchivos
import com.spiralsoft.servare.ui.composable.formulario.CampoFecha
import com.spiralsoft.servare.ui.composable.formulario.CampoHora
import com.spiralsoft.servare.ui.composable.formulario.CampoOpciones
import com.spiralsoft.servare.ui.composable.formulario.CampoTexto
import com.spiralsoft.servare.ui.composable.formulario.ListaEditable
import com.spiralsoft.servare.ui.composable.formulario.SeccionFormulario
import com.spiralsoft.servare.ui.viewModel.FichaFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFichaScreen(
    onVolver: () -> Unit,
    onGuardada: () -> Unit,
    viewModel: FichaFormViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(form.guardada) {
        if (form.guardada) onGuardada()
    }

    LaunchedEffect(form.mensaje) {
        form.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    // Lo capturado no se pierde en silencio: salir con cambios pide confirmación
    val salir = { if (form.hayCambios && !form.guardada) confirmarSalida = true else onVolver() }
    BackHandler(onBack = salir)

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Los cambios de esta ficha no se han guardado en el dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    onVolver()
                }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Seguir editando") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.entityId == null) "Nueva ficha" else "Editar ficha") },
                navigationIcon = {
                    IconButton(onClick = salir) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::guardar,
                        enabled = !form.guardando && !form.cargando && form.errorCarga == null
                    ) { Text("Guardar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            form.cargando -> Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            form.errorCarga != null -> Column(
                modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(form.errorCarga.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onVolver) { Text("Volver") }
            }
            else -> FormularioFicha(
                ficha = form.ficha,
                errorNombre = form.errorNombre,
                onCambio = viewModel::updateFicha,
                modifier = modifier.imePadding()
            )
        }
    }
}

@Composable
private fun FormularioFicha(
    ficha: Ficha,
    errorNombre: String?,
    onCambio: ((Ficha) -> Ficha) -> Unit,
    modifier: Modifier
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "registro") {
            Seccion("Registro de levantamiento", abiertaAlInicio = true) {
                CampoOpciones(
                    "Pueblo originario", PuebloOriginario.entries, ficha.pueblo, { it.etiqueta },
                    { v -> onCambio { it.copy(pueblo = v) } }
                )
                CampoTexto("Núcleo agrario / Ejido / Localidad", ficha.nucleoAgrario, { v -> onCambio { it.copy(nucleoAgrario = v) } })
                CampoTexto("Comunidad", ficha.comunidad, { v -> onCambio { it.copy(comunidad = v) } })
                CampoTexto("Número de levantamiento", ficha.numeroLevantamiento, { v -> onCambio { it.copy(numeroLevantamiento = v) } })
                CampoFecha("Fecha de levantamiento", ficha.fechaLevantamiento, { v -> onCambio { it.copy(fechaLevantamiento = v) } })
            }
        }

        item(key = "generales") {
            Seccion("1. Datos generales", abiertaAlInicio = true) {
                CampoOpciones(
                    "Tipo de espacio sagrado", TipoEspacio.entries, ficha.tipoEspacio, { it.etiqueta },
                    { v -> onCambio { it.copy(tipoEspacio = v) } }
                )
                if (ficha.tipoEspacio == TipoEspacio.OTRO) {
                    CampoTexto("Otro tipo de espacio", ficha.tipoEspacioOtro, { v -> onCambio { it.copy(tipoEspacioOtro = v) } })
                }
                CampoTexto(
                    "Nombre del lugar *", ficha.nombre, { v -> onCambio { it.copy(nombre = v) } },
                    error = errorNombre
                )
                CampoTexto(
                    "Otros nombres con que se conoce", ficha.otrosNombres,
                    { v -> onCambio { it.copy(otrosNombres = v) } }, multilinea = true
                )
                CampoTexto(
                    "Cómo se llega al lugar", ficha.comoLlegar,
                    { v -> onCambio { it.copy(comoLlegar = v) } }, multilinea = true
                )
            }
        }

        item(key = "geograficos") {
            Seccion("2. Datos geográficos y de ubicación") {
                CampoTexto("Estado", ficha.estado, { v -> onCambio { it.copy(estado = v) } })
                CampoTexto("Municipio", ficha.municipio, { v -> onCambio { it.copy(municipio = v) } })
                CampoTexto("Localidad", ficha.localidad, { v -> onCambio { it.copy(localidad = v) } })
                CampoTexto("Tipo de propiedad", ficha.tipoPropiedad, { v -> onCambio { it.copy(tipoPropiedad = v) } })
                CampoTexto(
                    "Poblados, sitios o lugares próximos", ficha.lugaresProximos,
                    { v -> onCambio { it.copy(lugaresProximos = v) } }, multilinea = true
                )
                CampoOpciones(
                    "Punto, línea, polígono", Geometria.entries, ficha.geometria, { it.etiqueta },
                    { v -> onCambio { it.copy(geometria = v) } }
                )
                CampoTexto("Punto de referencia en GPS", ficha.puntoGps, { v -> onCambio { it.copy(puntoGps = v) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto("Altura", ficha.altura, { v -> onCambio { it.copy(altura = v) } }, Modifier.weight(1f))
                    CampoTexto("Área del lugar", ficha.area, { v -> onCambio { it.copy(area = v) } }, Modifier.weight(1f))
                }
                CampoTexto("U.T.M.", ficha.utm, { v -> onCambio { it.copy(utm = v) } })
                ListaEditable(
                    "Poligonal", ficha.poligonal, ::PuntoPoligonal,
                    { v -> onCambio { it.copy(poligonal = v) } }
                ) { punto, cambiar ->
                    CampoTexto("Punto de referencia", punto.puntoReferencia, { cambiar(punto.copy(puntoReferencia = it)) })
                    CampoTexto("UTM", punto.utm, { cambiar(punto.copy(utm = it)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoTexto("Margen de error", punto.margenError, { cambiar(punto.copy(margenError = it)) }, Modifier.weight(1f))
                        CampoTexto("Altura", punto.altura, { cambiar(punto.copy(altura = it)) }, Modifier.weight(1f))
                    }
                }
            }
        }

        item(key = "usoSocial") {
            Seccion("3. Uso social") {
                CampoTexto(
                    "Historia y relatos del espacio", ficha.historia,
                    { v -> onCambio { it.copy(historia = v) } }, multilinea = true
                )
                CampoTexto(
                    "Informantes y su procedencia", ficha.informantes,
                    { v -> onCambio { it.copy(informantes = v) } }, multilinea = true
                )
                ListaEditable(
                    "Rituales y actos ceremoniales", ficha.rituales, ::Ritual,
                    { v -> onCambio { it.copy(rituales = v) } }
                ) { ritual, cambiar ->
                    CampoOpciones("Tipo", TipoRitual.entries, ritual.tipo, { it.etiqueta }, { cambiar(ritual.copy(tipo = it)) })
                    if (ritual.tipo == TipoRitual.OTRO) {
                        CampoTexto("Otro tipo", ritual.tipoOtro, { cambiar(ritual.copy(tipoOtro = it)) })
                    }
                    CampoTexto("Nombres", ritual.nombres, { cambiar(ritual.copy(nombres = it)) })
                    CampoTexto("Temporalidad, fecha o frecuencia", ritual.temporalidad, { cambiar(ritual.copy(temporalidad = it)) })
                    CampoTexto(
                        "Descripción: qué sucede y quién lo realiza", ritual.descripcion,
                        { cambiar(ritual.copy(descripcion = it)) }, multilinea = true
                    )
                }
            }
        }

        item(key = "conformacion") {
            Seccion("4. Conformación del espacio sagrado") {
                CampoTexto(
                    "Descripción del lugar", ficha.descripcionEspacio,
                    { v -> onCambio { it.copy(descripcionEspacio = v) } }, multilinea = true
                )
                ListaEditable(
                    "Elementos del espacio", ficha.elementos, ::ElementoEspacio,
                    { v -> onCambio { it.copy(elementos = v) } }
                ) { elemento, cambiar ->
                    CampoOpciones("Tipo", TipoLugar.entries, elemento.tipo, { it.etiqueta }, { cambiar(elemento.copy(tipo = it)) })
                    CampoTexto("Forma", elemento.forma, { cambiar(elemento.copy(forma = it)) })
                    CampoTexto("Ancestro / Deidad / Santo", elemento.ancestro, { cambiar(elemento.copy(ancestro = it)) })
                    CampoTexto("Uso del lugar", elemento.uso, { cambiar(elemento.copy(uso = it)) })
                    CampoTexto("Características del lugar", elemento.caracteristicas, { cambiar(elemento.copy(caracteristicas = it)) })
                    CampoTexto("Elementos del lugar", elemento.elementos, { cambiar(elemento.copy(elementos = it)) })
                    Text("Medidas (opcional)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoTexto("L", elemento.largo, { cambiar(elemento.copy(largo = it)) }, Modifier.weight(1f), teclado = KeyboardType.Decimal)
                        CampoTexto("AN", elemento.ancho, { cambiar(elemento.copy(ancho = it)) }, Modifier.weight(1f), teclado = KeyboardType.Decimal)
                        CampoTexto("AL", elemento.alto, { cambiar(elemento.copy(alto = it)) }, Modifier.weight(1f), teclado = KeyboardType.Decimal)
                        CampoTexto("P", elemento.profundidad, { cambiar(elemento.copy(profundidad = it)) }, Modifier.weight(1f), teclado = KeyboardType.Decimal)
                    }
                }
            }
        }

        item(key = "cartografia") {
            Seccion("5. Cartografía del informante") {
                CampoTexto(
                    "Observaciones", ficha.observacionesCartografia,
                    { v -> onCambio { it.copy(observacionesCartografia = v) } }, multilinea = true
                )
            }
        }

        item(key = "conservacion") {
            Seccion("6. Estado de conservación") {
                CampoTexto(
                    "¿Presenta algún daño? (humano o natural)", ficha.danos,
                    { v -> onCambio { it.copy(danos = v) } }, multilinea = true
                )
                CampoTexto(
                    "¿Corre algún riesgo? (humano o natural)", ficha.riesgos,
                    { v -> onCambio { it.copy(riesgos = v) } }, multilinea = true
                )
            }
        }

        item(key = "tecnica") {
            Seccion("7. Información técnica del levantamiento") {
                CampoFecha("Fecha", ficha.fechaTecnica, { v -> onCambio { it.copy(fechaTecnica = v) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoHora("Hora de inicio", ficha.horaInicio, { v -> onCambio { it.copy(horaInicio = v) } }, Modifier.weight(1f))
                    CampoHora("Hora de fin", ficha.horaFin, { v -> onCambio { it.copy(horaFin = v) } }, Modifier.weight(1f))
                }
                CampoArchivos("Fotografías realizadas", ficha.fotografias) { v -> onCambio { it.copy(fotografias = v) } }
                CampoArchivos("Grabaciones realizadas", ficha.grabaciones) { v -> onCambio { it.copy(grabaciones = v) } }
                CampoTexto("Duración de la grabación", ficha.duracionGrabacion, { v -> onCambio { it.copy(duracionGrabacion = v) } })
                CampoArchivos("Georreferencias realizadas", ficha.georreferencias) { v -> onCambio { it.copy(georreferencias = v) } }
                CampoTexto(
                    "Integrantes del equipo", ficha.integrantesEquipo,
                    { v -> onCambio { it.copy(integrantesEquipo = v) } }, multilinea = true
                )
                CampoTexto(
                    "Comentarios u observaciones finales", ficha.comentariosFinales,
                    { v -> onCambio { it.copy(comentariosFinales = v) } }, multilinea = true
                )
            }
        }

        item(key = "aval") {
            Seccion("8. Aval comunitario / ejidal / autoridad local") {
                CampoTexto("Autoridad local", ficha.autoridadLocal, { v -> onCambio { it.copy(autoridadLocal = v) } })
                ListaEditable(
                    "Participantes", ficha.participantes, ::Participante,
                    { v -> onCambio { it.copy(participantes = v) } }
                ) { participante, cambiar ->
                    CampoTexto("Cargo", participante.cargo, { cambiar(participante.copy(cargo = it)) })
                    CampoTexto("Nombre", participante.nombre, { cambiar(participante.copy(nombre = it)) })
                }
            }
        }
    }
}

// Sección plegable que recuerda si estaba abierta (también tras rotar o recrear la pantalla)
@Composable
private fun Seccion(
    titulo: String,
    abiertaAlInicio: Boolean = false,
    contenido: @Composable () -> Unit
) {
    var abierta by rememberSaveable { mutableStateOf(abiertaAlInicio) }
    SeccionFormulario(titulo, abierta, onAlternar = { abierta = !abierta }, contenido = contenido)
}
