package app.memoria.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCleanupTest {
    @Test
    fun uneLineasYQuitaGuionesYNumerosDePagina() {
        val raw = """
            ARTICULO 1°.- Los casos que este Código rige deben ser resueltos según las leyes que resulten apli-
            cables, conforme con la Constitución Nacional y los tratados de derechos humanos.
            12
            ARTICULO 2°.- La ley debe ser interpretada teniendo en cuenta sus palabras.
        """.trimIndent()
        assertEquals(
            "ARTICULO 1°.- Los casos que este Código rige deben ser resueltos según las leyes que resulten aplicables, " +
                "conforme con la Constitución Nacional y los tratados de derechos humanos.\n\n" +
                "ARTICULO 2°.- La ley debe ser interpretada teniendo en cuenta sus palabras.",
            TextCleanup.fromPdf(raw),
        )
    }
}
