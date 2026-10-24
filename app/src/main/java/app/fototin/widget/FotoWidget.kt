package app.fototin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import app.fototin.data.PhotoRepository
import app.fototin.data.WidgetSource
import app.fototin.ui.MainActivity

class FotoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val source = WidgetSource.from(prefs[KEY_SOURCE])
            val photos = PhotoPool.resolve(context, source)
            if (photos.isEmpty()) {
                EmptyContent(if (source is WidgetSource.FromAlbum) "Este álbum está vacío" else "Elegí tus fotos")
            } else {
                val photo = photos.firstOrNull { it.id == prefs[KEY_PHOTO_ID] } ?: photos.first()
                val caption = PhotoPool.caption(photo, source, prefs[KEY_SHOW_DATE] ?: false)
                PhotoContent(photo, WidgetStyle.from(prefs[KEY_STYLE]), caption)
            }
        }
    }

    @Composable
    private fun PhotoContent(photo: Photo, style: WidgetStyle, caption: String?) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        val bitmap = remember(photo.id, style, caption, size) {
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            PhotoRepository.decodeForSize(photo.file, maxOf(w, h))?.let { source ->
                PhotoStyler.render(source, style, w, h, density, caption).also { source.recycle() }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<NextPhotoAction>()),
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

    @Composable
    private fun EmptyContent(message: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(Color(0xFFFBE3DA))
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
                    color = ColorProvider(Color(0xFF5C2A20)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    companion object {
        val KEY_PHOTO_ID = stringPreferencesKey("photo_id")
        val KEY_SEEN = stringSetPreferencesKey("seen")
        val KEY_STYLE = stringPreferencesKey("style")
        val KEY_SHOW_DATE = booleanPreferencesKey("show_date")
        /** Clave de [WidgetSource]: todas las fotos, recuerdos o un álbum. */
        val KEY_SOURCE = stringPreferencesKey("source")
    }
}
