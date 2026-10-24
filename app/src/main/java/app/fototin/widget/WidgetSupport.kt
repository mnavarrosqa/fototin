package app.fototin.widget

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.appwidget.updateAll
import app.fototin.data.Photo
import app.fototin.data.PhotoRepository

/** Claves del estado que guarda cada widget. */
object WidgetKeys {
    /** Foto actual del widget de una foto. */
    val PHOTO_ID = stringPreferencesKey("photo_id")
    /** Fotos actuales del collage, separadas por coma. */
    val PHOTO_IDS = stringPreferencesKey("photo_ids")
    /** Fotos ya mostradas en esta ronda del orden aleatorio. */
    val SEEN = stringSetPreferencesKey("seen")
    /** Clave de [app.fototin.data.WidgetSource]: todas las fotos, recuerdos o un álbum. */
    val SOURCE = stringPreferencesKey("source")
    val STYLE = stringPreferencesKey("style")
    val SHOW_DATE = booleanPreferencesKey("show_date")
    val TAP = stringPreferencesKey("tap")
    /** Cantidad de fotos del collage. */
    val COUNT = intPreferencesKey("count")
}

object WidgetSupport {

    /** Radio de las esquinas de los widgets en el launcher (Android 12+), en dp. */
    fun launcherCornerDp(context: Context, fallback: Float): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback
        val res = context.resources
        return runCatching {
            res.getDimension(android.R.dimen.system_app_widget_background_radius) / res.displayMetrics.density
        }.getOrDefault(fallback)
    }

    fun load(photo: Photo, targetPx: Int): StyledSource? =
        PhotoRepository.decodeForSize(photo.file, targetPx)?.let { StyledSource(it, photo.focusX, photo.focusY) }

    /** Redibuja todos los widgets de la app, por ejemplo después de cambiar las fotos. */
    suspend fun updateAll(context: Context) {
        FotoWidget().updateAll(context)
        CollageWidget().updateAll(context)
    }
}
