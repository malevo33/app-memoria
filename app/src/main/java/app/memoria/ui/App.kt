package app.memoria.ui

import android.Manifest
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import app.memoria.Importer
import app.memoria.Prefs
import app.memoria.core.Segmenter
import kotlinx.coroutines.launch
import java.util.Locale
import android.speech.tts.Voice as TtsVoice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.memoria.Store
import app.memoria.Voice
import app.memoria.core.Coach
import app.memoria.core.Doc
import app.memoria.core.Planner

private sealed interface Screen {
    data object Home : Screen
    data class Edit(val text: String, val title: String = "") : Screen
    data object Voice : Screen
    data class Detail(val id: String) : Screen
    data class Session(val id: String) : Screen
}

@Composable
fun App(store: Store, shared: Importer.Imported?, onSharedConsumed: () -> Unit) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(shared) {
        if (shared != null) {
            screen = Screen.Edit(shared.text, shared.title)
            onSharedConsumed()
        }
    }
    BackHandler(enabled = screen != Screen.Home) {
        screen = when (val s = screen) {
            is Screen.Session -> Screen.Detail(s.id)
            else -> Screen.Home
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                docs = remember(s) { store.list() },
                onNew = { screen = Screen.Edit("") },
                onVoice = { screen = Screen.Voice },
                onOpen = { screen = Screen.Detail(it.id) },
            )
            Screen.Voice -> VoiceScreen(onBack = { screen = Screen.Home })
            is Screen.Edit -> EditScreen(
                initialText = s.text,
                initialTitle = s.title,
                onSave = { title, text -> screen = Screen.Detail(store.create(title, text).id) },
                onCancel = { screen = Screen.Home },
            )
            is Screen.Detail -> {
                val doc = remember(s) { store.load(s.id) }
                if (doc == null) {
                    LaunchedEffect(Unit) { screen = Screen.Home }
                } else {
                    DetailScreen(
                        doc = doc,
                        onStart = { screen = Screen.Session(doc.id) },
                        onDelete = { store.delete(doc.id); screen = Screen.Home },
                    )
                }
            }
            is Screen.Session -> {
                val doc = remember(s) { store.load(s.id) }
                if (doc == null) {
                    LaunchedEffect(Unit) { screen = Screen.Home }
                } else {
                    SessionScreen(doc = doc, store = store, onExit = { screen = Screen.Detail(doc.id) })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(docs: List<Doc>, onNew: () -> Unit, onVoice: () -> Unit, onOpen: (Doc) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Memoria", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onVoice) { Text("Voz") }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Nuevo texto", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (docs.isEmpty()) {
            Text(
                "Cargá un texto para empezar: abrí un PDF o un .txt, pegalo, o compartilo desde otra app con \"Compartir → Memoria\".",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(docs, key = { it.id }) { doc ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(doc) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(doc.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${doc.masteredParts} de ${doc.parts.size} partes salen solas",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Los textos más largos que esto no se editan en pantalla: el campo de texto de Android se traba. */
private const val EDITABLE_LIMIT = 20_000

@Composable
private fun EditScreen(
    initialText: String,
    initialTitle: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var title by remember { mutableStateOf(initialTitle) }
    var text by remember { mutableStateOf(initialText) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val stats = remember(text) { if (text.isBlank()) null else Segmenter.split(text).let { p -> p.sumOf { it.words.size } to p.size } }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loading = true
            error = null
            scope.launch {
                try {
                    val imported = Importer.read(context, uri)
                    text = imported.text
                    if (title.isBlank()) title = imported.title
                } catch (e: Importer.NoTextException) {
                    error = "Ese archivo no tiene texto para leer. Si es un PDF escaneado (fotos de páginas), no se puede usar todavía."
                } catch (e: Exception) {
                    error = "No pude abrir el archivo: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nuevo texto", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openFile.launch(arrayOf("application/pdf", "text/plain")) }) {
                Text("Abrir PDF o .txt")
            }
            OutlinedButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Pegar") }
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Leyendo el archivo…")
            }
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        if (stats != null) {
            Text("${stats.first} palabras · ${stats.second} partes", style = MaterialTheme.typography.labelLarge)
        }
        if (text.length <= EDITABLE_LIMIT) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("O escribí el texto acá (una línea en blanco separa secciones)") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            // Texto largo: se muestra el comienzo sin editar, para que no se trabe el teclado.
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(text.take(3000) + "…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { text = "" }) { Text("Quitar el texto") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = text.isNotBlank() && !loading,
                onClick = { onSave(title.ifBlank { text.trim().take(40) }, text) },
            ) { Text("Guardar") }
        }
    }
}

@Composable
private fun VoiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val voice = remember { Voice(context) }
    var voices by remember { mutableStateOf<List<TtsVoice>>(emptyList()) }
    var chosen by remember { mutableStateOf(prefs.voiceName) }
    var rate by remember { mutableFloatStateOf(prefs.speechRate) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(voice) {
        if (voice.awaitReady()) {
            voices = voice.spanishVoices()
            if (chosen == null) chosen = voices.firstOrNull()?.name
        }
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    fun test() {
        prefs.voiceName = chosen
        prefs.speechRate = rate
        voice.applyPrefs()
        scope.launch { voice.speak("Hola, así sueno yo. Artículo 1°. Seguí después de: los casos que este Código rige.") }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voz", style = MaterialTheme.typography.headlineSmall)
        Text("Velocidad: ${"%.1f".format(rate)}×")
        Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.6f..1.6f, steps = 9)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::test) { Text("Probar") }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("Instalar más voces") }
        }
        if (voices.isEmpty()) Text("Buscando voces en español…")
        LazyColumn(Modifier.weight(1f)) {
            items(voices, key = { it.name }) { v ->
                Row(
                    Modifier.fillMaxWidth().clickable { chosen = v.name; test() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == v.name, onClick = { chosen = v.name; test() })
                    Column {
                        Text(voiceLabel(v), style = MaterialTheme.typography.bodyLarge)
                        Text(v.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Button(onClick = { prefs.voiceName = chosen; prefs.speechRate = rate; onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Listo")
        }
    }
}

private fun voiceLabel(v: TtsVoice): String {
    val country = v.locale.getDisplayCountry(Locale.forLanguageTag("es")).ifBlank { "general" }
    val quality = when {
        v.quality >= TtsVoice.QUALITY_VERY_HIGH -> "muy natural"
        v.quality >= TtsVoice.QUALITY_HIGH -> "natural"
        else -> "básica"
    }
    val net = if (v.isNetworkConnectionRequired) " · necesita internet" else ""
    return "Español ($country) · $quality$net"
}

@Composable
private fun DetailScreen(doc: Doc, onStart: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val plan = remember(doc) { Planner.plan(doc, newLimit = 5) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(doc.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("${doc.masteredParts} de ${doc.parts.size} partes salen solas · ${doc.masteredWords} de ${doc.totalWords} palabras")
        val minutes = doc.studyMillis / 60000
        val speed = doc.minutesPer100Words
        Text(
            "$minutes min de estudio" +
                if (speed != null) " · %.1f min cada 100 palabras".format(speed) else ""
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(
                if (plan.isEmpty) "Nada pendiente hoy" else "Empezar sesión (${plan.review.size + plan.new.size} partes)",
                fontSize = 18.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Mapa de activadores", style = MaterialTheme.typography.titleMedium)
        Legend()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(doc.parts, key = { it.index }) { part ->
                val cue = doc.stat(part.index)?.minCue ?: -1
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(12.dp).background(cueColor(cue), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(part.text, style = MaterialTheme.typography.bodyMedium)
                        if (cue > 0) Text("Pista: $cue palabras", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        TextButton(onClick = { confirmDelete = true }) { Text("Borrar texto") }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar este texto?") },
            text = { Text("Se pierde también el progreso.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        listOf(-1 to "sin practicar", 0 to "sale sola", 2 to "poca pista", 5 to "mucha pista").forEach { (cue, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(cueColor(cue), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun cueColor(cue: Int): Color = when {
    cue < 0 -> Color(0xFF9E9E9E)
    cue == 0 -> Color(0xFF2E7D32)
    cue <= 3 -> Color(0xFFF9A825)
    else -> Color(0xFFC62828)
}

@Composable
private fun SessionScreen(doc: Doc, store: Store, onExit: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableIntStateOf(0) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(asked) { if (!granted) askMic.launch(Manifest.permission.RECORD_AUDIO) }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Necesito el micrófono para escucharte recitar.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { asked++ }) { Text("Dar permiso") }
            TextButton(onClick = onExit) { Text("Volver") }
        }
        return
    }

    val voice = remember { Voice(context) }
    val coach = remember { Coach(voice, doc, onSave = store::save) }
    val ui by coach.ui.collectAsState()
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            voice.release()
        }
    }
    LaunchedEffect(coach) {
        coach.run()
        onExit()
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(ui.partLabel, style = MaterialTheme.typography.titleMedium)
        Text(
            ui.prompt,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier.size(72.dp).background(
                if (ui.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (ui.listening) "Te escucho" else "", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
        }
        if (ui.heard.isNotBlank()) Text("Escuché: ${ui.heard}", textAlign = TextAlign.Center)
        Text(ui.feedback, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Text(
            "Decí \"pista\", \"repetí\", \"leé\", \"siguiente\" o \"terminar\".",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Terminar")
        }
    }
}
