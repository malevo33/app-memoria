package app.memoria.core

import java.text.Normalizer

enum class Op { OK, MISSING, WRONG, EXTRA }

data class Step(val op: Op, val expected: String?, val said: String?)

data class AlignResult(val steps: List<Step>, val expectedCount: Int) {
    val errors: Int get() = steps.count { it.op != Op.OK }

    val accuracy: Double
        get() = if (expectedCount == 0) 1.0
        else (expectedCount - errors).coerceAtLeast(0).toDouble() / expectedCount

    /** Cuántas palabras esperadas se dijeron bien antes del primer error. */
    val correctPrefix: Int
        get() {
            var count = 0
            for (step in steps) {
                if (step.op != Op.OK) break
                count++
            }
            return count
        }

    /** El primer error en palabras, listo para decirlo en voz alta. */
    fun firstErrorPhrase(): String? {
        val start = steps.indexOfFirst { it.op != Op.OK }
        if (start < 0) return null
        val op = steps[start].op
        val run = steps.drop(start).takeWhile { it.op == op }.take(5)
        return when (op) {
            Op.MISSING -> "Te faltó: " + run.joinToString(" ") { it.expected.orEmpty() }
            Op.WRONG -> "Dijiste " + run.joinToString(" ") { it.said.orEmpty() } +
                ", era " + run.joinToString(" ") { it.expected.orEmpty() }
            Op.EXTRA -> "Sobró: " + run.joinToString(" ") { it.said.orEmpty() }
            Op.OK -> null
        }
    }
}

/** Compara palabra por palabra lo que se dijo contra el texto original. */
object Aligner {
    fun tokens(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotBlank() }

    /**
     * Minúsculas, sin tildes ni puntuación, y los números en cifras, para que no cuente como error
     * un acento o que el reconocedor escriba "1" donde el texto dice "primero" (o al revés).
     */
    fun normalize(word: String): String {
        val decomposed = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
        val plain = decomposed.replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        numberWords[plain]?.let { return it.toString() }
        ordinalSuffix.matchEntire(plain)?.let { return it.groupValues[1] }
        return plain
    }

    private val ordinalSuffix = Regex("(\\d+)(ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma|o|a)")

    private val numberWords: Map<String, Int> = buildMap {
        put("cero", 0)
        listOf("un", "uno", "una", "primero", "primera", "primer").forEach { put(it, 1) }
        listOf("dos", "segundo", "segunda").forEach { put(it, 2) }
        listOf("tres", "tercero", "tercera", "tercer").forEach { put(it, 3) }
        listOf("cuatro", "cuarto", "cuarta").forEach { put(it, 4) }
        listOf("cinco", "quinto", "quinta").forEach { put(it, 5) }
        listOf("seis", "sexto", "sexta").forEach { put(it, 6) }
        listOf("siete", "septimo", "septima", "setimo", "setima").forEach { put(it, 7) }
        listOf("ocho", "octavo", "octava").forEach { put(it, 8) }
        listOf("nueve", "noveno", "novena").forEach { put(it, 9) }
        listOf("diez", "decimo", "decima").forEach { put(it, 10) }
        listOf(
            "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve", "veinte",
            "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete",
            "veintiocho", "veintinueve",
        ).forEachIndexed { i, w -> put(w, 11 + i) }
        listOf("treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
            .forEachIndexed { i, w -> put(w, 30 + 10 * i) }
        listOf("cien", "ciento").forEach { put(it, 100) }
        put("mil", 1000)
    }

    /** Junta "treinta y dos" en "32", igual que lo escribe el reconocedor. */
    private fun joinCompoundNumbers(words: List<String>, normalized: List<String>): Pair<List<String>, List<String>> {
        val outRaw = mutableListOf<String>()
        val outNorm = mutableListOf<String>()
        var i = 0
        while (i < normalized.size) {
            val tens = normalized[i].toIntOrNull()
            val unit = normalized.getOrNull(i + 2)?.toIntOrNull()
            if (tens != null && tens in 30..90 && tens % 10 == 0 && normalized.getOrNull(i + 1) == "y" &&
                unit != null && unit in 1..9
            ) {
                outRaw += words.subList(i, i + 3).joinToString(" ")
                outNorm += (tens + unit).toString()
                i += 3
            } else {
                outRaw += words[i]
                outNorm += normalized[i]
                i++
            }
        }
        return outRaw to outNorm
    }

    fun align(expected: String, said: String): AlignResult = align(tokens(expected), tokens(said))

    fun align(expectedRaw: List<String>, saidRaw: List<String>): AlignResult {
        val (exp, en) = expectedRaw.filter { normalize(it).isNotEmpty() }.let { joinCompoundNumbers(it, it.map(::normalize)) }
        val (said, sn) = saidRaw.filter { normalize(it).isNotEmpty() }.let { joinCompoundNumbers(it, it.map(::normalize)) }
        val n = en.size
        val m = sn.size

        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (en[i - 1] == sn[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j - 1] + cost, d[i - 1][j] + 1, d[i][j - 1] + 1)
        }

        val steps = ArrayDeque<Step>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val same = i > 0 && j > 0 && en[i - 1] == sn[j - 1]
            if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + (if (same) 0 else 1)) {
                steps.addFirst(Step(if (same) Op.OK else Op.WRONG, exp[i - 1], said[j - 1]))
                i--; j--
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                steps.addFirst(Step(Op.MISSING, exp[i - 1], null))
                i--
            } else {
                steps.addFirst(Step(Op.EXTRA, null, said[j - 1]))
                j--
            }
        }
        // Si siguió recitando la parte siguiente, lo que sobra al final no es un error.
        return AlignResult(steps.toList().dropLastWhile { it.op == Op.EXTRA }, n)
    }
}
