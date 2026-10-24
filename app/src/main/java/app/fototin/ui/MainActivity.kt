package app.fototin.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import app.fototin.config.WidgetConfigActivity
import app.fototin.widget.CollageWidgetReceiver
import app.fototin.widget.FotoWidgetReceiver
import app.fototin.widget.RotationScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // El selector de fotos del sistema muestra la galería y, si el usuario lo tiene activado,
    // también sus fotos de Google Fotos. No requiere permisos de almacenamiento.
    private val pickPhotos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris -> viewModel.import(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RotationScheduler.schedule(this)
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            FototinTheme {
                MainScreen(
                    viewModel = viewModel,
                    onPickPhotos = {
                        pickPhotos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onAddWidget = ::requestPinWidget,
                    onEditWidget = { id ->
                        startActivity(
                            Intent(this@MainActivity, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver (por ejemplo, después de agregar o editar un widget), mostrar los widgets al día.
        viewModel.refreshWidgets()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Fotos recibidas con "Compartir" desde Google Fotos u otra galería. */
    private fun handleShare(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        viewModel.import(uris)
    }

    private fun requestPinWidget(collage: Boolean) {
        val manager = AppWidgetManager.getInstance(this)
        val receiver = if (collage) CollageWidgetReceiver::class.java else FotoWidgetReceiver::class.java
        if (manager.isRequestPinAppWidgetSupported) {
            // El launcher muestra su diálogo y luego abre la configuración del widget.
            manager.requestPinAppWidget(ComponentName(this, receiver), null, null)
        } else {
            Toast.makeText(
                this,
                "Mantené presionada la pantalla de inicio → Widgets → Fototin",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        private const val MAX_PICK = 100
    }
}
