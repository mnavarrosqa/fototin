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
import androidx.compose.material.icons.filled.PlayArrow
import app.fototin.data.WidgetSource
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Diálogo abierto en la pantalla principal. */
private sealed interface Dialog {
    data object NewAlbum : Dialog
    data class AlbumFromImport(val ids: List<String>) : Dialog
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
    onPickGooglePhotos: () -> Unit,
    onAddWidget: (collage: Boolean) -> Unit,
    onEditWidget: (appWidgetId: Int) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onUpdate: () -> Unit,
    onSlideshow: (List<Photo>, WidgetSource) -> Unit,
    onMemoriesNotification: (Boolean) -> Unit,
    onScreensaverSettings: () -> Unit,
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
    val screensaverSource by viewModel.screensaverSource.collectAsState()
    val memoriesNotification by viewModel.memoriesNotification.collectAsState()
    val googlePhotos by viewModel.googlePhotos.collectAsState()
    // Mientras se eligen fotos de Google Fotos, el aviso con los botones tiene que estar a la vista.
    val choosingGooglePhotos = googlePhotos != null
    val update by viewModel.update.collectAsState()
    val whatsNew by viewModel.whatsNew.collectAsState()
    val checkingUpdates by viewModel.checkingUpdates.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val query by viewModel.query.collectAsState()
    var addMenu by remember { mutableStateOf(false) }

