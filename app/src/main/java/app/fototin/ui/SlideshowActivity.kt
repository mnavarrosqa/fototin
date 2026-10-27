package app.fototin.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.fototin.data.Photo
import app.fototin.data.PhotoRepository
import app.fototin.data.WidgetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Presentación a pantalla completa de un grupo de fotos. Tocar muestra los controles. */
class SlideshowActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Pantalla encendida y sin barras del sistema mientras dura la presentación.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val source = WidgetSource.from(intent.getStringExtra(EXTRA_SOURCE))

        setContent {
            FototinTheme {
                val photos by produceState<List<Photo>?>(null) {
                    value = withContext(Dispatchers.IO) {
                        val byId = PhotoRepository(this@SlideshowActivity).list().associateBy { it.id }
                        ids.mapNotNull { byId[it] }
                    }
                }
                photos?.let { list ->
                    if (list.isEmpty()) LaunchedEffect(Unit) { finish() } else SlideshowScreen(list, source)
                }
            }
        }
    }

    @Composable
    private fun SlideshowScreen(photos: List<Photo>, source: WidgetSource) {
        var paused by remember { mutableStateOf(false) }
        var controls by remember { mutableStateOf(true) }
        // Los controles se esconden solos a los pocos segundos.
        LaunchedEffect(controls, paused) {
            if (controls && !paused) {
                delay(3_000)
                controls = false
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
        ) {
            Slideshow(photos, source, paused = paused)
            AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    IconButton(
                        onClick = ::finish,
                        modifier = Modifier.align(Alignment.TopStart).background(Color(0x66000000), CircleShape),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        FilledTonalButton(onClick = { paused = !paused }) {
                            if (paused) Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(if (paused) "Seguir" else "Pausa")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_SOURCE = "source"

        fun intent(context: Context, photos: List<Photo>, source: WidgetSource = WidgetSource.All): Intent =
            Intent(context, SlideshowActivity::class.java)
                .putStringArrayListExtra(EXTRA_IDS, ArrayList(photos.map { it.id }))
                .putExtra(EXTRA_SOURCE, source.key)
    }
}
