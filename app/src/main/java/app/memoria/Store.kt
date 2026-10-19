package app.memoria

import android.content.Context
import app.memoria.core.Doc
import app.memoria.core.PartStats
import app.memoria.core.Segmenter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Guarda cada texto y su progreso como un archivo JSON en el teléfono. */
class Store(context: Context) {
    private val dir = File(context.filesDir, "textos").apply { mkdirs() }

    fun list(): List<Doc> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { read(it) }.getOrNull() }
            .sortedByDescending { it.created }

    fun load(id: String): Doc? =
        File(dir, "$id.json").takeIf { it.exists() }?.let { runCatching { read(it) }.getOrNull() }

    fun create(title: String, text: String): Doc =
        Doc(UUID.randomUUID().toString(), title, text, System.currentTimeMillis()).also(::save)

    fun save(doc: Doc) {
        val tmp = File(dir, "${doc.id}.tmp")
        tmp.writeText(toJson(doc).toString())
        tmp.renameTo(File(dir, "${doc.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun toJson(doc: Doc) = JSONObject().apply {
        put("id", doc.id)
        put("title", doc.title)
        put("text", doc.text)
        put("created", doc.created)
        put("studyMillis", doc.studyMillis)
        put("segmenter", Segmenter.VERSION)
        put("stats", JSONArray().apply {
            doc.stats.forEach { (index, s) ->
                put(JSONObject().apply {
                    put("i", index)
                    put("minCue", s.minCue)
                    put("attempts", s.attempts)
                    put("reviews", s.reviews)
                    put("lastAccuracy", s.lastAccuracy)
                    put("lastSeen", s.lastSeen)
                })
            }
        })
    }

    private fun read(file: File): Doc {
        val o = JSONObject(file.readText())
        val doc = Doc(
            id = o.getString("id"),
            title = o.getString("title"),
            text = o.getString("text"),
            created = o.getLong("created"),
            studyMillis = o.optLong("studyMillis"),
        )
        // Si el texto se dividió con otra versión, los índices de las partes ya no coinciden.
        val stats = o.optJSONArray("stats")?.takeIf { o.optInt("segmenter", 1) == Segmenter.VERSION } ?: JSONArray()
        for (k in 0 until stats.length()) {
            val s = stats.getJSONObject(k)
            doc.stats[s.getInt("i")] = PartStats(
                minCue = s.getInt("minCue"),
                attempts = s.optInt("attempts"),
                reviews = s.optInt("reviews"),
                lastAccuracy = s.optDouble("lastAccuracy", 0.0),
                lastSeen = s.optLong("lastSeen"),
            )
        }
        return doc
    }
}