    var tab by rememberSaveable { mutableStateOf(Tab.PHOTOS) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    // Después de traer varias fotos, ofrecer guardarlas como álbum.
    LaunchedEffect(Unit) {
        viewModel.albumOffers.collect { offer ->
            val result = snackbar.showSnackbar(offer.message, actionLabel = "Guardar como álbum", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) dialog = Dialog.AlbumFromImport(offer.ids)
        }
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
    BackHandler(enabled = selecting || tab != Tab.PHOTOS || currentAlbum != null || favoritesOnly || searching) {
        when {
            selecting -> viewModel.clearSelection()
            searching && tab == Tab.PHOTOS -> viewModel.closeSearch()
            tab != Tab.PHOTOS -> tab = Tab.PHOTOS
            else -> viewModel.openAlbum(null)
        }
    }

    val gridState = rememberLazyGridState()
    val atTop by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(choosingGooglePhotos) {
        if (choosingGooglePhotos) {
            tab = Tab.PHOTOS
            gridState.scrollToItem(0)
        }
    }
    // Tocar «Fotos» estando en Fotos vuelve al principio, como en las apps de galería.
    val selectTab: (Tab) -> Unit = { item ->
        if (item == tab && item == Tab.PHOTOS) scope.launch { gridState.animateScrollToItem(0) }
        tab = item
    }
    val addMenuContent: @Composable () -> Unit = {
        AddMenu(
            expanded = addMenu,
            onDismiss = { addMenu = false },
            onPickPhotos = {
                addMenu = false
                onPickPhotos()
            },
            onPickGooglePhotos = {
                addMenu = false
                onPickGooglePhotos()
            },
        )
    }

    // Desplegado o en tablet, las pestañas pasan a una barra lateral.
    val wide = isWideScreen()

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
        if (wide) {
            AnimatedVisibility(visible = !selecting, enter = fadeIn(), exit = fadeOut()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Spacer(Modifier.weight(1f))
                    Tab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = tab == item,
                            onClick = { selectTab(item) },
                            icon = { TabIcon(item) },
                            label = { Text(item.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            // En el teléfono, los avisos aparecen arriba de la barra flotante.
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (wide) 0.dp else 84.dp)) },
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
            floatingActionButton = {
                // En el teléfono el botón + va en la barra flotante; acá solo en pantallas anchas.
                AnimatedVisibility(visible = wide && tab == Tab.PHOTOS && !selecting, enter = scaleIn(), exit = scaleOut()) {
                    Box {
                        addMenuContent()
                        ExtendedFloatingActionButton(
                            onClick = { addMenu = true },
                            expanded = atTop || visible.isEmpty(),
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(if (currentAlbum != null) "Agregar al álbum" else "Agregar fotos") },
                        )
                    }
                }
            },
        ) { scaffoldPadding ->
            // Lugar para la barra flotante, que se dibuja encima del contenido.
            val padding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding(),
                bottom = scaffoldPadding.calculateBottomPadding() + if (wide || selecting) 0.dp else FLOATING_BAR_SPACE,
            )
            Crossfade(tab, label = "tab") { current ->
                when (current) {
                    Tab.PHOTOS -> PhotosTab(
                        padding = padding,
                        gridState = gridState,
                        photos = photos,
                        visible = visible,
                        albums = albums,
                        currentAlbum = currentAlbum,
                        favoritesOnly = favoritesOnly,
                        memories = memories,
                        importing = importing,
                        googlePhotos = googlePhotos,
                        update = update,
                        onUpdate = onUpdate,
                        onDismissUpdate = viewModel::dismissUpdate,
                        onReopenGooglePhotos = viewModel::reopenGooglePhotos,
                        onCheckGooglePhotos = viewModel::checkGooglePhotosNow,
                        onCancelGooglePhotos = viewModel::cancelGooglePhotos,
                        onGooglePhotosInBrowser = viewModel::openGooglePhotosInBrowser,
                        selection = selection,
                        sortByTaken = sortByTaken,
                        gridSize = gridSize,
                        onPickPhotos = onPickPhotos,
                        onGoToWidgets = { tab = Tab.WIDGETS },
                        onOpenAlbum = viewModel::openAlbum,
                        onOpenFavorites = viewModel::openFavorites,
                        onNewAlbum = { dialog = Dialog.NewAlbum },
                        onSort = viewModel::setSortByTaken,
                        onGridSize = viewModel::setGridSize,
                        onRenameAlbum = { dialog = Dialog.RenameAlbum(it) },
                        onDeleteAlbum = { dialog = Dialog.DeleteAlbum(it) },
                        onOpenPhoto = viewModel::openPhoto,
                        onToggleSelection = viewModel::toggleSelection,
                        onSelectRange = viewModel::selectRange,
                        searching = searching,
                        query = query,
                        onOpenSearch = viewModel::openSearch,
                        onQuery = viewModel::setQuery,
                        onCloseSearch = viewModel::closeSearch,
                        onSlideshow = onSlideshow,
                    )
                    Tab.WIDGETS -> WidgetsTab(
                        padding = padding,
                        widgets = widgets,
                        hasPhotos = photos.isNotEmpty(),
                        onAdd = onAddWidget,
                        onNext = viewModel::advanceWidget,
                        onEdit = { onEditWidget(it.appWidgetId) },
                        onShare = viewModel::shareWidget,
                    )
                    Tab.SETTINGS -> SettingsTab(
                        padding = padding,
                        interval = interval,
                        shuffle = shuffle,
                        gridSize = gridSize,
                        onGridSize = viewModel::setGridSize,
                        backupBusy = backupBusy,
                        photoCount = photos.size,
                        dynamicColor = dynamicColor,
                        onInterval = viewModel::setInterval,
                        onShuffle = viewModel::setShuffle,
                        onDynamicColor = viewModel::setDynamicColor,
                        cleaning = cleaning,
                        onRemoveDuplicates = viewModel::removeDuplicates,
                        installedVersion = viewModel.installedVersion,
                        update = update,
                        checkingUpdates = checkingUpdates,
                        autoCheckUpdates = autoCheckUpdates,
                        onCheckUpdates = { viewModel.checkForUpdates(manual = true) },
                        albums = albums,
                        screensaverSource = screensaverSource,
                        onScreensaverSource = viewModel::setScreensaverSource,
                        onScreensaverSettings = onScreensaverSettings,
                        memoriesNotification = memoriesNotification,
                        onMemoriesNotification = onMemoriesNotification,
                        onAutoCheckUpdates = viewModel::setAutoCheckUpdates,
                        onUpdate = onUpdate,
                        onBackup = onBackup,
                        onRestore = onRestore,
                    )
                }
            }
        }

        }

        if (!wide) {
            AnimatedVisibility(
                visible = !selecting,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            ) {
                FloatingTabBar(
                    current = tab,
                    onSelect = selectTab,
                    showAdd = tab == Tab.PHOTOS,
                    onAdd = { addMenu = true },
                    addMenu = addMenuContent,
                )
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

    whatsNew?.let { (version, notes) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissWhatsNew,
            title = { Text("Novedades de la versión $version") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (notes.isEmpty()) Text("Mejoras y correcciones.")
                    notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissWhatsNew) { Text("Genial") } },
        )
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
        is Dialog.AlbumFromImport -> NameDialog(
            title = "Nuevo álbum con ${d.ids.size} fotos",
            initial = "",
            confirm = "Crear",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                viewModel.createAlbumWith(name, d.ids)
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
private fun TabIcon(tab: Tab, contentDescription: String? = null) {
    when (tab) {
        Tab.PHOTOS -> Icon(painterResource(R.drawable.ic_tab_photos), contentDescription = contentDescription)
        Tab.WIDGETS -> Icon(painterResource(R.drawable.ic_tab_widgets), contentDescription = contentDescription)
        Tab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = contentDescription)
    }
}

/** Alto que ocupa la barra flotante (con su margen), para que el contenido no quede tapado. */
private val FLOATING_BAR_SPACE = 84.dp

/** De dónde agregar: el selector del teléfono o Google Fotos. */
@Composable
private fun AddMenu(expanded: Boolean, onDismiss: () -> Unit, onPickPhotos: () -> Unit, onPickGooglePhotos: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Desde el teléfono") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_tab_photos), contentDescription = null) },
            onClick = onPickPhotos,
        )
        DropdownMenuItem(
            text = { Text("Desde Google Fotos") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_cloud), contentDescription = null) },
            onClick = onPickGooglePhotos,
        )
    }
}

