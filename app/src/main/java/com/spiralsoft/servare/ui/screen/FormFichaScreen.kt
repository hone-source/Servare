package com.spiralsoft.servare.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.spiralsoft.servare.domain.model.ElementoEspacio
import com.spiralsoft.servare.domain.model.Ficha
import com.spiralsoft.servare.domain.model.Geometria
import com.spiralsoft.servare.domain.model.Participante
import com.spiralsoft.servare.domain.model.PuebloOriginario
import com.spiralsoft.servare.domain.model.PuntoPoligonal
import com.spiralsoft.servare.domain.model.RegistroArchivos
import com.spiralsoft.servare.domain.model.Ritual
import com.spiralsoft.servare.ui.viewModel.FichaFormViewModel
import com.spiralsoft.servare.domain.model.TipoEspacio
import com.spiralsoft.servare.domain.model.TipoLugar
import com.spiralsoft.servare.domain.model.TipoRitual
import com.spiralsoft.servare.ui.viewModel.state.quitar
import com.spiralsoft.servare.ui.viewModel.state.reemplazar

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFichaScreen(
    onVolver: () -> Unit,
    onGuardada: () -> Unit,
    viewModel: FichaFormViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(form.guardada) {
        if (form.guardada) onGuardada()
    }
    LaunchedEffect(form.mensaje) {
        form.mensaje?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            viewModel.mensajeMostrado()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.entityId == null) "Ficha técnica de levantamiento" else "Editar ficha") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::guardar,
                        enabled = !form.guardando && !form.cargando && form.errorCarga == null
                    ) {
                        Text("Guardar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val errorCarga = form.errorCarga
        when {
            // Sin formulario hasta tener la ficha, para no mezclar lo escrito con lo guardado
            form.cargando -> Box(Modifier.fillMaxSize().padding(padding))
            errorCarga != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(errorCarga, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            else -> FichaForm(
                form = form.ficha,
                errorNombre = form.errorNombre,
                onChange = viewModel::updateFicha,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
            )
        }
    }
}

@Composable
private fun FichaForm(
    form: Ficha,
    errorNombre: String?,
    onChange: ((Ficha) -> Ficha) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Seccion("Registro de levantamiento") {
            Etiqueta("Pueblo originario")
            Opciones(PuebloOriginario.entries, form.pueblo, { it.etiqueta }) { v ->
                onChange { it.copy(pueblo = v) }
            }
            Campo("Núcleo agrario / Ejido / Localidad", form.nucleoAgrario) { v ->
                onChange { it.copy(nucleoAgrario = v) }
            }
            Campo("Comunidad", form.comunidad) { v -> onChange { it.copy(comunidad = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(
                    "Número de levantamiento", form.numeroLevantamiento,
                    Modifier.weight(1f), teclado = KeyboardType.Number
                ) { v -> onChange { it.copy(numeroLevantamiento = v) } }
                CampoFecha("Fecha de levantamiento", form.fechaLevantamiento, Modifier.weight(1f)) { v ->
                    onChange { it.copy(fechaLevantamiento = v) }
                }
            }
        }

        Seccion("1. Datos generales") {
            Etiqueta("Tipo de espacio sagrado")
            Opciones(TipoEspacio.entries, form.tipoEspacio, { it.etiqueta }) { v ->
                onChange { it.copy(tipoEspacio = v) }
            }
            if (form.tipoEspacio == TipoEspacio.OTRO) {
                Campo("Otro tipo de espacio", form.tipoEspacioOtro) { v ->
                    onChange { it.copy(tipoEspacioOtro = v) }
                }
            }
            Campo("Nombre del lugar", form.nombre, ayuda = "Obligatorio", error = errorNombre) { v ->
                onChange { it.copy(nombre = v) }
            }
            Campo(
                "Otros nombres", form.otrosNombres, multilinea = true,
                ayuda = "Cómo se le refiere, conoce y/o identifica"
            ) { v -> onChange { it.copy(otrosNombres = v) } }
            Campo("Cómo se llega al lugar", form.comoLlegar, multilinea = true) { v ->
                onChange { it.copy(comoLlegar = v) }
            }
        }

        Seccion("2. Datos geográficos y de ubicación") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Estado", form.estado, Modifier.weight(1f)) { v -> onChange { it.copy(estado = v) } }
                Campo("Municipio", form.municipio, Modifier.weight(1f)) { v ->
                    onChange { it.copy(municipio = v) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Localidad", form.localidad, Modifier.weight(1f)) { v ->
                    onChange { it.copy(localidad = v) }
                }
                Campo("Tipo de propiedad", form.tipoPropiedad, Modifier.weight(1f)) { v ->
                    onChange { it.copy(tipoPropiedad = v) }
                }
            }
            Campo("Poblados, sitios o lugares próximos", form.lugaresProximos, multilinea = true) { v ->
                onChange { it.copy(lugaresProximos = v) }
            }

            Subtitulo("Datos de georreferencia")
            Opciones(Geometria.entries, form.geometria, { it.etiqueta }) { v ->
                onChange { it.copy(geometria = v) }
            }
            Campo("Punto de referencia en GPS", form.puntoGps) { v -> onChange { it.copy(puntoGps = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Altura", form.altura, Modifier.weight(1f), teclado = KeyboardType.Decimal) { v ->
                    onChange { it.copy(altura = v) }
                }
                Campo("Área del lugar/sitio", form.area, Modifier.weight(1f)) { v ->
                    onChange { it.copy(area = v) }
                }
            }
            Campo("U.T.M.", form.utm) { v -> onChange { it.copy(utm = v) } }

            Subtitulo("Poligonal")
            form.poligonal.forEachIndexed { i, punto ->
                Renglon("Punto ${i + 1}", onQuitar = { onChange { it.copy(poligonal = it.poligonal.quitar(i)) } }) {
                    val cambiar = { nuevo: PuntoPoligonal ->
                        onChange { it.copy(poligonal = it.poligonal.reemplazar(i, nuevo)) }
                    }
                    Campo("Punto de referencia", punto.puntoReferencia) { cambiar(punto.copy(puntoReferencia = it)) }
                    Campo("UTM", punto.utm) { cambiar(punto.copy(utm = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Campo("Margen de error", punto.margenError, Modifier.weight(1f), teclado = KeyboardType.Decimal) {
                            cambiar(punto.copy(margenError = it))
                        }
                        Campo("Altura", punto.altura, Modifier.weight(1f), teclado = KeyboardType.Decimal) {
                            cambiar(punto.copy(altura = it))
                        }
                    }
                }
            }
            Agregar("Agregar punto") { onChange { it.copy(poligonal = it.poligonal + PuntoPoligonal()) } }
        }

        Seccion("3. Uso social") {
            Subtitulo("3.1 Historia y relatos del espacio")
            Campo(
                "Historia o relato", form.historia, multilinea = true,
                ayuda = "Cómo se conformó el lugar o qué relato se cuenta de él"
            ) { v -> onChange { it.copy(historia = v) } }
            Campo("Informantes y su procedencia", form.informantes, multilinea = true) { v ->
                onChange { it.copy(informantes = v) }
            }

            Subtitulo("3.2 Rituales y actos ceremoniales")
            form.rituales.forEachIndexed { i, ritual ->
                Renglon("Ritual ${i + 1}", onQuitar = { onChange { it.copy(rituales = it.rituales.quitar(i)) } }) {
                    val cambiar = { nuevo: Ritual ->
                        onChange { it.copy(rituales = it.rituales.reemplazar(i, nuevo)) }
                    }
                    Etiqueta("Tipo")
                    Opciones(TipoRitual.entries, ritual.tipo, { it.etiqueta }) { cambiar(ritual.copy(tipo = it)) }
                    if (ritual.tipo == TipoRitual.OTRO) {
                        Campo("Especificar tipo", ritual.tipoOtro) { cambiar(ritual.copy(tipoOtro = it)) }
                    }
                    Campo("Nombres", ritual.nombres) { cambiar(ritual.copy(nombres = it)) }
                    Campo("Temporalidad, fecha o frecuencia", ritual.temporalidad) {
                        cambiar(ritual.copy(temporalidad = it))
                    }
                    Campo(
                        "Descripción", ritual.descripcion, multilinea = true,
                        ayuda = "¿Qué sucede y quién lo realiza?"
                    ) { cambiar(ritual.copy(descripcion = it)) }
                }
            }
            Agregar("Agregar ritual") { onChange { it.copy(rituales = it.rituales + Ritual()) } }
        }

        Seccion("4. Conformación del espacio sagrado") {
            Campo(
                "Descripción del lugar", form.descripcionEspacio, multilinea = true,
                ayuda = "Elementos naturales y/o naturales transformados que conforman el espacio"
            ) { v -> onChange { it.copy(descripcionEspacio = v) } }
            form.elementos.forEachIndexed { i, elemento ->
                Renglon("Elemento ${i + 1}", onQuitar = { onChange { it.copy(elementos = it.elementos.quitar(i)) } }) {
                    val cambiar = { nuevo: ElementoEspacio ->
                        onChange { it.copy(elementos = it.elementos.reemplazar(i, nuevo)) }
                    }
                    Etiqueta("Tipo")
                    Opciones(TipoLugar.entries, elemento.tipo, { it.etiqueta }) { cambiar(elemento.copy(tipo = it)) }
                    Campo(
                        "Forma", elemento.forma,
                        ayuda = "Ej. ojo de agua, manantial, árbol, sendero, peñasco, cueva, pozo"
                    ) { cambiar(elemento.copy(forma = it)) }
                    Campo("Ancestro / Deidad / Santo", elemento.ancestro) { cambiar(elemento.copy(ancestro = it)) }
                    Campo("Uso del lugar", elemento.uso, multilinea = true) { cambiar(elemento.copy(uso = it)) }
                    Campo("Características del lugar", elemento.caracteristicas, multilinea = true) {
                        cambiar(elemento.copy(caracteristicas = it))
                    }
                    Campo("Elementos del lugar", elemento.elementos, multilinea = true) {
                        cambiar(elemento.copy(elementos = it))
                    }
                    Etiqueta("Medidas (opcional en el lugar natural)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Campo("Largo", elemento.largo, Modifier.weight(1f), teclado = KeyboardType.Decimal) {
                            cambiar(elemento.copy(largo = it))
                        }
                        Campo("Ancho", elemento.ancho, Modifier.weight(1f), teclado = KeyboardType.Decimal) {
                            cambiar(elemento.copy(ancho = it))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Campo("Alto", elemento.alto, Modifier.weight(1f), teclado = KeyboardType.Decimal) {
                            cambiar(elemento.copy(alto = it))
                        }
                        Campo("Profundidad", elemento.profundidad, Modifier.weight(1f), teclado = KeyboardType.Decimal) {
                            cambiar(elemento.copy(profundidad = it))
                        }
                    }
                }
            }
            Agregar("Agregar elemento") { onChange { it.copy(elementos = it.elementos + ElementoEspacio()) } }
        }

        Seccion("5. Cartografía del informante") {
            Campo("Observaciones", form.observacionesCartografia, multilinea = true) { v ->
                onChange { it.copy(observacionesCartografia = v) }
            }
        }

        Seccion("6. Estado de conservación") {
            Campo(
                "¿Presenta algún daño?", form.danos, multilinea = true,
                ayuda = "Humano o natural, mencionarlos"
            ) { v -> onChange { it.copy(danos = v) } }
            Campo(
                "¿Corre algún riesgo?", form.riesgos, multilinea = true,
                ayuda = "Humano o natural, mencionarlos"
            ) { v -> onChange { it.copy(riesgos = v) } }
        }

        Seccion("7. Información técnica del levantamiento") {
            CampoFecha("Fecha", form.fechaTecnica) { v -> onChange { it.copy(fechaTecnica = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoHora("Hora de inicio", form.horaInicio, Modifier.weight(1f)) { v ->
                    onChange { it.copy(horaInicio = v) }
                }
                CampoHora("Hora de fin", form.horaFin, Modifier.weight(1f)) { v ->
                    onChange { it.copy(horaFin = v) }
                }
            }
            CamposArchivos("Fotografías realizadas", form.fotografias) { v ->
                onChange { it.copy(fotografias = v) }
            }
            CamposArchivos("Grabaciones realizadas", form.grabaciones) { v ->
                onChange { it.copy(grabaciones = v) }
            }
            Campo("Duración de la grabación", form.duracionGrabacion) { v ->
                onChange { it.copy(duracionGrabacion = v) }
            }
            CamposArchivos("Georreferencias realizadas", form.georreferencias) { v ->
                onChange { it.copy(georreferencias = v) }
            }
            Campo("Integrantes del equipo", form.integrantesEquipo, multilinea = true) { v ->
                onChange { it.copy(integrantesEquipo = v) }
            }
            Campo("Comentarios u observaciones finales", form.comentariosFinales, multilinea = true) { v ->
                onChange { it.copy(comentariosFinales = v) }
            }
        }

        Seccion("8. Aval comunitario / ejidal / autoridad local") {
            Campo("Autoridad local", form.autoridadLocal) { v -> onChange { it.copy(autoridadLocal = v) } }
            Subtitulo("Participantes en el proceso de levantamiento")
            form.participantes.forEachIndexed { i, participante ->
                Renglon(
                    "Participante ${i + 1}",
                    onQuitar = { onChange { it.copy(participantes = it.participantes.quitar(i)) } }
                ) {
                    val cambiar = { nuevo: Participante ->
                        onChange { it.copy(participantes = it.participantes.reemplazar(i, nuevo)) }
                    }
                    Campo("Cargo", participante.cargo) { cambiar(participante.copy(cargo = it)) }
                    Campo("Nombre", participante.nombre) { cambiar(participante.copy(nombre = it)) }
                }
            }
            Agregar("Agregar participante") {
                onChange { it.copy(participantes = it.participantes + Participante()) }
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        contenido()
    }
}

@Composable
private fun Subtitulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    multilinea: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    ayuda: String? = null,
    error: String? = null,
    onValor: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = (error ?: ayuda)?.let { { Text(it) } },
        singleLine = !multilinea,
        minLines = if (multilinea) 3 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = teclado
        ),
        modifier = modifier
    )
}

@Composable
private fun <T> Opciones(
    opciones: List<T>,
    seleccion: T?,
    etiqueta: (T) -> String,
    onSeleccion: (T?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { opcion ->
            FilterChip(
                selected = opcion == seleccion,
                onClick = { onSeleccion(if (opcion == seleccion) null else opcion) },
                label = { Text(etiqueta(opcion)) }
            )
        }
    }
}

// Elemento de una lista repetible (poligonal, rituales, elementos, participantes)
@Composable
private fun Renglon(titulo: String, onQuitar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onQuitar) { Text("Quitar") }
            }
            contenido()
        }
    }
}

@Composable
private fun Agregar(texto: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(texto) }
}

