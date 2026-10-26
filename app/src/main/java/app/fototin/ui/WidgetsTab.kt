package app.fototin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.fototin.R
import app.fototin.widget.PlacedWidget

/** Pestaña Widgets: los que están puestos y cómo agregar otros. */
@Composable
fun WidgetsTab(
    padding: PaddingValues,
    widgets: List<PlacedWidget>,
    hasPhotos: Boolean,
    onAdd: (collage: Boolean) -> Unit,
    onNext: (PlacedWidget) -> Unit,
    onEdit: (PlacedWidget) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "Widgets",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (widgets.isNotEmpty()) {
            item { SectionHeader("En tu pantalla de inicio", "Cambiá la foto o editá el estilo sin salir de la app.") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(widgets, key = { it.appWidgetId }) { widget ->
                        PlacedWidgetCard(
                            widget,
                            onNext = { onNext(widget) },
                            onEdit = { onEdit(widget) },
                            // Con uno solo ocupa todo el ancho; con varios se asoma el siguiente.
                            modifier = Modifier.fillParentMaxWidth(if (widgets.size == 1) 1f else 0.88f),
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(
                "Agregar un widget",
                if (hasPhotos) "Se agrega a tu pantalla de inicio y después elegís el estilo." else "Primero sumá algunas fotos en la pestaña Fotos.",
            )
        }
        item {
            AddWidgetCard(
                preview = R.drawable.widget_preview,
                title = "Foto",
                text = "Una foto a la vez, con 10 estilos: polaroid, corazón, estampilla, marco y más.",
                onAdd = { onAdd(false) },
            )
        }
        item {
            AddWidgetCard(
                preview = R.drawable.collage_preview,
                title = "Collage",
                text = "Dos, tres o cuatro fotos juntas que cambian a la vez.",
                onAdd = { onAdd(true) },
            )
        }
        item {
            Text(
                "También podés agregarlos manteniendo presionada la pantalla de inicio → Widgets → Fototin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Un widget puesto: vista previa a la izquierda y acciones apiladas a la derecha, para que los
 * botones entren aunque el teléfono use letra grande.
 */
@Composable
private fun PlacedWidgetCard(widget: PlacedWidget, onNext: () -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(if (widget.collage) 150.dp else 120.dp).height(130.dp),
                contentAlignment = Alignment.Center,
            ) {
                val preview = widget.preview
                if (preview != null) {
                    Image(preview.asImageBitmap(), contentDescription = widget.description, modifier = Modifier.fillMaxSize())
                } else {
                    Text("Sin fotos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(widget.description, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar", maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AddWidgetCard(preview: Int, title: String, text: String, onAdd: () -> Unit) {
    Card(
        onClick = onAdd,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(preview), contentDescription = null, modifier = Modifier.width(96.dp).height(80.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAdd) { Text("Agregar") }
            }
        }
    }
}
