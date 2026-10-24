package app.fototin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fototin.R
import app.fototin.data.Photo
import app.fototin.data.Settings
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onPickPhotos: () -> Unit, onAddWidget: () -> Unit) {
    val photos by viewModel.photos.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val interval by viewModel.interval.collectAsState()
    val shuffle by viewModel.shuffle.collectAsState()

    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    val gridState = rememberLazyStaggeredGridState()
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    // Las más nuevas primero.
    val shown = remember(photos) { photos.asReversed() }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = selecting, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    title = { Text("${selection.size} seleccionadas") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::removeSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar del widget")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !selecting, enter = scaleIn(), exit = scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = onPickPhotos,
                    expanded = atTop || photos.isEmpty(),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Agregar fotos") },
                )
            }
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!selecting) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Header(photos = shown, onAddWidget = onAddWidget)
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    SettingsCard(interval, shuffle, viewModel::setInterval, viewModel::setShuffle)
                }
            }
            importing?.let { progress ->
                item(span = StaggeredGridItemSpan.FullLine) { ImportingRow(progress) }
            }
            if (photos.isEmpty() && importing == null) {
                item(span = StaggeredGridItemSpan.FullLine) { EmptyState() }
            } else if (!selecting) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text("Tus fotos", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${photos.size} en rotación · mantené presionada una para quitarla",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { photo ->
                PhotoTile(
                    photo = photo,
                    selected = photo.id in selection,
                    selecting = selecting,
                    onToggle = { viewModel.toggleSelection(photo.id) },
                )
            }
        }
    }
}

@Composable
private fun Header(photos: List<Photo>, onAddWidget: () -> Unit) {
    Column {
        Text("Fototin", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PolaroidStack(photos.take(3))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tus recuerdos, en la pantalla de inicio",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Elegí entre 7 estilos al agregarlo. Tocá el widget para ver otra foto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddWidget) { Text("Agregar widget") }
                }
            }
        }
    }
}

/** Tres fotos apiladas como polaroids sobre una mesa. */
@Composable
private fun PolaroidStack(photos: List<Photo>) {
    val angles = listOf(-8f, 6f, -2f)
    val offsets = listOf((-10).dp, 10.dp, 0.dp)
    Box(Modifier.size(width = 104.dp, height = 112.dp), contentAlignment = Alignment.Center) {
        (0 until 3).forEach { i ->
            // Se dibujan de atrás hacia adelante: la foto más nueva queda arriba.
            val photo = photos.getOrNull(2 - i)
            Box(
                Modifier
                    .offset(x = offsets[i])
                    .rotate(angles[i])
                    .shadow(4.dp, RoundedCornerShape(3.dp))
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 16.dp),
            ) {
                Box(Modifier.size(68.dp).background(Color(0xFFF3D9D1))) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo.thumb.takeIf { it.exists() } ?: photo.file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    interval: Int,
    shuffle: Boolean,
    onInterval: (Int) -> Unit,
    onShuffle: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("Cambiar de foto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                Settings.INTERVAL_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = option == interval,
                        onClick = { onInterval(option) },
                        label = { Text(intervalLabel(option)) },
                        leadingIcon = if (option == interval) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Orden aleatorio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Sin repetir hasta mostrar todas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = shuffle, onCheckedChange = onShuffle)
            }
        }
    }
}

@Composable
private fun ImportingRow(progress: ImportProgress) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            "Agregando ${progress.done} de ${progress.total}…",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth().clip(CircleShape),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
    ) {
        Box(
            Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_widget_empty), contentDescription = null, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Sumá tus primeras fotos", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tocá «Agregar fotos» para elegirlas de tu galería o de Google Fotos. " +
                "También podés usar «Compartir → Fototin» desde cualquier app de fotos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(photo: Photo, selected: Boolean, selecting: Boolean, onToggle: () -> Unit) {
    val inset by animateDpAsState(if (selected) 8.dp else 0.dp, label = "inset")
    val corner by animateDpAsState(if (selected) 20.dp else 14.dp, label = "corner")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(photo.aspectRatio.coerceIn(0.6f, 1.7f))
            .combinedClickable(
                onClick = { if (selecting) onToggle() },
                onLongClick = onToggle,
            ),
    ) {
        AsyncImage(
            model = photo.thumb.takeIf { it.exists() } ?: photo.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .padding(inset)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (selecting) {
            val check = Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp)
            if (selected) {
                Box(check.background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = "Seleccionada", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(check.border(2.dp, Color.White, CircleShape).background(Color(0x33000000), CircleShape))
            }
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> "Al tocar"
    minutes < 60 -> "$minutes min"
    minutes < 1440 -> "${minutes / 60} h"
    else -> "1 día"
}
