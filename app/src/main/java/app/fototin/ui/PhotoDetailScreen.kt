package app.fototin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.fototin.data.Album
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** Detalle de una foto: nota, favorita, encuadre para los widgets y álbumes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoDetailScreen(
    photo: Photo,
    albums: List<Album>,
    onClose: () -> Unit,
    onNote: (String) -> Unit,
    onFavorite: () -> Unit,
    onFocus: (Float, Float) -> Unit,
    onToggleAlbum: (Album) -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var confirmDelete by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // El foco se mueve al instante en pantalla y se guarda al soltar.
    var focus by remember(photo.id) { mutableStateOf(photo.focusX to photo.focusY) }

    var note by remember(photo.id) { mutableStateOf(photo.note) }
    LaunchedEffect(note) {
        delay(600)
        if (note.trim() != photo.note) onNote(note)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(PhotoPool.formatDate(photo.takenAt)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFavorite()
                    }) {
                        Icon(
                            if (photo.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (photo.favorite) "Quitar de favoritas" else "Marcar como favorita",
                            tint = if (photo.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar de Fototin")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            FocusPicker(photo, focus) { x, y ->
                focus = x to y
                onFocus(x, y)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tocá la parte de la foto que siempre tiene que verse en los widgets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Text("Así se ve en los widgets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                CropPreview(photo, focus, Modifier.width(96.dp).aspectRatio(1f))
                CropPreview(photo, focus, Modifier.width(72.dp).aspectRatio(0.6f))
                CropPreview(photo, focus, Modifier.width(160.dp).aspectRatio(2f))
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(NOTE_MAX) },
                label = { Text("Nota") },
                placeholder = { Text("Mi primer día en la playa") },
                supportingText = { Text("Se muestra en el widget si activás «Mostrar nota o fecha». ${note.length}/$NOTE_MAX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            if (albums.isNotEmpty()) {
                Text("Álbumes", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    albums.forEach { album ->
                        FilterChip(
                            selected = photo.id in album.photoIds,
                            onClick = { onToggleAlbum(album) },
                            label = { Text(album.name) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar esta foto de Fototin?") },
            text = { Text("Se quita de todos los widgets y álbumes. La original de tu galería no se toca.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private const val NOTE_MAX = 40

/** La foto entera; un toque elige el punto de foco, marcado con un círculo. */
@Composable
private fun FocusPicker(photo: Photo, focus: Pair<Float, Float>, onFocus: (Float, Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(photo.aspectRatio.coerceIn(0.6f, 1.8f))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(photo.id) {
                detectTapGestures { offset ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFocus(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        (offset.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        AsyncImage(
            model = photo.file,
            contentDescription = null,
            // Centrada (no según el foco) para que el toque marque siempre el mismo punto de la foto.
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val marker = 36.dp
        Box(
            Modifier
                .offset(x = maxWidth * focus.first - marker / 2, y = maxHeight * focus.second - marker / 2)
                .size(marker)
                .border(3.dp, Color.White, CircleShape)
                .background(Color(0x33000000), CircleShape),
        )
    }
}

@Composable
private fun CropPreview(photo: Photo, focus: Pair<Float, Float>, modifier: Modifier) {
    AsyncImage(
        model = photo.thumb.takeIf { it.exists() } ?: photo.file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = FocusAlignment(focus.first, focus.second),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/**
 * Ubica la imagen recortada igual que los widgets: con el punto de foco lo más centrado posible
 * sin dejar huecos (ver `PhotoStyler.drawPhoto`).
 */
private class FocusAlignment(private val fx: Float, private val fy: Float) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        fun axis(content: Int, container: Int, f: Float): Int {
            val min = min(container - content, 0).toFloat()
            return (container / 2f - f * content).coerceIn(min, maxOf(min, 0f)).roundToInt()
        }
        return IntOffset(axis(size.width, space.width, fx), axis(size.height, space.height, fy))
    }
}
