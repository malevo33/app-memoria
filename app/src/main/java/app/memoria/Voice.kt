package app.memoria

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.memoria.core.Spoken
import app.memoria.core.VoiceIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import android.speech.tts.Voice as TtsVoice

/** Voz del teléfono: síntesis y reconocimiento de Android, en español. */
class Voice(context: Context) : VoiceIO, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val ready = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(appContext, this)
    private var recognizer: SpeechRecognizer? = null

    private val locale: Locale =
        Locale.getDefault().takeIf { it.language == "es" } ?: Locale.forLanguageTag("es-ES")

    private val prefs = Prefs(appContext)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = locale
            applyPrefs()
            ready.complete(true)
        } else {
            ready.complete(false)
        }
    }

    /** Aplica la voz y la velocidad elegidas; sin elección, la mejor voz en español instalada. */
    fun applyPrefs() {
        val voices = spanishVoices()
        val chosen = voices.firstOrNull { it.name == prefs.voiceName } ?: voices.firstOrNull()
        if (chosen != null) tts.voice = chosen
        tts.setSpeechRate(prefs.speechRate)
    }

    /** Voces en español instaladas, primero las argentinas y después las de mejor calidad. */
    fun spanishVoices(): List<TtsVoice> =
        (runCatching { tts.voices }.getOrNull() ?: emptySet())
            .filter { it.locale.language == "es" }
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
            .sortedByDescending { score(it) }

    private fun score(v: TtsVoice): Int {
        val country = when (v.locale.country) {
            "AR" -> 2000
            "UY" -> 1500
            "ES", "" -> 0
            else -> 1000 // el resto de Latinoamérica suena más cerca que España
        }
        return country + v.quality
    }

    suspend fun awaitReady(): Boolean = ready.await()

    override suspend fun speak(text: String) {
        if (!ready.await()) return
        val spoken = Spoken.forSpeech(text)
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
            })
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
            cont.invokeOnCancellation { tts.stop() }
        }
    }

    override suspend fun listen(): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (cont.isActive) cont.resume(text.orEmpty())
                }

                override fun onError(error: Int) {
                    if (cont.isActive) cont.resume("")
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Deja pausas largas para recitar sin que corte (algunos teléfonos lo ignoran).
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            }
            r.startListening(intent)
            cont.invokeOnCancellation { main.post { r.cancel() } }
        }
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        main.post {
            recognizer?.destroy()
            recognizer = null
        }
    }
}
