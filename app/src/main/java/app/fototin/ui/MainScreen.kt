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
import app.fototin.widget.PlacedWidget
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.Calendar
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.saveable.rememberSaveable

/** Diálogo abierto en la pantalla principal. */
private sealed interface Dialog {
    data object NewAlbum : Dialog
    data class RenameAlbum(val album: Album) : Dialog
    data class DeleteAlbum(val album: Album) : Dialog
    data object AddToAlbum : Dialog
}

/** Pestañas de la pantalla principal. */
private enum class Tab(val label: String) {
    PHOTOS("Fotos"),
    WIDGETS("Widgets"),
    SETTINGS("Ajustes"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPickPhotos: () -> Unit,
    onAddWidget: (collage: Boolean) -> Unit,
    onEditWidget: (appWidgetId: Int) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val photos by viewModel.photos.collectAsState()
    val visible by viewModel.visiblePhotos.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val currentAlbum by viewModel.currentAlbum.collectAsState()
    val memories by viewModel.memoriesToday.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val interval by viewModel.interval.collectAsState()
    val shuffle by viewModel.shuffle.collectAsState()
    val sortByTaken by viewModel.sortByTaken.collectAsState()
    val widgets by viewModel.widgets.collectAsState()
    val openPhoto by viewModel.openPhoto.collectAsState()
    val backupBusy by viewModel.backupBusy.collectAsState()
    val gridSize by viewModel.gridSize.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val cleaning by viewModel.cleaning.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.PHOTOS) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    // Borrar no pide confirmación: se puede deshacer desde el aviso.
    LaunchedEffect(Unit) {
        viewModel.undo.collect { event ->
            val result = snackbar.showSnackbar(event.message, actionLabel = "Deshacer", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(event.removed)
            } else {
                viewModel.confirmDelete(event.removed)
            }
        }
    }

    val selecting = selection.isNotEmpty()
    // Atrás: primero cancela la selección, después vuelve a Fotos y por último sale del álbum.
    BackHandler(enabled = selecting || tab != Tab.PHOTOS || currentAlbum != null) {
        when {
            selecting -> viewModel.clearSelection()
            tab != Tab.PHOTOS -> tab = Tab.PHOTOS
            else -> viewModel.openAlbum(null)
        }
    }

