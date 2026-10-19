package app.memoria.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTest {
    @Test
    fun articuloConOrdinal() {
        assertEquals("artículo primero. (texto).", Spoken.forSpeech("ARTICULO 1°. (TEXTO)."))
        assertEquals("artículo décimo", Spoken.forSpeech("Artículo 10º"))
    }

    @Test
    fun articulosDeDosCifrasQuedanEnCardinal() {
        assertEquals("artículo 25.", Spoken.forSpeech("ARTÍCULO 25°."))
    }

    @Test
    fun abreviaturas() {
        assertEquals("artículo 5, inciso a) y ley número 340", Spoken.forSpeech("Art. 5, inc. a) y ley N° 340"))
        assertEquals("la primera vez", Spoken.forSpeech("la 1ra vez"))
    }

    @Test
    fun noTocaNumerosComunes() {
        assertEquals("tiene 3 hijos y 20 años", Spoken.forSpeech("tiene 3 hijos y 20 años"))
    }
}
