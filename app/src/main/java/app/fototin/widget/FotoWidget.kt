package app.fototin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.fototin.data.PhotoRepository
import app.fototin.ui.MainActivity
import java.io.File

class FotoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = PhotoRepository(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val photos = repository.list()
            if (photos.isEmpty()) {
                EmptyContent()
            } else {
                val index = (prefs[KEY_INDEX] ?: 0).mod(photos.size)
                PhotoContent(photos[index], WidgetStyle.from(prefs[KEY_STYLE]))
            }
        }
    }

    @Composable
    private fun PhotoContent(file: File, style: WidgetStyle) {
        val context = LocalContext.current
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        val bitmap = remember(file, style, size) {
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            PhotoRepository.decodeForSize(file, maxOf(w, h))?.let { source ->
                PhotoStyler.render(source, style, w, h, density).also { source.recycle() }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<NextPhotoAction>()),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Foto",
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
        }
    }

    @Composable
    private fun EmptyContent() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(Color(0xFFF3B4A6))
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Tocá para elegir tus fotos",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF3A2A26)),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    companion object {
        val KEY_INDEX = intPreferencesKey("index")
        val KEY_STYLE = stringPreferencesKey("style")
    }
}
