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

    /** El intervalo propio más corto entre los widgets (0 si ninguno tiene uno propio). */
    var shortestWidgetInterval: Int
        get() = prefs.getInt(KEY_SHORTEST_WIDGET_INTERVAL, 0)
        set(value) = prefs.edit().putInt(KEY_SHORTEST_WIDGET_INTERVAL, value).apply()

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

    /** De dónde saca las fotos el protector de pantalla (clave de [WidgetSource]). */
    var screensaverSource: WidgetSource
        get() = WidgetSource.from(prefs.getString(KEY_SCREENSAVER_SOURCE, null))
        set(value) = prefs.edit().putString(KEY_SCREENSAVER_SOURCE, value.key).apply()

    /** Avisar a la mañana cuando hay recuerdos de un día como hoy. */
    var memoriesNotification: Boolean
        get() = prefs.getBoolean(KEY_MEMORIES_NOTIFICATION, false)
        set(value) = prefs.edit().putBoolean(KEY_MEMORIES_NOTIFICATION, value).apply()

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

    /** Una elección de Google Fotos en curso, guardada por si Android cierra la app. */
    data class PendingPicker(val sessionId: String, val pickerUri: String, val albumId: String?, val createdAt: Long)

    fun savePendingPicker(pending: PendingPicker) {
        prefs.edit()
            .putString(KEY_PICKER_SESSION, pending.sessionId)
            .putString(KEY_PICKER_URI, pending.pickerUri)
            .putString(KEY_PICKER_ALBUM, pending.albumId)
            .putLong(KEY_PICKER_CREATED, pending.createdAt)
            .apply()
    }

    /** La elección pendiente, si la hay y no venció (Google las da por vencidas en menos de una hora). */
    fun pendingPicker(): PendingPicker? {
        val id = prefs.getString(KEY_PICKER_SESSION, null) ?: return null
        val createdAt = prefs.getLong(KEY_PICKER_CREATED, 0L)
        if (System.currentTimeMillis() - createdAt > 50 * 60 * 1000L) {
            clearPendingPicker()
            return null
        }
        return PendingPicker(id, prefs.getString(KEY_PICKER_URI, null).orEmpty(), prefs.getString(KEY_PICKER_ALBUM, null), createdAt)
    }

    fun clearPendingPicker() {
        prefs.edit().remove(KEY_PICKER_SESSION).remove(KEY_PICKER_URI).remove(KEY_PICKER_ALBUM).remove(KEY_PICKER_CREATED).apply()
    }

    companion object {
        private const val KEY_SHORTEST_WIDGET_INTERVAL = "shortest_widget_interval"
        private const val KEY_SCREENSAVER_SOURCE = "screensaver_source"
        private const val KEY_MEMORIES_NOTIFICATION = "memories_notification"
        private const val KEY_PICKER_SESSION = "picker_session"
        private const val KEY_PICKER_URI = "picker_uri"
        private const val KEY_PICKER_ALBUM = "picker_album"
        private const val KEY_PICKER_CREATED = "picker_created"
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
