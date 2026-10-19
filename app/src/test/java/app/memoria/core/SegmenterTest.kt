package app.memoria.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterTest {
    @Test
    fun separaSeccionesPorLineaEnBlanco() {
        val parts = Segmenter.split("Primera sección con varias palabras para llegar al mínimo.\n\nSegunda sección también con varias palabras para el mínimo.")
        assertEquals(2, parts.size)
        assertEquals(0, parts[0].section)
        assertEquals(1, parts[1].section)
    }

    @Test
    fun juntaFrasesCortas() {
        val parts = Segmenter.split("Hola. Qué tal. Todo bien por acá, gracias por preguntar.")
        assertEquals(1, parts.size)
    }

    @Test
    fun cortaFrasesLargasEnPartesParejas() {
        val long = (1..70).joinToString(" ") { "palabra$it" } + "."
        val parts = Segmenter.split(long)
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.words.size <= Segmenter.MAX_WORDS })
        assertEquals(70, parts.sumOf { it.words.size })
    }

    @Test
    fun noPierdePalabras() {
        val text = "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo. " +
            "De los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor."
        assertEquals(Aligner.tokens(text).size, Segmenter.split(text).sumOf { it.words.size })
    }

    @Test
    fun cadaArticuloEsUnaSeccion() {
        val text = "ARTICULO 1°.- Los casos que este Código rige deben ser resueltos. ARTICULO 2°.- La ley debe ser interpretada teniendo en cuenta sus palabras.\nARTICULO 3°.- El juez debe resolver los asuntos que sean sometidos a su jurisdicción."
        val parts = Segmenter.split(text)
        assertEquals(listOf(0, 1, 2), parts.map { it.section }.distinct())
    }
}
