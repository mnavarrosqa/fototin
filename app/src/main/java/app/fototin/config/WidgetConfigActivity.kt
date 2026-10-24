package app.fototin.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.fototin.data.Album
import app.fototin.data.AlbumRepository
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.PhotoRepository
import app.fototin.ui.FototinTheme
import app.fototin.widget.FotoWidget
import app.fototin.widget.PhotoStyler
import app.fototin.widget.WidgetStyle
import app.fototin.data.WidgetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Se abre al agregar (o reconfigurar) un widget para elegir su estilo. */
class WidgetConfigActivity : ComponentActivity() {

    private val appWidgetId by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            val albums = withContext(Dispatchers.IO) { AlbumRepository(this@WidgetConfigActivity).list() }

            setContent {
                FototinTheme {
                    ConfigScreen(
                        albums = albums,
                        currentPhotoId = prefs[FotoWidget.KEY_PHOTO_ID],
                        initialSource = WidgetSource.from(prefs[FotoWidget.KEY_SOURCE]),
                        initialStyle = WidgetStyle.from(prefs[FotoWidget.KEY_STYLE]),
                        initialShowDate = prefs[FotoWidget.KEY_SHOW_DATE] ?: false,
                        onSave = ::save,
                    )
                }
            }
        }
    }

    private fun save(source: WidgetSource, style: WidgetStyle, showDate: Boolean) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
                it[FotoWidget.KEY_SOURCE] = source.key
                it[FotoWidget.KEY_STYLE] = style.name
                it[FotoWidget.KEY_SHOW_DATE] = showDate
            }
            FotoWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private companion object {
        /** Tamaño típico de un widget de 2×2, para que las vistas previas tengan sus proporciones. */
        const val PREVIEW_DP = 180f
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    @Composable
    private fun ConfigScreen(
        albums: List<Album>,
        currentPhotoId: String?,
        initialSource: WidgetSource,
        initialStyle: WidgetStyle,
        initialShowDate: Boolean,
        onSave: (WidgetSource, WidgetStyle, Boolean) -> Unit,
    ) {
        // Si el álbum elegido se borró, volver a "Todas".
        var source by remember {
            mutableStateOf(
                initialSource.takeIf { s -> s !is WidgetSource.FromAlbum || albums.any { it.id == s.albumId } }
                    ?: WidgetSource.All,
            )
        }
        var style by remember { mutableStateOf(initialStyle) }
        var showDate by remember { mutableStateOf(initialShowDate) }

        // Mostrar la foto que el widget tiene ahora, si sigue entre las elegidas, así la vista previa es fiel.
        val photo by produceState<Photo?>(null, source) {
            value = withContext(Dispatchers.IO) {
                val pool = PhotoPool.resolve(this@WidgetConfigActivity, source)
                pool.firstOrNull { it.id == currentPhotoId } ?: pool.lastOrNull()
            }
        }
        // Fuente decodificada una sola vez para todas las vistas previas.
        val sourceBitmap by produceState<Bitmap?>(null, photo) {
            value = photo?.let { withContext(Dispatchers.IO) { PhotoRepository.decodeForSize(it.file, 720) } }
        }
        val caption = photo?.let { PhotoPool.caption(it, source, showDate) }
        val large by produceState<Bitmap?>(null, sourceBitmap, style, caption) {
            value = sourceBitmap?.let { withContext(Dispatchers.Default) { PhotoStyler.render(it, style, 720, 720, 720f / PREVIEW_DP, caption) } }
        }
        val thumbs by produceState<Map<WidgetStyle, Bitmap>>(emptyMap(), sourceBitmap) {
            value = sourceBitmap?.let { src ->
                withContext(Dispatchers.Default) {
                    WidgetStyle.entries.associateWith { PhotoStyler.render(src, it, 240, 240, 240f / PREVIEW_DP) }
                }
            }.orEmpty()
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(vertical = 16.dp)) {
                Text(
                    "Configurá tu widget",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                SourcePicker(albums, source, onSelect = { source = it })
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().weight(1f, fill = false),
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photo == null) {
                            Text(
                                if (source is WidgetSource.FromAlbum) {
                                    "Este álbum todavía no tiene fotos."
                                } else {
                                    "Agregá fotos desde la app para ver la vista previa."
                                },
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Crossfade(large, label = "preview") { bitmap ->
                                if (bitmap != null) {
                                    Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(WidgetStyle.entries) { option ->
                        StyleOption(option, thumbs[option], selected = option == style, onClick = { style = option })
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (source != WidgetSource.Memories) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar la fecha", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "El día en que se sacó cada foto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showDate, onCheckedChange = { showDate = it })
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSave(source, style, showDate) },
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp),
                ) {
                    Text("Listo")
                }
            }
        }
    }

    @Composable
    private fun SourcePicker(albums: List<Album>, selected: WidgetSource, onSelect: (WidgetSource) -> Unit) {
        val options = listOf(WidgetSource.All to "Todas", WidgetSource.Memories to "Recuerdos") +
            albums.map { WidgetSource.FromAlbum(it.id) to it.name }
        Column {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.first.key }) { (option, label) ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(label) },
                    )
                }
            }
            if (selected == WidgetSource.Memories) {
                Text(
                    "Fotos de un día como hoy en otros años, con cuántos años pasaron.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }

    @Composable
    private fun StyleOption(style: WidgetStyle, preview: Bitmap?, selected: Boolean, onClick: () -> Unit) {
        OutlinedCard(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.width(96.dp),
        ) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(76.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (preview != null) {
                        Image(preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(style.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
