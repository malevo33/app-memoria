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

    /** Minúsculas, sin tildes ni puntuación, para que el reconocedor de voz no cuente como error un acento. */
    fun normalize(word: String): String {
        val decomposed = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    fun align(expected: String, said: String): AlignResult = align(tokens(expected), tokens(said))

    fun align(expectedRaw: List<String>, saidRaw: List<String>): AlignResult {
        val exp = expectedRaw.filter { normalize(it).isNotEmpty() }
        val said = saidRaw.filter { normalize(it).isNotEmpty() }
        val en = exp.map(::normalize)
        val sn = said.map(::normalize)
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
