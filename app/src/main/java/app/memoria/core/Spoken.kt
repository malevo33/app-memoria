package app.memoria.core

/**
 * Convierte el texto escrito en lo que hay que decir en voz alta:
 * "ARTICULO 1°" → "artículo primero", "Art. 5" → "artículo 5", "N° 3" → "número 3".
 */
object Spoken {
    private val ordinalsM = listOf("", "primero", "segundo", "tercero", "cuarto", "quinto", "sexto", "séptimo", "octavo", "noveno", "décimo")
    private val ordinalsF = listOf("", "primera", "segunda", "tercera", "cuarta", "quinta", "sexta", "séptima", "octava", "novena", "décima")

    private val numberSign = Regex("\\b[Nn][°º.]\\s*(?=\\d)")
    private val ordinal = Regex("(\\d+)(?:\\s*([°ºª])|(\\.º|\\.ª|ro|ra|do|da|er|to|ta|vo|va|no|na|mo|ma))(?![\\p{L}\\d])")
    private val articleAbbrev = Regex("\\b([Aa]rts?)\\.\\s*(?=\\d)")
    private val articleNoAccent = Regex("\\b[Aa][Rr][Tt][IiÍí][Cc][Uu][Ll][Oo]([Ss]?)\\b")
    private val incisoAbbrev = Regex("\\b[Ii]nc\\.\\s*")
    private val allCaps = Regex("\\b\\p{Lu}{4,}\\b")

    fun forSpeech(text: String): String {
        var s = text
        s = numberSign.replace(s, "número ")
        s = articleAbbrev.replace(s) { if (it.groupValues[1].endsWith("s")) "artículos " else "artículo " }
        s = incisoAbbrev.replace(s, "inciso ")
        s = ordinal.replace(s) { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            val suffix = m.groupValues[2] + m.groupValues[3]
            val feminine = suffix.contains('ª') || suffix.endsWith("a")
            if (n in 1..10) (if (feminine) ordinalsF else ordinalsM)[n] else m.groupValues[1]
        }
        // En mayúsculas algunos motores deletrean o acentúan mal; "ARTICULO" sin tilde suena "articulo".
        s = allCaps.replace(s) { it.value.lowercase() }
        s = articleNoAccent.replace(s) { "artículo" + it.groupValues[1].lowercase() }
        return s
    }
}
