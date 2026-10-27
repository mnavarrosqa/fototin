package app.fototin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.fototin.R
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.WidgetSource
import app.fototin.ui.MainActivity
import app.fototin.ui.PhotoViewerActivity

class FotoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val source = WidgetSource.from(prefs[WidgetKeys.SOURCE])
            val photos = PhotoPool.resolve(context, source)
            if (photos.isEmpty()) {
                EmptyWidget(if (source is WidgetSource.FromAlbum) "Este álbum está vacío" else "Elegí tus fotos")
            } else {
                val photo = photos.firstOrNull { it.id == prefs[WidgetKeys.PHOTO_ID] } ?: photos.first()
                val style = WidgetStyle.from(prefs[WidgetKeys.STYLE])
                val caption = PhotoPool.caption(photo, source, (prefs[WidgetKeys.SHOW_DATE] ?: false) || style.alwaysText)
                val tap = when (TapAction.from(prefs[WidgetKeys.TAP])) {
                    TapAction.NEXT -> actionRunCallback<NextPhotoAction>()
                    TapAction.VIEW -> actionStartActivity<PhotoViewerActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>(PhotoViewerActivity.EXTRA_PHOTO_ID) to photo.id,
                            ActionParameters.Key<String>(PhotoViewerActivity.EXTRA_SOURCE) to source.key,
                        ),
                    )
                    TapAction.OPEN_APP -> actionStartActivity<MainActivity>()
                }
                PhotoContent(photo, style, caption, tap)
            }
        }
    }

    @Composable
    private fun PhotoContent(photo: Photo, style: WidgetStyle, caption: String?, tap: Action) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        // La clave incluye la foto entera: si cambia su encuadre, hay que redibujar.
        // El Calendario muestra la fecha de hoy: al cambiar el día hay que redibujarlo.
        val day = if (style == WidgetStyle.CALENDAR) java.time.LocalDate.now().toEpochDay() else 0L
        val bitmap = remember(photo, style, caption, size, day) {
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            WidgetSupport.load(photo, maxOf(w, h))?.let { source ->
                val corner = WidgetSupport.launcherCornerDp(context, fallback = 28f)
                PhotoStyler.render(source, style, w, h, density, caption, corner).also { source.bitmap.recycle() }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(tap),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Foto del ${PhotoPool.formatDate(photo.takenAt)}",
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Lo que muestran los widgets cuando no tienen fotos: invita a abrir la app. */
@Composable
fun EmptyWidget(message: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(Color(0xFFEEEDFF))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_empty),
            contentDescription = null,
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = message,
            style = TextStyle(
                color = ColorProvider(Color(0xFF2E2A6B)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
