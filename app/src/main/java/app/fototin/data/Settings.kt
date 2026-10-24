package app.fototin.data

import android.content.Context

/** Ajustes globales de rotación de fotos, compartidos por todos los widgets. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Cada cuántos minutos cambia la foto. 0 = solo al tocar el widget. */
    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value).apply()

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    companion object {
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_SHUFFLE = "shuffle"

        /** WorkManager no permite intervalos periódicos menores a 15 minutos. */
        val INTERVAL_OPTIONS = listOf(0, 15, 60, 360, 1440)
    }
}
