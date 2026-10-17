package app.memoria.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lo que la sesión necesita del teléfono: hablar y escuchar. */
interface VoiceIO {
    suspend fun speak(text: String)

    /** Devuelve lo que se escuchó, o "" si hubo silencio o error. */
    suspend fun listen(): String
}

data class SessionUi(
    val partLabel: String = "",
    val prompt: String = "",
    val heard: String = "",
    val feedback: String = "",
    val listening: Boolean = false,
)

/**
 * Conduce una sesión hablada.
 *
 * Para cada parte mide la pista mínima: arranca con el activador más chico
 * (el final de la parte anterior) y va agregando palabras del comienzo
 * hasta que la parte sale. Corrige siempre en el momento.
 */
class Coach(
    private val voice: VoiceIO,
    private val doc: Doc,
    private val newLimit: Int = 5,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onSave: (Doc) -> Unit,
) {
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui

    private var practiced = 0

    suspend fun run() {
        val start = clock()
        try {
            val plan = Planner.plan(doc, newLimit, clock())
            if (plan.isEmpty) {
                say("No hay nada pendiente por hoy. ¡Bien!")
                return
            }
            say(intro(plan))
            for (i in plan.review) if (!practice(i)) return
            for (i in plan.new) if (!practice(i)) return
            if (plan.new.size > 1) {
                // Encadenado: las partes nuevas de hoy, seguidas, solo con el activador.
                say("Ahora encadenamos lo nuevo, desde el principio.")
                for (i in plan.new) if (!practice(i)) return
            }
            say("Listo por hoy. Practicaste $practiced partes y ${doc.masteredParts} de ${doc.parts.size} ya salen solas.")
        } finally {
            doc.studyMillis += clock() - start
            onSave(doc)
        }
    }

    private fun intro(plan: Planner.Plan): String {
        val bits = mutableListOf<String>()
        if (plan.review.isNotEmpty()) bits += "${plan.review.size} para repasar"
        if (plan.new.isNotEmpty()) bits += "${plan.new.size} nuevas"
        return "Hoy tenés " + bits.joinToString(" y ") + ". Empezamos."
    }

    /** Devuelve false si se pidió terminar la sesión. */
    private suspend fun practice(index: Int): Boolean {
        val part = doc.parts[index]
        val words = part.words
        if (words.isEmpty()) return true
        val stats = doc.stats.getOrPut(index) { PartStats() }
        val isNew = stats.minCue < 0
        show { copy(partLabel = "Parte ${index + 1} de ${doc.parts.size}", heard = "", feedback = "") }

        if (isNew) {
            say("Parte nueva. Escuchá.")
            say(part.text)
        }
        var cue = if (isNew) 1 else 0
        var silences = 0
        val maxCue = words.size - 1

        while (true) {
            val prompt = if (cue == 0) jumpCue(index) else words.take(cue).joinToString(" ")
            show { copy(prompt = prompt) }
            say(prompt)
            val heard = listen()

            when (Commands.parse(heard)) {
                Command.STOP -> return false
                Command.SKIP -> return true
                Command.HINT -> { cue = (cue + 1).coerceAtMost(maxCue); continue }
                Command.REPEAT -> continue
                Command.READ -> { say(part.text); continue }
                null -> Unit
            }

            if (heard.isBlank()) {
                silences++
                if (silences >= 2) {
                    silences = 0
                    cue = (cue + 1).coerceAtMost(maxCue)
                }
                say("No te escuché.")
                continue
            }

            val result = Aligner.align(words.drop(cue), Aligner.tokens(heard))
            stats.attempts++
            if (result.accuracy >= PASS) {
                record(stats, cue, result.accuracy)
                val error = result.firstErrorPhrase()
                say(if (error == null) "Perfecto." else "Bien. $error.")
                return true
            }

            say(result.firstErrorPhrase() ?: "Otra vez.")
            if (cue >= maxCue) {
                say("Escuchala entera.")
                say(part.text)
                record(stats, words.size, result.accuracy)
                return true
            }
            // La pista crece hasta la palabra donde se trabó, así no se repite lo que ya salía.
            cue = (cue + result.correctPrefix + 1).coerceAtMost(maxCue)
        }
    }

    /** El activador de nivel cero: el final de la parte anterior, o el comienzo de la sección. */
    private fun jumpCue(index: Int): String {
        val part = doc.parts[index]
        val previous = doc.parts.getOrNull(index - 1)
        return if (previous == null || previous.section != part.section) {
            "Sección ${part.section + 1}, desde el comienzo."
        } else {
            "Seguí después de: " + previous.words.takeLast(4).joinToString(" ")
        }
    }

    private fun record(stats: PartStats, cue: Int, accuracy: Double) {
        stats.reviews = if (cue == 0) stats.reviews + 1 else 0
        stats.minCue = cue
        stats.lastAccuracy = accuracy
        stats.lastSeen = clock()
        practiced++
        show { copy(feedback = if (cue == 0) "Salió sola" else "Pista: $cue palabras") }
        onSave(doc)
    }

    private suspend fun say(text: String) {
        show { copy(feedback = text) }
        voice.speak(text)
    }

    private suspend fun listen(): String {
        show { copy(listening = true) }
        val heard = voice.listen()
        show { copy(listening = false, heard = heard) }
        return heard
    }

    private inline fun show(change: SessionUi.() -> SessionUi) {
        _ui.value = _ui.value.change()
    }

    companion object {
        const val PASS = 0.9
    }
}