@Composable
private fun CamposArchivos(etiqueta: String, registro: RegistroArchivos, onCambio: (RegistroArchivos) -> Unit) {
    Etiqueta(etiqueta)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Campo("Cantidad", registro.cantidad, Modifier.weight(1f), teclado = KeyboardType.Number) {
            onCambio(registro.copy(cantidad = it))
        }
        Campo("Del archivo", registro.desde, Modifier.weight(1f)) { onCambio(registro.copy(desde = it)) }
        Campo("Al archivo", registro.hasta, Modifier.weight(1f)) { onCambio(registro.copy(hasta = it)) }
    }
}

// Campo de solo lectura que abre un selector al tocarlo
@Composable
private fun CampoSelector(etiqueta: String, texto: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(
            value = texto,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(etiqueta) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun CampoFecha(
    etiqueta: String,
    fecha: LocalDate?,
    modifier: Modifier = Modifier,
    onFecha: (LocalDate?) -> Unit
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    CampoSelector(etiqueta, fecha?.format(FORMATO_FECHA).orEmpty(), modifier) { abierto = true }

    if (abierto) {
        // El DatePicker trabaja en milisegundos UTC
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fecha?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    onFecha(estado.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    })
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = estado)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoHora(
    etiqueta: String,
    hora: LocalTime?,
    modifier: Modifier = Modifier,
    onHora: (LocalTime) -> Unit
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    CampoSelector(etiqueta, hora?.format(FORMATO_HORA).orEmpty(), modifier) { abierto = true }

    if (abierto) {
        val inicial = hora ?: LocalTime.now()
        val estado = rememberTimePickerState(
            initialHour = inicial.hour,
            initialMinute = inicial.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(etiqueta) },
            text = { TimeInput(state = estado) },
            confirmButton = {
                TextButton(onClick = {
                    onHora(LocalTime.of(estado.hour, estado.minute))
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } }
        )
    }
}
