package app.fototin.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.WidgetSource
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Muestra en grande la foto del widget; deslizando se ven las demás del mismo widget. */
class PhotoViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val photoId = intent.getStringExtra(EXTRA_PHOTO_ID)
        val source = WidgetSource.from(intent.getStringExtra(EXTRA_SOURCE))

        setContent {
            FototinTheme {
                val photos by produceState<List<Photo>?>(null) {
                    value = withContext(Dispatchers.IO) { PhotoPool.resolve(this@PhotoViewerActivity, source) }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    photos?.let { list -> Viewer(list, photoId, source) }
                    IconButton(
                        onClick = ::finish,
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(8.dp)
                            .background(Color(0x66000000), CircleShape),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }
        }
    }

    @Composable
    private fun Viewer(photos: List<Photo>, startId: String?, source: WidgetSource) {
        if (photos.isEmpty()) {
            LaunchedEffect(Unit) { finish() }
            return
        }
        val start = photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = start) { photos.size }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val photo = photos[page]
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photo.file,
                    contentDescription = "Foto del ${PhotoPool.formatDate(photo.takenAt)}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    PhotoPool.caption(photo, source, showDate = true) ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(bottom = 24.dp)
                        .background(Color(0x66000000), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    companion object {
        const val EXTRA_PHOTO_ID = "photo_id"
        const val EXTRA_SOURCE = "source"
    }
}