    val gridState = rememberLazyStaggeredGridState()
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
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
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Seleccionar todas")
                            }
                            IconButton(onClick = { dialog = Dialog.AddToAlbum }) {
                                Icon(Icons.Filled.AddCircle, contentDescription = "Agregar a un álbum")
                            }
                            if (currentAlbum != null) {
                                IconButton(onClick = viewModel::removeSelectedFromAlbum) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Quitar del álbum")
                                }
                            }
                            IconButton(onClick = viewModel::deleteSelected) {
                                Icon(Icons.Filled.Delete, contentDescription = "Borrar de Fototin")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(visible = !selecting, enter = fadeIn(), exit = fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = {
                                    when (item) {
                                        Tab.PHOTOS -> Icon(painterResource(R.drawable.ic_tab_photos), contentDescription = null)
                                        Tab.WIDGETS -> Icon(painterResource(R.drawable.ic_tab_widgets), contentDescription = null)
                                        Tab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
                                    }
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(visible = tab == Tab.PHOTOS && !selecting, enter = scaleIn(), exit = scaleOut()) {
                    ExtendedFloatingActionButton(
                        onClick = onPickPhotos,
                        expanded = atTop || visible.isEmpty(),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(if (currentAlbum != null) "Agregar al álbum" else "Agregar fotos") },
                    )
                }
            },
        ) { padding ->
            Crossfade(tab, label = "tab") { current ->
                when (current) {
                    Tab.PHOTOS -> PhotosTab(
                        padding = padding,
                        gridState = gridState,
                        photos = photos,
                        visible = visible,
                        albums = albums,
                        currentAlbum = currentAlbum,
                        memories = memories,
                        importing = importing,
                        selection = selection,
                        sortByTaken = sortByTaken,
                        gridSize = gridSize,
                        onPickPhotos = onPickPhotos,
                        onGoToWidgets = { tab = Tab.WIDGETS },
                        onOpenAlbum = viewModel::openAlbum,
                        onNewAlbum = { dialog = Dialog.NewAlbum },
                        onSort = viewModel::setSortByTaken,
                        onGridSize = viewModel::setGridSize,
                        onRenameAlbum = { dialog = Dialog.RenameAlbum(it) },
                        onDeleteAlbum = { dialog = Dialog.DeleteAlbum(it) },
                        onOpenPhoto = viewModel::openPhoto,
                        onToggleSelection = viewModel::toggleSelection,
                    )
                    Tab.WIDGETS -> WidgetsTab(
                        padding = padding,
                        widgets = widgets,
                        hasPhotos = photos.isNotEmpty(),
                        onAdd = onAddWidget,
                        onNext = viewModel::advanceWidget,
                        onEdit = { onEditWidget(it.appWidgetId) },
                    )
                    Tab.SETTINGS -> SettingsTab(
                        padding = padding,
                        interval = interval,
                        shuffle = shuffle,
                        backupBusy = backupBusy,
                        photoCount = photos.size,
                        dynamicColor = dynamicColor,
                        onInterval = viewModel::setInterval,
                        onShuffle = viewModel::setShuffle,
                        onDynamicColor = viewModel::setDynamicColor,
                        cleaning = cleaning,
                        onRemoveDuplicates = viewModel::removeDuplicates,
                        onBackup = onBackup,
                        onRestore = onRestore,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = openPhoto != null,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            // Mantener la última foto mientras se anima la salida.
            var shown by remember { mutableStateOf(openPhoto) }
            openPhoto?.let { shown = it }
            shown?.let { photo ->
                PhotoDetailScreen(
                    photo = photo,
                    photos = visible,
                    albums = albums,
                    onPageChange = viewModel::openPhoto,
                    onClose = { viewModel.openPhoto(null) },
                    onNote = { viewModel.setNote(photo.id, it) },
                    onFavorite = { viewModel.toggleFavorite(photo) },
                    onFocus = { x, y -> viewModel.setFocus(photo.id, x, y) },
                    onToggleAlbum = { viewModel.toggleAlbum(photo.id, it) },
                    onDelete = { viewModel.deletePhoto(photo.id) },
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
    }
}

@Composable
private fun PhotosTab(
    padding: PaddingValues,
    gridState: LazyStaggeredGridState,
    photos: List<Photo>,
    visible: List<Photo>,
    albums: List<Album>,
    currentAlbum: Album?,
    memories: List<Photo>,
    importing: ImportProgress?,
    selection: Set<String>,
    sortByTaken: Boolean,
    gridSize: Int,
    onPickPhotos: () -> Unit,
    onGoToWidgets: () -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onNewAlbum: () -> Unit,
    onSort: (Boolean) -> Unit,
    onGridSize: (Int) -> Unit,
    onRenameAlbum: (Album) -> Unit,
    onDeleteAlbum: (Album) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    val selecting = selection.isNotEmpty()
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(GRID_SIZES[gridSize.coerceIn(GRID_SIZES.indices)]),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!selecting) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    "Fototin",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (memories.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    MemoriesBanner(memories, onClick = { onOpenPhoto(memories.first().id) })
                }
            }
        }
        if (photos.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                AlbumRow(
                    albums = albums,
                    allCount = photos.size,
                    current = currentAlbum,
                    onOpen = onOpenAlbum,
                    onNew = onNewAlbum,
                )
            }
        }
        importing?.let { progress ->
            item(span = StaggeredGridItemSpan.FullLine) { ImportingRow(progress) }
        }
        if (!selecting && photos.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionTitle(
                    album = currentAlbum,
                    count = visible.size,
                    sortByTaken = sortByTaken,
                    gridSize = gridSize,
                    onSort = onSort,
                    onGridSize = onGridSize,
                    onRename = onRenameAlbum,
                    onDelete = onDeleteAlbum,
                )
            }
        }
        if (visible.isEmpty() && importing == null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                if (currentAlbum == null) GettingStarted(onPickPhotos, onGoToWidgets) else EmptyAlbum()
            }
        }
        // Ordenadas por fecha de la foto, con un separador por año.
        val groups = if (sortByTaken) visible.groupBy { yearOf(it.takenAt) }.toList() else listOf(null to visible)
        groups.forEach { (year, group) ->
            if (year != null) {
                item(key = "year-$year", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            items(group, key = { it.id }) { photo ->
                PhotoTile(
                    photo = photo,
                    selected = photo.id in selection,
                    selecting = selecting,
                    onOpen = { onOpenPhoto(photo.id) },
                    onToggle = { onToggleSelection(photo.id) },
                    // Las fotos se acomodan con una animación al borrar, agregar o cambiar el orden.
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** Aviso de recuerdos de hoy, con las fotos apiladas. Al tocarlo abre la primera. */
@Composable
private fun MemoriesBanner(memories: List<Photo>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PolaroidStack(memories.take(3))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (memories.size == 1) "Hoy tenés 1 recuerdo" else "Hoy tenés ${memories.size} recuerdos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fotos de un día como hoy en otros años. Elegí «Recuerdos» en un widget para verlas en tu pantalla de inicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
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
private fun SectionTitle(
    album: Album?,
    count: Int,
    sortByTaken: Boolean,
    gridSize: Int,
    onSort: (Boolean) -> Unit,
    onGridSize: (Int) -> Unit,
    onRename: (Album) -> Unit,
    onDelete: (Album) -> Unit,
) {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(album?.name ?: "Tus fotos", style = MaterialTheme.typography.titleLarge)
            Text(
                "$count fotos · mantené presionada una para elegir varias",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Orden y opciones del álbum en un solo menú, para no llenar la pantalla de botones.
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Últimas agregadas") },
                    trailingIcon = { if (!sortByTaken) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        menu = false
                        onSort(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Por fecha de la foto") },
                    trailingIcon = { if (sortByTaken) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        menu = false
                        onSort(true)
                    },
                )
                HorizontalDivider()
                GRID_SIZE_LABELS.forEachIndexed { size, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (size == gridSize) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = {
                            menu = false
                            onGridSize(size)
                        },
                    )
                }
                if (album != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Cambiar nombre del álbum") },
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
                Box(Modifier.size(68.dp).background(Color(0xFFE6E6FA))) {
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

/** Primeros pasos, cuando todavía no hay fotos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GettingStarted(onPickPhotos: () -> Unit, onGoToWidgets: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            PolaroidStack(emptyList())
        }
        Spacer(Modifier.height(20.dp))
        Text("Tus fotos, en tu pantalla de inicio", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Step(1, "Elegí tus fotos", "De la galería o de Google Fotos. También podés usar «Compartir → Fototin» desde cualquier app de fotos.")
        Step(2, "Agregá un widget", "Una foto sola o un collage. Elegís el estilo y qué fotos muestra: todas, un álbum, favoritas o recuerdos.")
        Step(3, "Disfrutalo", "Las fotos van cambiando solas. Tocá una para escribirle una nota o ajustar cómo se recorta.")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickPhotos) { Text("Elegir fotos") }
            TextButton(onClick = onGoToWidgets) { Text("Ver widgets") }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, text: String) {
    Row(Modifier.padding(bottom = 16.dp)) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: Photo,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val inset by animateDpAsState(if (selected) 8.dp else 0.dp, label = "inset")
    val corner by animateDpAsState(if (selected) 20.dp else 14.dp, label = "corner")
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(photo.aspectRatio.coerceIn(0.6f, 1.7f))
            .combinedClickable(
                onClick = { if (selecting) onToggle() else onOpen() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
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
        if (photo.favorite && !selecting) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorita",
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp),
            )
        }
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

/** Ancho mínimo de cada foto de la grilla según el tamaño elegido. */
private val GRID_SIZES = listOf(84.dp, 108.dp, 150.dp)
private val GRID_SIZE_LABELS = listOf("Fotos chicas", "Fotos medianas", "Fotos grandes")

private fun yearOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
