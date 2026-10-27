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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import app.fototin.config.WidgetConfigActivity
import app.fototin.data.GooglePhotosPicker
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::backup) }

    private val openBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restore) }

    // Si Google necesita mostrar la pantalla de permiso, vuelve por acá con el resultado.
    private val googleAuth = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data) }
            .onSuccess { auth -> auth.accessToken?.let(viewModel::pickFromGooglePhotos) }
            .onFailure { viewModel.showMessage("No se pudo conectar con Google Fotos") }
    }

    /** Pide permiso para leer las fotos que el usuario elija en Google Fotos y arranca la elección. */
    private fun pickFromGooglePhotos() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GooglePhotosPicker.SCOPE)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    googleAuth.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    result.accessToken?.let(viewModel::pickFromGooglePhotos)
                }
            }
            .addOnFailureListener { e ->
                viewModel.showMessage("No se pudo conectar con Google: ${e.message ?: "error desconocido"}")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RotationScheduler.schedule(this)
        if (savedInstanceState == null) handleShare(intent)
        lifecycleScope.launch {
            viewModel.openUrl.collect { url ->
                // Si está instalada, el enlace abre la app de Google Fotos; si no, el navegador.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { viewModel.showMessage("No se pudo abrir Google Fotos") }
            }
        }

        setContent {
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            FototinTheme(dynamicColor = dynamicColor) {
                MainScreen(
                    viewModel = viewModel,
                    onPickPhotos = {
                        pickPhotos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onPickGooglePhotos = ::pickFromGooglePhotos,
                    onAddWidget = ::requestPinWidget,
                    onEditWidget = { id ->
                        startActivity(
                            Intent(this@MainActivity, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        )
                    },
                    onBackup = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        createBackup.launch("fototin-$date.zip")
                    },
                    onRestore = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
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
