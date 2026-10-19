package app.memoria

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.memoria.core.TextCleanup
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Lee el texto de un archivo elegido o compartido: PDF o texto plano. */
object Importer {
    data class Imported(val title: String, val text: String)

    class NoTextException : Exception()

    suspend fun read(context: Context, uri: Uri): Imported = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(context, uri)
        val isPdf = resolver.getType(uri) == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        val text = if (isPdf) {
            PDFBoxResourceLoader.init(context.applicationContext)
            val raw = resolver.openInputStream(uri)!!.use { input ->
                PDDocument.load(input).use { PDFTextStripper().getText(it) }
            }
            TextCleanup.fromPdf(raw)
        } else {
            decode(resolver.openInputStream(uri)!!.use { it.readBytes() })
        }
        // Un PDF escaneado son solo imágenes: no trae texto para leer.
        if (text.isBlank()) throw NoTextException()
        Imported(name.substringBeforeLast('.'), text)
    }

    /** UTF-8 si es válido; si no, Latin-1, que es como suelen venir los .txt viejos en Windows. */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charsets.ISO_8859_1)
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Texto"
}
