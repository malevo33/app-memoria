package app.memoria.core

/** Arregla el texto que sale de un PDF: líneas cortadas, guiones de corte y números de página. */
object TextCleanup {
    private val pageNumber = Regex("^\\s*(?:-\\s*)?\\d{1,4}(?:\\s*-)?\\s*$")
    private val articleLine = Regex("^\\s*(?:ART[IÍ]CULO|Art[ií]culo|ARTS?\\.|Arts?\\.)\\s*\\d")

    fun fromPdf(raw: String): String {
        val lines = raw.replace("\r", "").lines().map { it.trimEnd() }.filterNot { pageNumber.matches(it) }
        val lengths = lines.filter { it.isNotBlank() }.map { it.length }.sorted()
        // Una línea bastante más corta que las normales suele ser el final de un párrafo.
        val fullLine = if (lengths.isEmpty()) 0 else lengths[(lengths.size * 0.9).toInt().coerceAtMost(lengths.size - 1)]

        val out = StringBuilder()
        var previous = ""
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append("\n\n")
                previous = ""
                continue
            }
            when {
                out.isEmpty() || out.endsWith("\n\n") -> Unit
                articleLine.containsMatchIn(trimmed) -> out.append("\n\n")
                previous.endsWith("-") && trimmed.first().isLowerCase() -> out.setLength(out.length - 1)
                previous.last() in ".:;" && previous.length < fullLine * 0.8 -> out.append("\n\n")
                else -> out.append(' ')
            }
            out.append(trimmed)
            previous = trimmed
        }
        return out.toString().trim()
    }
}
