package app.memoria.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeVoice(private val answers: ArrayDeque<String>) : VoiceIO {
    val spoken = mutableListOf<String>()
    override suspend fun speak(text: String) { spoken += text }
    override suspend fun listen(): String = answers.removeFirstOrNull() ?: "terminar"
}

class CoachTest {
    private val text = "uno dos tres cuatro cinco seis siete ocho nueve diez."

    @Test
    fun parteNuevaQueSaleConLaPrimeraPalabra() = runBlocking {
        val doc = Doc("d", "t", text, 0)
        // Pista "uno": recita el resto bien. Luego terminar.
        val voice = FakeVoice(ArrayDeque(listOf("dos tres cuatro cinco seis siete ocho nueve diez")))
        Coach(voice, doc, onSave = {}).run()
        assertEquals(1, doc.stats[0]?.minCue)
    }

    @Test
    fun laPistaCreceHastaDondeSeTrabo() = runBlocking {
        val doc = Doc("d", "t", text, 0)
        val voice = FakeVoice(
            ArrayDeque(
                listOf(
                    "dos tres cuatro gato", // se traba en "cinco": la pista pasa a 5 palabras
                    "seis siete ocho nueve diez",
                )
            )
        )
        Coach(voice, doc, onSave = {}).run()
        assertEquals(5, doc.stats[0]?.minCue)
    }

    @Test
    fun repasoSinPistaConElActivadorDeSeccion() = runBlocking {
        val doc = Doc("d", "t", text, 0)
        doc.stats[0] = PartStats(minCue = 3)
        val voice = FakeVoice(ArrayDeque(listOf("uno dos tres cuatro cinco seis siete ocho nueve diez")))
        Coach(voice, doc, onSave = {}).run()
        assertEquals(0, doc.stats[0]?.minCue)
        assertEquals(1, doc.stats[0]?.reviews)
    }
}
