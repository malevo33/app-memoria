package app.memoria.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlignerTest {
    @Test
    fun ignoraTildesMayusculasYPuntuacion() {
        val r = Aligner.align("Cuando la noche cayó, él habló.", "cuando la noche cayo el hablo")
        assertEquals(1.0, r.accuracy, 0.0)
        assertNull(r.firstErrorPhrase())
    }

    @Test
    fun detectaPalabrasFaltantes() {
        val r = Aligner.align("y sin embargo se mueve", "y se mueve")
        assertEquals("Te faltó: sin embargo", r.firstErrorPhrase())
        assertEquals(1, r.correctPrefix)
    }

    @Test
    fun detectaPalabraCambiada() {
        val r = Aligner.align("el perro negro corre", "el gato negro corre")
        assertEquals("Dijiste gato, era perro", r.firstErrorPhrase())
        assertEquals(0.75, r.accuracy, 1e-9)
    }

    @Test
    fun loQueSigueDespuesDelFinalNoEsError() {
        val r = Aligner.align("hola mundo", "hola mundo y la parte siguiente")
        assertEquals(1.0, r.accuracy, 0.0)
    }

    @Test
    fun silencioEsCeroPorCiento() {
        assertEquals(0.0, Aligner.align("hola mundo", "").accuracy, 0.0)
    }
}
