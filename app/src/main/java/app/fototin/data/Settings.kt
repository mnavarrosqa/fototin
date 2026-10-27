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

    /** Buscar versiones nuevas al abrir la app. */
    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATES, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATES, value).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    /**
     * Novedades de la versión que se está instalando, para mostrarlas al abrir la app actualizada.
     * Se guardan antes de instalar porque después la app se cierra.
     */
    fun saveReleaseNotes(versionCode: Int, versionName: String, notes: List<String>) {
        prefs.edit()
            .putInt(KEY_NOTES_VERSION, versionCode)
            .putString(KEY_NOTES_NAME, versionName)
            .putString(KEY_NOTES, notes.joinToString("\n"))
            .apply()
    }

    /** Las novedades guardadas si corresponden a [installedVersionCode]; se muestran una sola vez. */
    fun takeReleaseNotes(installedVersionCode: Int): Pair<String, List<String>>? {
        if (prefs.getInt(KEY_NOTES_VERSION, -1) != installedVersionCode) return null
        val name = prefs.getString(KEY_NOTES_NAME, null) ?: return null
        val notes = prefs.getString(KEY_NOTES, null).orEmpty().lines().filter { it.isNotBlank() }
        prefs.edit().remove(KEY_NOTES_VERSION).remove(KEY_NOTES_NAME).remove(KEY_NOTES).apply()
        return name to notes
    }

    companion object {
        private const val KEY_AUTO_UPDATES = "auto_updates"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_NOTES_VERSION = "notes_version"
        private const val KEY_NOTES_NAME = "notes_name"
        private const val KEY_NOTES = "notes"
        private const val KEY_GRID_SIZE = "grid_size"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_SORT_TAKEN = "sort_taken"

        /** WorkManager no permite intervalos periódicos menores a 15 minutos. */
        val INTERVAL_OPTIONS = listOf(0, 15, 60, 360, 1440)
    }
}
