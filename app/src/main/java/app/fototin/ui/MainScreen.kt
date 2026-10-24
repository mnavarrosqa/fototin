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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fototin.data.Album
import app.fototin.data.Photo
import app.fototin.data.Settings
import coil.compose.AsyncImage

/** Diálogo abierto en la pantalla principal. */
private sealed interface Dialog {
    data object NewAlbum : Dialog
    data class RenameAlbum(val album: Album) : Dialog
    data class DeleteAlbum(val album: Album) : Dialog
    data object AddToAlbum : Dialog
    data object DeletePhotos : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onPickPhotos: () -> Unit, onAddWidget: () -> Unit) {
    val photos by viewModel.photos.collectAsState()
    val visible by viewModel.visiblePhotos.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val currentAlbum by viewModel.currentAlbum.collectAsState()
    val memories by viewModel.memoriesToday.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val interval by viewModel.interval.collectAsState()
    val shuffle by viewModel.shuffle.collectAsState()

    var dialog by remember { mutableStateOf<Dialog?>(null) }

    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting || currentAlbum != null) {
        if (selecting) viewModel.clearSelection() else viewModel.openAlbum(null)
    }

    val gridState = rememberLazyStaggeredGridState()
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

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
                        IconButton(onClick = { dialog = Dialog.AddToAlbum }) {
                            Icon(Icons.Filled.AddCircle, contentDescription = "Agregar a un álbum")
                        }
                        if (currentAlbum != null) {
                            IconButton(onClick = viewModel::removeSelectedFromAlbum) {
                                Icon(Icons.Filled.Clear, contentDescription = "Quitar del álbum")
                            }
                        }
                        IconButton(onClick = { dialog = Dialog.DeletePhotos }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar de Fototin")
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
                    expanded = atTop || visible.isEmpty(),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (currentAlbum != null) "Agregar al álbum" else "Agregar fotos") },
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
                    Header(
                        stack = memories.ifEmpty { photos.asReversed() },
                        memoriesCount = memories.size,
                        onAddWidget = onAddWidget,
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    SettingsCard(interval, shuffle, viewModel::setInterval, viewModel::setShuffle)
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                AlbumRow(
                    albums = albums,
                    allCount = photos.size,
                    current = currentAlbum,
                    onOpen = viewModel::openAlbum,
                    onNew = { dialog = Dialog.NewAlbum },
                )
            }
            importing?.let { progress ->
                item(span = StaggeredGridItemSpan.FullLine) { ImportingRow(progress) }
            }
            if (!selecting) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SectionTitle(
                        album = currentAlbum,
                        count = visible.size,
                        onRename = { a -> dialog = Dialog.RenameAlbum(a) },
                        onDelete = { a -> dialog = Dialog.DeleteAlbum(a) },
                    )
                }
            }
            if (visible.isEmpty() && importing == null) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    if (currentAlbum == null) EmptyState() else EmptyAlbum()
                }
            }
            items(visible, key = { it.id }) { photo ->
                PhotoTile(
                    photo = photo,
                    selected = photo.id in selection,
                    selecting = selecting,
                    onToggle = { viewModel.toggleSelection(photo.id) },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.NewAlbum -> NameDialog(
            title = if (selecting) "Nuevo álbum con ${selection.size} fotos" else "Nuevo álbum",
            initial = "",
            confirm = "Crear",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                viewModel.createAlbum(name)
                dialog = null
            },
        )
        is Dialog.RenameAlbum -> NameDialog(
            title = "Cambiar nombre",
            initial = d.album.name,
            confirm = "Guardar",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                viewModel.renameAlbum(d.album.id, name)
                dialog = null
            },
        )
        is Dialog.DeleteAlbum -> ConfirmDialog(
            title = "¿Borrar «${d.album.name}»?",
            text = "Las fotos siguen en Fototin. Los widgets que muestran este álbum pasan a mostrar todas.",
            confirm = "Borrar álbum",
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.deleteAlbum(d.album.id)
                dialog = null
            },
        )
        Dialog.AddToAlbum -> AlbumPickerDialog(
            albums = albums.filter { it.id != currentAlbum?.id },
            onDismiss = { dialog = null },
            onPick = { album ->
                viewModel.addSelectedToAlbum(album.id)
                dialog = null
            },
            onNew = { dialog = Dialog.NewAlbum },
        )
        Dialog.DeletePhotos -> ConfirmDialog(
            title = "¿Borrar ${selection.size} fotos de Fototin?",
            text = "Se quitan de todos los widgets y álbumes. Las fotos originales de tu galería no se tocan.",
            confirm = "Borrar",
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.deleteSelected()
                dialog = null
            },
        )
    }
}

@Composable
private fun Header(stack: List<Photo>, memoriesCount: Int, onAddWidget: () -> Unit) {
    Column {
        Text("Fototin", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PolaroidStack(stack.take(3))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (memoriesCount > 0) {
                            if (memoriesCount == 1) "Hoy tenés 1 recuerdo" else "Hoy tenés $memoriesCount recuerdos"
                        } else {
                            "Tus recuerdos, en la pantalla de inicio"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (memoriesCount > 0) {
                            "Fotos de un día como hoy en otros años. Elegí «Recuerdos» al configurar un widget."
                        } else {
                            "Cada widget puede mostrar todas tus fotos, un álbum o tus recuerdos."
                        },
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

@Composable
private fun AlbumRow(
    albums: List<Album>,
    allCount: Int,
    current: Album?,
    onOpen: (String?) -> Unit,
    onNew: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        item {
            FilterChip(
                selected = current == null,
                onClick = { onOpen(null) },
                label = { Text("Todas · $allCount") },
            )
        }
        rowItems(albums, key = { it.id }) { album ->
            FilterChip(
                selected = current?.id == album.id,
                onClick = { onOpen(album.id) },
                label = { Text(album.name) },
            )
        }
        item {
            AssistChip(
                onClick = onNew,
                label = { Text("Nuevo álbum") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun SectionTitle(album: Album?, count: Int, onRename: (Album) -> Unit, onDelete: (Album) -> Unit) {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(album?.name ?: "Tus fotos", style = MaterialTheme.typography.titleLarge)
            Text(
                "$count fotos · mantené presionada una para seleccionar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (album != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones del álbum")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Cambiar nombre") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onRename(album)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Borrar álbum") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete(album)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = { Text("Vacaciones, Familia, Mascotas…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AlbumPickerDialog(albums: List<Album>, onDismiss: () -> Unit, onPick: (Album) -> Unit, onNew: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a un álbum") },
        text = {
            Column {
                albums.forEach { album ->
                    TextButton(onClick = { onPick(album) }, modifier = Modifier.fillMaxWidth()) {
                        Text(album.name, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nuevo álbum", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EmptyAlbum() {
    Text(
        "Este álbum está vacío. Tocá «Agregar al álbum», o en «Todas» mantené presionadas " +
            "algunas fotos y sumalas desde arriba.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
    )
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
