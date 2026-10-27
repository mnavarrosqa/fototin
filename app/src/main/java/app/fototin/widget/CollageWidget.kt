package app.fototin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.WidgetSource
import app.fototin.data.collageSelection
import app.fototin.ui.MainActivity
import app.fototin.ui.PhotoViewerActivity

/** Widget con 2 a 6 fotos a la vez, en distintos diseños. */
class CollageWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val source = WidgetSource.from(prefs[WidgetKeys.SOURCE])
            val pool = PhotoPool.resolve(context, source)
            if (pool.isEmpty()) {
                EmptyWidget(if (source is WidgetSource.FromAlbum) "Este álbum está vacío" else "Elegí tus fotos")
            } else {
                val style = CollageStyle.from(prefs[WidgetKeys.COLLAGE_STYLE])
                val photos = collageSelection(pool, prefs[WidgetKeys.PHOTO_IDS], style.clampCount(prefs[WidgetKeys.COUNT] ?: DEFAULT_COUNT))
                val captions = photos.map { PhotoPool.caption(it, source, prefs[WidgetKeys.SHOW_DATE] ?: false) }
                val tap = when (TapAction.from(prefs[WidgetKeys.TAP])) {
                    TapAction.NEXT -> actionRunCallback<NextPhotoAction>()
                    TapAction.VIEW -> actionStartActivity<PhotoViewerActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>(PhotoViewerActivity.EXTRA_PHOTO_ID) to photos.first().id,
                            ActionParameters.Key<String>(PhotoViewerActivity.EXTRA_SOURCE) to source.key,
                        ),
                    )
                    TapAction.OPEN_APP -> actionStartActivity<MainActivity>()
                }
                Collage(photos, style, captions, tap)
            }
        }
    }

    @Composable
    private fun Collage(photos: List<Photo>, style: CollageStyle, captions: List<String?>, tap: Action) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        // La clave incluye la foto entera: si cambia su encuadre, hay que redibujar.
        val bitmap = remember(photos, style, captions, size) {
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            val sources = photos.mapNotNull { WidgetSupport.load(it, maxOf(w, h) / 2 + 1) }
            val corner = WidgetSupport.launcherCornerDp(context, fallback = 20f)
            CollageStyler.render(sources, style, w, h, density, corner, captions, seedOf(photos)).also {
                sources.forEach { s -> s.bitmap.recycle() }
            }
        }
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Collage de ${photos.size} fotos",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize().clickable(tap),
        )
    }

    companion object {
        const val DEFAULT_COUNT = 3

        /** Las polaroids se inclinan distinto según las fotos, pero siempre igual para las mismas. */
        fun seedOf(photos: List<Photo>): Int = photos.firstOrNull()?.id?.hashCode() ?: 0
    }
}

class CollageWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CollageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RotationScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RotationScheduler.cancelIfUnused(context)
    }
}
