package app.fototin.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.fototin.data.AlbumRepository
import app.fototin.data.PhotoPool
import app.fototin.data.WidgetSource
import app.fototin.data.collageSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.fototin.data.Photo
import app.fototin.data.PhotoRepository

/** Un widget puesto en la pantalla de inicio, tal como se muestra en la app. */
class PlacedWidget(
    val appWidgetId: Int,
    val glanceId: GlanceId,
    val collage: Boolean,
    val preview: Bitmap?,
    val description: String,
)

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

    /** Widgets puestos en la pantalla de inicio, con una vista previa de lo que muestran ahora. */
    suspend fun placedWidgets(context: Context): List<PlacedWidget> = withContext(Dispatchers.IO) {
        val glance = GlanceAppWidgetManager(context)
        val system = AppWidgetManager.getInstance(context)
        val albums = AlbumRepository(context).list()
        val density = context.resources.displayMetrics.density
        listOf(false, true).flatMap { collage ->
            val type = if (collage) CollageWidget::class.java else FotoWidget::class.java
            glance.getGlanceIds(type).map { glanceId ->
                val appWidgetId = glance.getAppWidgetId(glanceId)
                val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                val source = WidgetSource.from(prefs[WidgetKeys.SOURCE])
                val pool = PhotoPool.resolve(context, source)
                // Proporción real del widget en el launcher (en vertical: ancho mínimo y alto máximo).
                val options = system.getAppWidgetOptions(appWidgetId)
                val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: if (collage) 320 else 160
                val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 160
                val scale = PREVIEW_PX / maxOf(wDp, hDp).toFloat()
                val w = (wDp * scale).toInt()
                val h = (hDp * scale).toInt()
                val preview = if (pool.isEmpty()) {
                    null
                } else if (collage) {
                    val photos = collageSelection(pool, prefs[WidgetKeys.PHOTO_IDS], prefs[WidgetKeys.COUNT] ?: CollageWidget.DEFAULT_COUNT)
                    val sources = photos.mapNotNull { load(it, PREVIEW_PX / 2) }
                    PhotoStyler.renderCollage(sources, w, h, scale, launcherCornerDp(context, 20f))
                        .also { sources.forEach { s -> s.bitmap.recycle() } }
                } else {
                    val photo = pool.firstOrNull { it.id == prefs[WidgetKeys.PHOTO_ID] } ?: pool.first()
                    load(photo, PREVIEW_PX)?.let { src ->
                        val caption = PhotoPool.caption(photo, source, prefs[WidgetKeys.SHOW_DATE] ?: false)
                        PhotoStyler.render(src, WidgetStyle.from(prefs[WidgetKeys.STYLE]), w, h, scale, caption, launcherCornerDp(context, 28f))
                            .also { src.bitmap.recycle() }
                    }
                }
                val sourceName = when (source) {
                    WidgetSource.All -> "Todas"
                    WidgetSource.Memories -> "Recuerdos"
                    WidgetSource.Favorites -> "Favoritas"
                    is WidgetSource.FromAlbum -> albums.firstOrNull { it.id == source.albumId }?.name ?: "Todas"
                }
                val title = if (collage) "Collage" else WidgetStyle.from(prefs[WidgetKeys.STYLE]).label
                PlacedWidget(appWidgetId, glanceId, collage, preview, "$title · $sourceName")
            }
        }
    }

    /** Pasa un widget a la siguiente foto (o fotos, si es un collage). */
    suspend fun advance(context: Context, widget: PlacedWidget) {
        if (widget.collage) advanceCollage(context, widget.glanceId) else advancePhoto(context, widget.glanceId)
    }

    private const val PREVIEW_PX = 360

    /** Redibuja todos los widgets de la app, por ejemplo después de cambiar las fotos. */
    suspend fun updateAll(context: Context) {
        FotoWidget().updateAll(context)
        CollageWidget().updateAll(context)
    }
}
