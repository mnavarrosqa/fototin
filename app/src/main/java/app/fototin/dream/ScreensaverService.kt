package app.fototin.dream

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.Settings
import app.fototin.ui.FototinTheme
import app.fototin.ui.Slideshow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Protector de pantalla de Android ("Daydream"): mientras el teléfono carga, muestra la
 * presentación de fotos como un portarretratos digital.
 *
 * DreamService no trae lo que Compose necesita (ciclo de vida y estado guardado), así que se lo
 * damos acá a mano.
 */
class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        val source = Settings(this).screensaverSource
        setContentView(
            ComposeView(this).apply {
                setContent {
                    FototinTheme {
                        val photos by produceState<List<Photo>>(emptyList()) {
                            value = withContext(Dispatchers.IO) { PhotoPool.resolve(this@ScreensaverService, source).shuffled() }
                        }
                        Slideshow(photos, source, intervalMs = 10_000)
                    }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
