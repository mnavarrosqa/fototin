package app.fototin.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.fototin.data.PhotoRepository
import app.fototin.ui.FototinTheme
import app.fototin.widget.FotoWidget
import app.fototin.widget.PhotoStyler
import app.fototin.widget.WidgetStyle
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
            val initial = WidgetStyle.from(prefs[FotoWidget.KEY_STYLE])

            setContent {
                FototinTheme {
                    ConfigScreen(initial = initial, onSave = { style -> save(style) })
                }
            }
        }
    }

    private fun save(style: WidgetStyle) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[FotoWidget.KEY_STYLE] = style.name }
            FotoWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    @Composable
    private fun ConfigScreen(initial: WidgetStyle, onSave: (WidgetStyle) -> Unit) {
        var selected by remember { mutableStateOf(initial) }
        val previews by produceState<Map<WidgetStyle, Bitmap>>(emptyMap()) {
            value = withContext(Dispatchers.Default) { renderPreviews() }
        }

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Elegí un estilo", style = MaterialTheme.typography.headlineSmall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(WidgetStyle.entries) { style ->
                        StyleOption(
                            style = style,
                            preview = previews[style],
                            selected = style == selected,
                            onClick = { selected = style },
                        )
                    }
                }
                Button(onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Listo")
                }
            }
        }
    }

    @Composable
    private fun StyleOption(style: WidgetStyle, preview: Bitmap?, selected: Boolean, onClick: () -> Unit) {
        OutlinedCard(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (preview != null) {
                        Image(preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(style.label, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    /** Muestra cada estilo con la primera foto del usuario. */
    private fun renderPreviews(): Map<WidgetStyle, Bitmap> {
        val file = PhotoRepository(this).list().firstOrNull() ?: return emptyMap()
        val sizePx = 400
        val source = PhotoRepository.decodeForSize(file, sizePx) ?: return emptyMap()
        val density = resources.displayMetrics.density
        return WidgetStyle.entries.associateWith { PhotoStyler.render(source, it, sizePx, sizePx, density) }
            .also { source.recycle() }
    }
}