/**
 * Pestañas en una píldora que flota sobre las fotos, con el botón + al lado, como en las
 * galerías actuales: las fotos llegan hasta el borde y la barra no les quita lugar.
 */
@Composable
private fun FloatingTabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    showAdd: Boolean,
    onAdd: () -> Unit,
    addMenu: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Tab.entries.forEach { item ->
                    TabPill(item, selected = item == current, onClick = { onSelect(item) })
                }
            }
        }
        AnimatedVisibility(visible = showAdd, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box {
                Surface(
                    onClick = onAdd,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Agregar fotos", modifier = Modifier.size(28.dp))
                    }
                }
                addMenu()
            }
        }
    }
}

/** Una pestaña: solo el ícono, o el ícono con su nombre cuando está elegida. */
@Composable
private fun TabPill(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tabBackground",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 18.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            TabIcon(tab, contentDescription = if (selected) null else tab.label)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

@Composable
private fun PhotosTab(
    padding: PaddingValues,
    gridState: LazyGridState,
    photos: List<Photo>,
    visible: List<Photo>,
    albums: List<Album>,
    currentAlbum: Album?,
    favoritesOnly: Boolean,
    memories: List<Photo>,
    importing: ImportProgress?,
    googlePhotos: String?,
    update: UpdateState?,
    onUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onReopenGooglePhotos: () -> Unit,
    onCheckGooglePhotos: () -> Unit,
    onCancelGooglePhotos: () -> Unit,
    onGooglePhotosInBrowser: () -> Unit,
    selection: Set<String>,
    sortByTaken: Boolean,
    gridSize: Int,
    onPickPhotos: () -> Unit,
    onGoToWidgets: () -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onOpenFavorites: () -> Unit,
    onNewAlbum: () -> Unit,
    onSort: (Boolean) -> Unit,
    onGridSize: (Int) -> Unit,
    onRenameAlbum: (Album) -> Unit,
    onDeleteAlbum: (Album) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectRange: (Set<String>, List<String>) -> Unit,
    searching: Boolean,
    query: String,
    onOpenSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onSlideshow: (List<Photo>, WidgetSource) -> Unit,
) {
    val selecting = selection.isNotEmpty()
    val title = when {
        favoritesOnly -> "Favoritas"
        currentAlbum != null -> currentAlbum.name
        photos.isEmpty() -> "Fototin"
        else -> "Fotos"
    }
    val source = when {
        favoritesOnly -> WidgetSource.Favorites
        currentAlbum != null -> WidgetSource.FromAlbum(currentAlbum.id)
        else -> WidgetSource.All
    }
    val side = Modifier.padding(horizontal = 16.dp)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Posición de cada foto en la lista y, para saltar por fecha, dónde empieza cada año.
    val positions = remember(visible) { visible.withIndex().associate { (i, photo) -> photo.id to i } }
    val groups = if (sortByTaken) visible.groupBy { yearOf(it.takenAt) }.toList() else listOf(null to visible)
    val groupStarts = remember(visible, sortByTaken) {
        if (!sortByTaken) emptyList() else groups.runningFold(0) { start, (_, group) -> start + group.size }.dropLast(1)
    }
    // Índice en la grilla de la foto número [position]: lo que hay antes (títulos, tarjetas) más
    // un separador por cada año que empezó hasta ahí.
    val gridIndexOf: (Int) -> Int = { position ->
        val total = gridState.layoutInfo.totalItemsCount
        val before = (total - visible.size - groupStarts.size).coerceAtLeast(0)
        before + groupStarts.count { it <= position } + position
    }

    // Elegir varias arrastrando el dedo, como en las galerías: se mantiene presionada una foto y
    // se desliza; cerca de los bordes la grilla se mueve sola.
    val drag = remember { DragSelect() }
    val currentVisible by rememberUpdatedState(visible)
    val currentPositions by rememberUpdatedState(positions)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnTap by rememberUpdatedState<(String) -> Unit> { id ->
        if (currentSelection.isNotEmpty()) onToggleSelection(id) else onOpenPhoto(id)
    }
    val currentOnToggle by rememberUpdatedState(onToggleSelection)
    val currentOnRange by rememberUpdatedState(onSelectRange)
    fun photoAt(position: Offset): String? {
        val info = gridState.layoutInfo
        // Las posiciones de la grilla se cuentan desde donde termina el margen de arriba.
        val y = position.y.toInt() + info.viewportStartOffset
        val x = position.x.toInt()
        return info.visibleItemsInfo.firstOrNull { item ->
            y in item.offset.y until item.offset.y + item.size.height &&
                x in item.offset.x until item.offset.x + item.size.width
        }?.key?.let { key -> (key as? String)?.takeIf { it in currentPositions } }
    }
    fun extendDrag() {
        val id = photoAt(drag.position) ?: return
        val end = currentPositions[id] ?: return
        if (drag.start < 0 || (end == drag.start && !drag.moved)) return
        drag.moved = true
        val range = currentVisible.subList(minOf(drag.start, end), maxOf(drag.start, end) + 1).map { it.id }
        currentOnRange(drag.base, range)
    }
    LaunchedEffect(drag.speed) {
        while (drag.speed != 0f) {
            gridState.scrollBy(drag.speed)
            extendDrag()
            delay(16)
        }
    }
    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startId = photoAt(down.position) ?: return@awaitEachGesture
            // Hasta que se cumple el tiempo de mantener presionado: si se suelta es un toque, si se
            // mueve es la grilla que se desplaza.
            val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result: Boolean? = null
                while (result == null) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    result = when {
                        change == null -> false
                        change.changedToUp() -> !change.isConsumed
                        change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> false
                        else -> null
                    }
                }
                result
            }
            if (tapped != null) {
                if (tapped) currentOnTap(startId)
                return@awaitEachGesture
            }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.base = currentSelection - startId
            drag.start = currentPositions[startId] ?: -1
            drag.moved = false
            drag.position = down.position
            currentOnToggle(startId)
            val edge = 72.dp.toPx()
            while (true) {
                // Se toman los movimientos antes que la grilla, para que no se desplace mientras se elige.
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                drag.position = change.position
                drag.speed = when {
                    change.position.y < edge -> -((edge - change.position.y) / edge * 24f).coerceIn(4f, 24f).roundToInt().toFloat()
                    change.position.y > size.height - edge ->
                        ((change.position.y - (size.height - edge)) / edge * 24f).coerceIn(4f, 24f).roundToInt().toFloat()
                    else -> 0f
                }
                extendDrag()
            }
            drag.speed = 0f
            drag.start = -1
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Las fotos van de borde a borde, con separaciones finas; los títulos y tarjetas llevan margen.
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(GRID_SIZES[gridSize.coerceIn(GRID_SIZES.indices)]),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            modifier = Modifier.fillMaxSize().then(gestures),
        ) {
            if (!selecting) {
                item(span = FULL_LINE) {
                    LargeHeader(
                        title = title,
                        subtitle = if (query.isNotBlank()) resultCount(visible.size) else headerSubtitle(visible, sortByTaken),
                        onSearch = onOpenSearch.takeIf { !searching && photos.isNotEmpty() },
                        album = currentAlbum,
                        showActions = visible.isNotEmpty() || currentAlbum != null,
                        sortByTaken = sortByTaken,
                        gridSize = gridSize,
                        onSort = onSort,
                        onGridSize = onGridSize,
                        onRename = onRenameAlbum,
                        onDelete = onDeleteAlbum,
                        onSlideshow = { onSlideshow(visible, source) }.takeIf { visible.isNotEmpty() },
                    )
                }
                if (searching) {
                    item(key = "search", span = FULL_LINE) {
                        SearchField(query, onQuery = onQuery, onClose = onCloseSearch, modifier = side.padding(bottom = 8.dp))
                    }
                }
                if (memories.isNotEmpty() && currentAlbum == null && !favoritesOnly && !searching) {
                    item(span = FULL_LINE) {
                        // Tocar los recuerdos de hoy los pasa como presentación.
                        MemoriesHero(memories, onPlay = { onSlideshow(memories, WidgetSource.Memories) }, modifier = side)
                    }
                }
            }
            if (photos.isNotEmpty() && !searching) {
                item(span = FULL_LINE) {
                    Collections(
                        photos = photos,
                        albums = albums,
                        current = currentAlbum,
                        favoritesOnly = favoritesOnly,
                        onOpen = onOpenAlbum,
                        onFavorites = onOpenFavorites,
                        onNew = onNewAlbum,
                    )
                }
            }
            update?.let { state ->
                item(span = FULL_LINE) {
                    UpdateBanner(state, onUpdate = onUpdate, onDismiss = onDismissUpdate, modifier = side)
                }
            }
            googlePhotos?.let { status ->
                item(span = FULL_LINE) {
                    GooglePhotosStatus(
                        status,
                        onCheck = onCheckGooglePhotos,
                        onReopen = onReopenGooglePhotos,
                        onBrowser = onGooglePhotosInBrowser,
                        onCancel = onCancelGooglePhotos,
                        modifier = side,
                    )
                }
            }
            importing?.let { progress ->
                item(span = FULL_LINE) { ImportingRow(progress, modifier = side) }
            }
            if (visible.isEmpty() && importing == null) {
                item(span = FULL_LINE) {
                    when {
                        query.isNotBlank() -> EmptyCollection(
                            "No hay fotos que coincidan con «${query.trim()}». Probá con una palabra de una nota, " +
                                "un mes (por ejemplo «marzo») o un año.",
                        )
                        searching -> EmptyCollection("Escribí una palabra de una nota, un mes o un año.")
                        favoritesOnly -> EmptyCollection(
                            "Todavía no tenés favoritas. Abrí una foto y tocá el corazón para sumarla acá.",
                        )
                        currentAlbum != null -> EmptyCollection(
                            "Este álbum está vacío. Tocá + para agregar fotos, o en «Todas» mantené presionadas " +
                                "algunas y sumalas desde arriba.",
                        )
                        else -> Box(side) { GettingStarted(onPickPhotos, onGoToWidgets) }
                    }
                }
            }
            if (visible.isNotEmpty() && !selecting) {
                item(span = FULL_LINE) { Spacer(Modifier.height(8.dp)) }
            }
            // Ordenadas por fecha de la foto, con un separador por año.
            groups.forEach { (year, group) ->
                if (year != null) {
                    item(key = "year-$year", span = FULL_LINE) {
                        Row(side.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                            Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                photoCount(group.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                }
                items(group, key = { it.id }) { photo ->
                    // Los toques los atiende la grilla (ver arriba), para poder elegir arrastrando.
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

        // Al bajar, el título grande pasa a una barra compacta y translúcida. Tocarla vuelve arriba.
        val collapsed by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
        if (visible.size >= SCRUBBER_MIN_PHOTOS) {
            DateScrubber(
                gridState = gridState,
                visible = visible,
                gridIndexOf = gridIndexOf,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = padding.calculateTopPadding() + 64.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            )
        }
        AnimatedVisibility(
            visible = collapsed && !selecting,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            CompactHeader(title, onClick = { scope.launch { gridState.animateScrollToItem(0) } })
        }
    }
}

/** Separación entre fotos de la grilla. */
private val GRID_GAP = 2.dp

/** Títulos y tarjetas ocupan todo el ancho de la grilla. */
private val FULL_LINE: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** Con menos fotos que estas no hace falta la barra para saltar de fecha. */
private const val SCRUBBER_MIN_PHOTOS = 40

/** Estado de la elección arrastrando el dedo. */
private class DragSelect {
    /** Lo que ya estaba elegido antes de empezar a arrastrar. */
    var base: Set<String> = emptySet()
    /** Posición (en la lista de fotos) de la foto donde se empezó; -1 si no se está arrastrando. */
    var start = -1
    var moved = false
    var position = Offset.Zero
    /** Velocidad del desplazamiento automático cerca de los bordes, en píxeles por cuadro. */
    var speed by mutableFloatStateOf(0f)
}

private fun resultCount(count: Int) = when (count) {
    0 -> "Sin resultados"
    1 -> "1 foto encontrada"
    else -> "$count fotos encontradas"
}

/** Campo de búsqueda: palabras de la nota, un mes o un año. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Buscar por nota, mes o año") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar búsqueda") }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier.fillMaxWidth().focusRequester(focus),
    )
}

/**
 * Barra para saltar por fecha, en el borde derecho. Aparece al desplazarse; al arrastrarla muestra
 * el mes y el año de las fotos a las que se va a llegar.
 */
@Composable
private fun DateScrubber(
    gridState: LazyGridState,
    visible: List<Photo>,
    gridIndexOf: (Int) -> Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    val scrolling = gridState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true else {
            delay(1500)
            shown = false
        }
    }
    val scrollFraction by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            if (total <= 1) 0f else gridState.firstVisibleItemIndex.toFloat() / (total - 1)
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "scrubber")
    if (alpha == 0f) return
    val currentVisible by rememberUpdatedState(visible)
    val currentGridIndexOf by rememberUpdatedState(gridIndexOf)
    BoxWithConstraints(modifier.fillMaxHeight().width(160.dp).graphicsLayer { this.alpha = alpha }) {
        val thumbHeight = 48.dp
        val track = constraints.maxHeight.toFloat() - with(LocalDensity.current) { thumbHeight.toPx() }
        val fraction = if (dragging) dragFraction else scrollFraction.coerceIn(0f, 1f)
        val position = (fraction * (visible.size - 1)).roundToInt().coerceIn(0, visible.lastIndex)
        var lastPosition by remember { mutableIntStateOf(-1) }
        fun jumpTo(y: Float, half: Float) {
            dragFraction = if (track <= 0f) 0f else ((y - half) / track).coerceIn(0f, 1f)
            val target = (dragFraction * (currentVisible.size - 1)).roundToInt().coerceIn(0, currentVisible.lastIndex)
            if (target != lastPosition) {
                lastPosition = target
                scope.launch { gridState.scrollToItem(currentGridIndexOf(target)) }
            }
        }
        Row(
            Modifier.offset { IntOffset(0, (fraction * track).roundToInt()) }.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragging) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        monthYear(visible[position].takenAt),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(width = 32.dp, height = thumbHeight)
                    .pointerInput(Unit) {
                        val half = thumbHeight.toPx() / 2
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                lastPosition = -1
                                dragFraction = scrollFraction.coerceIn(0f, 1f)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                // La posición del dedo es relativa a la manija, que se mueve con él.
                                jumpTo(dragFraction * track + change.position.y, half)
                            },
                        )
                    }
                    .semantics { contentDescription = "Saltar por fecha" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private val MONTH_YEAR = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale("es"))

private fun monthYear(millis: Long): String = synchronized(MONTH_YEAR) { MONTH_YEAR.format(java.util.Date(millis)) }

private fun photoCount(count: Int) = if (count == 1) "1 foto" else "$count fotos"

/** «128 fotos · 2019 – 2025». */
private fun headerSubtitle(visible: List<Photo>, sortByTaken: Boolean): String? {
    if (visible.isEmpty()) return null
    val count = photoCount(visible.size)
    if (!sortByTaken) return count
    val years = visible.map { yearOf(it.takenAt) }
    val first = years.min()
    val last = years.max()
    return if (first == last) "$count · $first" else "$count · $first – $last"
}

/** Título grande en negrita, con la presentación y las opciones a mano. */
@Composable
private fun LargeHeader(
    title: String,
    subtitle: String?,
    onSearch: (() -> Unit)?,
    album: Album?,
    showActions: Boolean,
    sortByTaken: Boolean,
    gridSize: Int,
    onSort: (Boolean) -> Unit,
    onGridSize: (Int) -> Unit,
    onRename: (Album) -> Unit,
    onDelete: (Album) -> Unit,
    onSlideshow: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Buscar")
            }
        }
        if (onSlideshow != null) {
            FilledTonalIconButton(onClick = onSlideshow) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Ver presentación")
            }
        }
        if (showActions) {
            // Orden, tamaño y opciones del álbum en un solo menú, para no llenar la pantalla de botones.
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
}

