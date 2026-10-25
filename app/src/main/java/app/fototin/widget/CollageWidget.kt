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

/** Widget con 2 a 4 fotos a la vez. Al tocarlo cambian todas. */
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
                Collage(collageSelection(pool, prefs[WidgetKeys.PHOTO_IDS], prefs[WidgetKeys.COUNT] ?: DEFAULT_COUNT))
            }
        }
    }

    @Composable
    private fun Collage(photos: List<Photo>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        // La clave incluye la foto entera: si cambia su encuadre, hay que redibujar.
        val bitmap = remember(photos, size) {
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            val sources = photos.mapNotNull { WidgetSupport.load(it, maxOf(w, h) / 2 + 1) }
            val corner = WidgetSupport.launcherCornerDp(context, fallback = 20f)
            PhotoStyler.renderCollage(sources, w, h, density, corner).also {
                sources.forEach { s -> s.bitmap.recycle() }
            }
        }
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Collage de ${photos.size} fotos",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<NextPhotoAction>()),
        )
    }

    companion object {
        const val DEFAULT_COUNT = 3
        val COUNT_OPTIONS = listOf(2, 3, 4)
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
