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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
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
import app.fototin.data.WidgetSource
import app.fototin.ui.FototinTheme
import app.fototin.widget.CollageWidget
import app.fototin.widget.CollageWidgetReceiver
import app.fototin.widget.FotoWidget
import app.fototin.widget.PhotoStyler
import app.fototin.widget.StyledSource
import app.fototin.widget.TapAction
import app.fototin.widget.WidgetKeys
import app.fototin.widget.WidgetStyle
import app.fototin.widget.WidgetSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que el usuario eligió en la configuración. */
private data class Choice(
    val source: WidgetSource,
    val style: WidgetStyle,
    val showDate: Boolean,
    val tap: TapAction,
    val count: Int,
)

/** Se abre al agregar (o reconfigurar) un widget de foto o un collage. */
class WidgetConfigActivity : ComponentActivity() {

    private val appWidgetId by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val isCollage by lazy {
        AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className ==
            CollageWidgetReceiver::class.java.name
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
            val saved = WidgetSource.from(prefs[WidgetKeys.SOURCE])
            val initial = Choice(
                // Si el álbum elegido se borró, volver a "Todas".
                source = saved.takeIf { s -> s !is WidgetSource.FromAlbum || albums.any { it.id == s.albumId } }
                    ?: WidgetSource.All,
                style = WidgetStyle.from(prefs[WidgetKeys.STYLE]),
                showDate = prefs[WidgetKeys.SHOW_DATE] ?: false,
                tap = TapAction.from(prefs[WidgetKeys.TAP]),
                count = prefs[WidgetKeys.COUNT] ?: CollageWidget.DEFAULT_COUNT,
            )

            setContent {
                FototinTheme {
                    ConfigScreen(
                        albums = albums,
                        currentPhotoId = prefs[WidgetKeys.PHOTO_ID],
                        currentCollageIds = prefs[WidgetKeys.PHOTO_IDS],
                        initial = initial,
                        onSave = ::save,
                    )
                }
            }
        }
    }

    private fun save(choice: Choice) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
                it[WidgetKeys.SOURCE] = choice.source.key
                it[WidgetKeys.STYLE] = choice.style.name
                it[WidgetKeys.SHOW_DATE] = choice.showDate
                it[WidgetKeys.TAP] = choice.tap.name
                it[WidgetKeys.COUNT] = choice.count
            }
            if (isCollage) {
                CollageWidget().update(this@WidgetConfigActivity, glanceId)
            } else {
                FotoWidget().update(this@WidgetConfigActivity, glanceId)
            }
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    @Composable
    private fun ConfigScreen(
        albums: List<Album>,
        currentPhotoId: String?,
        currentCollageIds: String?,
        initial: Choice,
        onSave: (Choice) -> Unit,
    ) {
        var source by remember { mutableStateOf(initial.source) }
        var style by remember { mutableStateOf(initial.style) }
        var showDate by remember { mutableStateOf(initial.showDate) }
        var tap by remember { mutableStateOf(initial.tap) }
        var count by remember { mutableIntStateOf(initial.count) }

        // Las fotos que el widget muestra ahora, si siguen entre las elegidas: la vista previa es fiel.
        val photos by produceState<List<Photo>>(emptyList(), source, count) {
            value = withContext(Dispatchers.IO) {
                val pool = PhotoPool.resolve(this@WidgetConfigActivity, source)
                if (isCollage) {
                    CollageWidget.current(pool, currentCollageIds, count)
                } else {
                    listOfNotNull(pool.firstOrNull { it.id == currentPhotoId } ?: pool.lastOrNull())
                }
            }
        }
        // Fotos decodificadas una sola vez para todas las vistas previas.
        val sources by produceState<List<StyledSource>>(emptyList(), photos) {
            value = withContext(Dispatchers.IO) { photos.mapNotNull { WidgetSupport.load(it, 720) } }
        }
        val caption = photos.firstOrNull()?.let { PhotoPool.caption(it, source, showDate) }
        val corner = WidgetSupport.launcherCornerDp(this, fallback = if (isCollage) 20f else 28f)
        val large by produceState<Bitmap?>(null, sources, style, caption) {
            value = withContext(Dispatchers.Default) {
                when {
                    sources.isEmpty() -> null
                    isCollage -> PhotoStyler.renderCollage(sources, 900, 450, 900f / COLLAGE_PREVIEW_DP, corner)
                    else -> PhotoStyler.render(sources.first(), style, 720, 720, 720f / PREVIEW_DP, caption, corner)
                }
            }
        }
        val thumbs by produceState<Map<WidgetStyle, Bitmap>>(emptyMap(), sources) {
            value = if (isCollage || sources.isEmpty()) {
                emptyMap()
            } else {
                withContext(Dispatchers.Default) {
                    WidgetStyle.entries.associateWith {
                        PhotoStyler.render(sources.first(), it, 240, 240, 240f / PREVIEW_DP, cornerDp = corner)
                    }
                }
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(vertical = 16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(
                        if (isCollage) "Configurá tu collage" else "Configurá tu widget",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    SourcePicker(albums, source, onSelect = { source = it })
                    Spacer(Modifier.height(12.dp))
                    Preview(large, isEmpty = photos.isEmpty(), source = source)
                    Spacer(Modifier.height(16.dp))

                    if (isCollage) {
                        Label("Cantidad de fotos")
                        ChipRow(CollageWidget.COUNT_OPTIONS, count, { "$it fotos" }, { count = it })
                    } else {
                        Label("Estilo")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(WidgetStyle.entries) { option ->
                                StyleOption(option, thumbs[option], selected = option == style, onClick = { style = option })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Label("Al tocar el widget")
                        ChipRow(TapAction.entries, tap, { it.label }, { tap = it })
                        if (source != WidgetSource.Memories) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Mostrar nota o fecha", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "La nota que le escribiste a la foto o, si no tiene, el día en que se sacó",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = showDate, onCheckedChange = { showDate = it })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onSave(Choice(source, style, showDate, tap, count)) },
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp),
                ) {
                    Text("Listo")
                }
            }
        }
    }

    @Composable
    private fun Preview(bitmap: Bitmap?, isEmpty: Boolean, source: WidgetSource) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(if (isCollage) 1.6f else 1.15f).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isEmpty) {
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
                    Crossfade(bitmap, label = "preview") { image ->
                        if (image != null) {
                            Image(image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Label(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
    }

    @Composable
    private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options) { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }

    @Composable
    private fun SourcePicker(albums: List<Album>, selected: WidgetSource, onSelect: (WidgetSource) -> Unit) {
        val options = listOf(
            WidgetSource.All to "Todas",
            WidgetSource.Memories to "Recuerdos",
            WidgetSource.Favorites to "Favoritas",
        ) +
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

    private companion object {
        /** Tamaño típico de un widget de 2×2, para que las vistas previas tengan sus proporciones. */
        const val PREVIEW_DP = 180f
        /** Ancho típico de un collage de 4×2. */
        const val COLLAGE_PREVIEW_DP = 360f
    }
}