/** Barra fina que queda arriba al bajar por la grilla. */
@Composable
private fun CompactHeader(title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.statusBarsPadding().height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Recuerdos de hoy en grande, con la foto de fondo y el botón para verlos como presentación. */
@Composable
private fun MemoriesHero(memories: List<Photo>, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val cover = memories.first()
    Box(
        modifier
            .fillMaxWidth()
            .height(228.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClickLabel = "Ver presentación", onClick = onPlay),
    ) {
        AsyncImage(
            model = cover.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Se recorta respetando las caras, igual que en los widgets.
            alignment = BiasAlignment(cover.focusX * 2 - 1, cover.focusY * 2 - 1),
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "RECUERDOS DE HOY",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp,
                )
                Text(yearsAgo(memories), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(photoCount(memories.size), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Surface(onClick = onPlay, shape = CircleShape, color = Color.White, contentColor = Color.Black, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Ver presentación", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** «Hace 3 años», o «Hace 1, 4 y 6 años» si hay recuerdos de varios años. */
private fun yearsAgo(memories: List<Photo>): String {
    val now = yearOf(System.currentTimeMillis())
    val ago = memories.map { now - yearOf(it.takenAt) }.filter { it > 0 }.distinct().sorted()
    return when {
        ago.isEmpty() -> "Un día como hoy"
        ago.size == 1 -> if (ago[0] == 1) "Hace 1 año" else "Hace ${ago[0]} años"
        else -> "Hace ${ago.dropLast(1).joinToString(", ")} y ${ago.last()} años"
    }
}

/** Colecciones con portada: todas, favoritas y cada álbum. Elegir una filtra la grilla. */
@Composable
private fun Collections(
    photos: List<Photo>,
    albums: List<Album>,
    current: Album?,
    favoritesOnly: Boolean,
    onOpen: (String?) -> Unit,
    onFavorites: () -> Unit,
    onNew: () -> Unit,
) {
    val byId = remember(photos) { photos.associateBy { it.id } }
    val favorites = remember(photos) { photos.filter { it.favorite } }
    Column(Modifier.padding(top = 8.dp)) {
        Text("Colecciones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "all") {
                CollectionCard("Todas", photos.size, photos.lastOrNull(), selected = current == null && !favoritesOnly) { onOpen(null) }
            }
            if (favorites.isNotEmpty() || favoritesOnly) {
                item(key = "favorites") {
                    CollectionCard("Favoritas", favorites.size, favorites.lastOrNull(), selected = favoritesOnly, favorite = true, onClick = onFavorites)
                }
            }
            rowItems(albums, key = { it.id }) { album ->
                val inAlbum = album.photoIds.mapNotNull { byId[it] }
                CollectionCard(album.name, inAlbum.size, inAlbum.lastOrNull(), selected = current?.id == album.id) { onOpen(album.id) }
            }
            item(key = "new") { NewCollectionCard(onNew) }
        }
    }
}

private val COLLECTION_SIZE = 112.dp

@Composable
private fun CollectionCard(
    name: String,
    count: Int,
    cover: Photo?,
    selected: Boolean,
    favorite: Boolean = false,
    onClick: () -> Unit,
) {
    val border by animateDpAsState(if (selected) 3.dp else 0.dp, label = "collectionBorder")
    Column(
        Modifier
            .width(COLLECTION_SIZE)
            .clip(RoundedCornerShape(22.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
    ) {
        Box(
            Modifier
                .size(COLLECTION_SIZE)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Un borde de 0.dp se dibujaría como línea fina: sin elegir, no hay borde.
                .then(if (border > 0.dp) Modifier.border(border, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)) else Modifier),
        ) {
            cover?.let {
                AsyncImage(
                    model = it.thumb.takeIf { thumb -> thumb.exists() } ?: it.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().padding(border).clip(RoundedCornerShape(22.dp - border)),
                )
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(20.dp),
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun NewCollectionCard(onClick: () -> Unit) {
    Column(Modifier.width(COLLECTION_SIZE).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(COLLECTION_SIZE)
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text("Nuevo álbum", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp))
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
private fun EmptyCollection(text: String) {
    Text(
        text,
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
private fun ImportingRow(progress: ImportProgress, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) {
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

/** Aviso de versión nueva, con sus novedades y el avance de la descarga. */
@Composable
private fun UpdateBanner(state: UpdateState, onUpdate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Hay una versión nueva: ${state.release.versionName}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            state.release.notes.take(3).forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                is UpdateState.Downloading -> {
                    Text("Descargando… ${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                }
                is UpdateState.Available -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onUpdate) { Text("Actualizar") }
                    TextButton(onClick = onDismiss) { Text("Ahora no") }
                }
            }
        }
    }
}

/** Mientras el usuario elige fotos en Google Fotos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GooglePhotosStatus(
    status: String,
    onCheck: () -> Unit,
    onReopen: () -> Unit,
    onBrowser: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = onCheck) { Text("Ya elegí las fotos") }
                TextButton(onClick = onReopen) { Text("Abrir Google Fotos") }
                TextButton(onClick = onBrowser) { Text("Elegir desde el navegador") }
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
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
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, label = "inset")
    val corner by animateDpAsState(if (selected) 16.dp else 3.dp, label = "corner")
    Box(
        modifier
            .fillMaxWidth()
            // Cuadradas, como en las galerías: la grilla queda pareja y entran más.
            .aspectRatio(1f)
            // Los toques llegan desde la grilla; esto es para TalkBack.
            .semantics(mergeDescendants = true) {
                this.selected = selected
                onClick(label = if (selecting) "Elegir" else "Abrir") {
                    if (selecting) onToggle() else onOpen()
                    true
                }
                onLongClick(label = "Elegir varias") {
                    onToggle()
                    true
                }
            },
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
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp),
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
