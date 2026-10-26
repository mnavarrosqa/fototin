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

    /** Ordenar la grilla por fecha de la foto (true) o por fecha en que se agregó (false). */
    var sortByTaken: Boolean
        get() = prefs.getBoolean(KEY_SORT_TAKEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SORT_TAKEN, value).apply()

    /** Tamaño de las fotos en la grilla: 0 chicas, 1 medianas, 2 grandes. */
    var gridSize: Int
        get() = prefs.getInt(KEY_GRID_SIZE, 1)
        set(value) = prefs.edit().putInt(KEY_GRID_SIZE, value).apply()

    /** Usar los colores del fondo de pantalla (Material You, Android 12+). */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    companion object {
        private const val KEY_GRID_SIZE = "grid_size"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_SORT_TAKEN = "sort_taken"

        /** WorkManager no permite intervalos periódicos menores a 15 minutos. */
        val INTERVAL_OPTIONS = listOf(0, 15, 60, 360, 1440)
    }
}
