package app.memoria.core

enum class Command { STOP, SKIP, HINT, REPEAT, READ }

/** Comandos de voz cortos que se pueden decir en cualquier momento de la sesión. */
object Commands {
    private val table = listOf(
        Command.STOP to setOf("terminar", "termina", "termino", "basta", "pausa", "salir", "chau"),
        Command.SKIP to setOf("siguiente", "saltar", "salta", "proxima"),
        Command.HINT to setOf("pista", "ayuda", "ayudame"),
        Command.REPEAT to setOf("repeti", "repite", "repetir", "repetime", "otra"),
        Command.READ to setOf("lee", "leer", "leemela", "leela"),
    )

    fun parse(heard: String): Command? {
        val words = Aligner.tokens(heard).map(Aligner::normalize).filter { it.isNotEmpty() }
        // Solo frases muy cortas: una palabra del texto no debe disparar un comando.
        if (words.isEmpty() || words.size > 3) return null
        for ((command, keys) in table) {
            if (words.any { it in keys }) return command
        }
        return null
    }
}
