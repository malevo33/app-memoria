package app.memoria.core

/**
 * Estado de memoria de una micro-parte.
 *
 * [minCue] es la pista mínima medida la última vez:
 * -1 = nunca practicada; 0 = sale sola con el final de la parte anterior;
 * k = necesitó las primeras k palabras.
 */
data class PartStats(
    var minCue: Int = -1,
    var attempts: Int = 0,
    var reviews: Int = 0,
    var lastAccuracy: Double = 0.0,
    var lastSeen: Long = 0L,
)

class Doc(
    val id: String,
    var title: String,
    val text: String,
    val created: Long,
    val stats: MutableMap<Int, PartStats> = mutableMapOf(),
    var studyMillis: Long = 0L,
) {
    val parts: List<Part> = Segmenter.split(text)

    fun stat(index: Int): PartStats? = stats[index]

    val masteredParts: Int get() = parts.count { stats[it.index]?.minCue == 0 }
    val totalWords: Int get() = parts.sumOf { it.words.size }
    val masteredWords: Int get() = parts.filter { stats[it.index]?.minCue == 0 }.sumOf { it.words.size }

    /** Minutos de estudio por cada 100 palabras que ya salen solas: la métrica de velocidad. */
    val minutesPer100Words: Double?
        get() = if (masteredWords == 0) null else studyMillis / 60000.0 / (masteredWords / 100.0)
}

/** Decide qué partes se practican en una sesión. */
object Planner {
    private const val DAY = 24L * 60 * 60 * 1000

    /** Días hasta el próximo repaso de una parte que ya sale sola; se duplica con cada repaso. */
    fun intervalMillis(reviews: Int): Long {
        val days = 1L shl (reviews - 1).coerceIn(0, 5)
        return days * DAY
    }

    data class Plan(val review: List<Int>, val new: List<Int>) {
        val isEmpty get() = review.isEmpty() && new.isEmpty()
    }

    fun plan(doc: Doc, newLimit: Int, now: Long = System.currentTimeMillis()): Plan {
        val review = doc.parts.map { it.index }.filter { i ->
            val s = doc.stats[i] ?: return@filter false
            when {
                s.minCue < 0 -> false
                s.minCue > 0 -> true // todavía necesita pista: va siempre
                else -> now - s.lastSeen >= intervalMillis(s.reviews)
            }
        }
        val new = doc.parts.map { it.index }.filter { (doc.stats[it]?.minCue ?: -1) < 0 }.take(newLimit)
        return Plan(review, new)
    }
}
