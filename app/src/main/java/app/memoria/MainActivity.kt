package app.memoria

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.memoria.ui.App
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var shared by mutableStateOf<Importer.Imported?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        if (savedInstanceState == null) receive(intent)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    App(store = store, shared = shared, onSharedConsumed = { shared = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** Texto o archivo (PDF, .txt) recibido con "Compartir" o "Abrir con" desde otra app. */
    private fun receive(intent: Intent) {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri == null) {
            val text = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
            if (!text.isNullOrBlank()) shared = Importer.Imported("", text)
            return
        }
        lifecycleScope.launch {
            try {
                shared = Importer.read(this@MainActivity, uri)
            } catch (e: Importer.NoTextException) {
                Toast.makeText(this@MainActivity, "Ese archivo no tiene texto para leer.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "No pude abrir el archivo.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
