package app.fototin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.WidgetSource
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Presentación: las fotos pasan solas, con un fundido entre una y otra y un zoom lento hacia el
 * punto de foco de cada una (el efecto "Ken Burns"). La usan la pantalla de presentación y el
 * protector de pantalla.
 *
 * @param paused si está en pausa, la foto actual se queda quieta.
 */
@Composable
fun Slideshow(
    photos: List<Photo>,
    source: WidgetSource,
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    intervalMs: Long = 6_000,
    paused: Boolean = false,
    showCaption: Boolean = true,
) {
    if (photos.isEmpty()) return
    var index by remember(photos) { mutableIntStateOf(startIndex.coerceIn(photos.indices)) }
    LaunchedEffect(photos, paused, index) {
        if (paused) return@LaunchedEffect
        delay(intervalMs)
        index = (index + 1) % photos.size
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = photos[index],
            transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) },
            label = "slideshow",
        ) { photo ->
            // Cada foto arranca sin zoom y se acerca despacio a la parte importante.
            val zoom = remember(photo.id) { Animatable(1f) }
            LaunchedEffect(photo.id, paused) {
                if (!paused) zoom.animateTo(1.12f, tween((intervalMs + 1500).toInt(), easing = LinearEasing))
            }
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photo.file,
                    contentDescription = "Foto del ${PhotoPool.formatDate(photo.takenAt)}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                            transformOrigin = TransformOrigin(photo.focusX, photo.focusY)
                        },
                )
                val caption = PhotoPool.caption(photo, source, showText = true)
                if (showCaption && caption != null) {
                    Text(
                        caption,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(bottom = 32.dp)
                            .background(Color(0x66000000), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
