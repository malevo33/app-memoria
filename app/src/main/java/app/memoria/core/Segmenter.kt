package app.memoria.core

/** Una micro-parte del texto: la unidad que se memoriza y que tiene su propio activador. */
data class Part(val index: Int, val section: Int, val text: String) {
    val words: List<String> = Aligner.tokens(text)
}

/**
 * Divide el texto en secciones (párrafos separados por una línea en blanco)
 * y cada sección en micro-partes de una o dos frases.
 */
object Segmenter {
    const val MIN_WORDS = 8
    const val MAX_WORDS = 30

    private val sentence = Regex("[^.!?;:…]+[.!?;:…]*[\"»”’)]*")

    fun split(text: String): List<Part> {
        val sections = text.replace("\r\n", "\n")
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val parts = mutableListOf<Part>()
        sections.forEachIndexed { s, sectionText ->
            val flat = sectionText.replace(Regex("\\s+"), " ")
            val pieces = mutableListOf<List<String>>()
            for (match in sentence.findAll(flat)) {
                val words = Aligner.tokens(match.value)
                if (words.isEmpty()) continue
                // Las frases largas se cortan en trozos parejos de hasta MAX_WORDS palabras.
                val n = (words.size + MAX_WORDS - 1) / MAX_WORDS
                val size = (words.size + n - 1) / n
                pieces += words.chunked(size)
            }
            // Las frases cortas se juntan con la vecina para no tener partes de dos palabras.
            val merged = mutableListOf<MutableList<String>>()
            for (piece in pieces) {
                val last = merged.lastOrNull()
                if (last != null && (last.size < MIN_WORDS || piece.size < MIN_WORDS) &&
                    last.size + piece.size <= MAX_WORDS
                ) {
                    last += piece
                } else {
                    merged += piece.toMutableList()
                }
            }
            merged.forEach { parts += Part(parts.size, s, it.joinToString(" ")) }
        }
        return parts
    }
}
