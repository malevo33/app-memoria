package app.memoria

import android.content.Context

/** Preferencias de la voz que lee los textos. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var voiceName: String?
        get() = sp.getString("voz", null)
        set(value) = sp.edit().putString("voz", value).apply()

    var speechRate: Float
        get() = sp.getFloat("velocidad", 1.0f)
        set(value) = sp.edit().putFloat("velocidad", value).apply()
}
