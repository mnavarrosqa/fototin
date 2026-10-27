package app.fototin.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fototin.data.Album
import app.fototin.data.AlbumRepository
import app.fototin.data.BackupManager
import app.fototin.data.GooglePhotosPicker
import app.fototin.data.ImportResult
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.PhotoRepository
import app.fototin.data.RemovedPhotos
import app.fototin.data.Settings
import app.fototin.widget.PlacedWidget
import app.fototin.widget.RotationScheduler
import app.fototin.widget.WidgetSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Fotos recién traídas que se pueden guardar juntas como álbum. */
class AlbumOffer(val message: String, val ids: List<String>)

/** Un borrado reciente que todavía se puede deshacer. */
class UndoDelete(val message: String, val removed: RemovedPhotos)

/** Progreso de una importación: [done] de [total] fotos procesadas. */
data class ImportProgress(val done: Int, val total: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PhotoRepository(app)
    private val albumRepository = AlbumRepository(app)
    private val settings = Settings(app)

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Álbum que se está mirando; null = todas las fotos. */
    private val _currentAlbumId = MutableStateFlow<String?>(null)
    val currentAlbum: StateFlow<Album?> = combine(_albums, _currentAlbumId) { albums, id ->
        albums.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sortByTaken = MutableStateFlow(settings.sortByTaken)
    val sortByTaken: StateFlow<Boolean> = _sortByTaken.asStateFlow()

    /** Fotos del álbum actual (o todas): las más nuevas primero, por fecha de la foto o de agregada. */
    val visiblePhotos: StateFlow<List<Photo>> = combine(_photos, currentAlbum, _sortByTaken) { photos, album, byTaken ->
        val inView = if (album == null) {
            photos
        } else {
            val byId = photos.associateBy { it.id }
            album.photoIds.mapNotNull { byId[it] }
        }
        if (byTaken) inView.sortedByDescending { it.takenAt } else inView.asReversed()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Foto abierta en la pantalla de detalle. */
    private val _openPhotoId = MutableStateFlow<String?>(null)
    val openPhoto: StateFlow<Photo?> = combine(_photos, _openPhotoId) { photos, id ->
        photos.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _widgets = MutableStateFlow<List<PlacedWidget>>(emptyList())
    val widgets: StateFlow<List<PlacedWidget>> = _widgets.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    /** Buscando fotos repetidas. */
    private val _cleaning = MutableStateFlow(false)
    val cleaning: StateFlow<Boolean> = _cleaning.asStateFlow()

    private val _gridSize = MutableStateFlow(settings.gridSize)
    val gridSize: StateFlow<Int> = _gridSize.asStateFlow()

    private val _dynamicColor = MutableStateFlow(settings.dynamicColor)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /** Borrados que se pueden deshacer desde el aviso de abajo. */
    private val _undo = MutableSharedFlow<UndoDelete>(extraBufferCapacity = 4)
    val undo: SharedFlow<UndoDelete> = _undo.asSharedFlow()

    /** Importaciones recientes que se pueden guardar como álbum. */
    private val _albumOffers = MutableSharedFlow<AlbumOffer>(extraBufferCapacity = 2)
    val albumOffers: SharedFlow<AlbumOffer> = _albumOffers.asSharedFlow()

    /** Mensajes cortos para mostrar abajo de la pantalla. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Fotos de un día como hoy en otros años. */
    val memoriesToday: StateFlow<List<Photo>> = _photos.map { PhotoPool.exactMemories(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _importing = MutableStateFlow<ImportProgress?>(null)
    val importing: StateFlow<ImportProgress?> = _importing.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _interval = MutableStateFlow(settings.intervalMinutes)
    val interval: StateFlow<Int> = _interval.asStateFlow()

    private val _shuffle = MutableStateFlow(settings.shuffle)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    init {
        viewModelScope.launch {
            reload()
            // Fotos de versiones anteriores: miniaturas y encuadre de caras.
            if (repository.completeMissingData()) refresh()
        }
    }

    /** Importa fotos; si se está mirando un álbum, también las suma a ese álbum. */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val albumId = _currentAlbumId.value
        viewModelScope.launch {
            _importing.value = ImportProgress(0, uris.size)
            val result = repository.import(uris) { done, total -> _importing.value = ImportProgress(done, total) }
            if (albumId != null) withContext(Dispatchers.IO) { albumRepository.add(albumId, result.ids) }
            _importing.value = null
            refresh()
            announceImport(result, insideAlbum = albumId != null)
        }
    }

    private var googleJob: Job? = null

    /** Texto de estado mientras se eligen o descargan fotos de Google Fotos; null si no hay nada en curso. */
    private val _googlePhotos = MutableStateFlow<String?>(null)
    val googlePhotos: StateFlow<String?> = _googlePhotos.asStateFlow()

    /** Pantallas de Google Fotos que la actividad tiene que abrir. */
    private val _openUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl: SharedFlow<String> = _openUrl.asSharedFlow()

    private var pickerUri: String? = null

    /**
     * Abre la pantalla de Google Fotos para elegir fotos y, cuando el usuario termina, las descarga
     * e importa. [token] es el permiso que dio el usuario (solo lectura de lo que elija).
     */
    fun pickFromGooglePhotos(token: String) {
        googleJob?.cancel()
        val albumId = _currentAlbumId.value
        googleJob = viewModelScope.launch {
            val picker = GooglePhotosPicker(token)
            var sessionId: String? = null
            val cache = File(getApplication<Application>().cacheDir, "gphotos").apply { mkdirs() }
            try {
                _googlePhotos.value = "Abriendo Google Fotos…"
                var session = picker.createSession()
                sessionId = session.id
                pickerUri = session.pickerUri
                _openUrl.tryEmit(session.pickerUri)
                _googlePhotos.value = "Elegí las fotos en Google Fotos y tocá «Listo». Después volvé a Fototin."
                // Google avisa por la sesión cuándo terminó la elección: se consulta cada pocos segundos.
                val deadline = System.currentTimeMillis() + session.timeoutMs
                while (!session.ready) {
                    if (System.currentTimeMillis() > deadline) error("Se venció el tiempo para elegir las fotos")
                    delay(session.pollIntervalMs)
                    session = picker.getSession(session.id)
                }
                val items = picker.listItems(session.id).filter { it.isPhoto }
                if (items.isEmpty()) {
                    _messages.tryEmit("No elegiste fotos")
                    return@launch
                }
                _googlePhotos.value = null
                _importing.value = ImportProgress(0, items.size * 2)
                val downloaded = items.mapIndexedNotNull { i, item ->
                    runCatching { picker.download(item, cache) }.getOrNull()
                        ?.let { Uri.fromFile(it) to item.createdAt }
                        .also { _importing.value = ImportProgress(i + 1, items.size * 2) }
                }
                val takenAt = downloaded.mapNotNull { (uri, at) -> at?.let { uri to it } }.toMap()
                val result = repository.import(downloaded.map { it.first }, takenAt) { done, _ ->
                    _importing.value = ImportProgress(items.size + done, items.size * 2)
                }
                if (albumId != null) withContext(Dispatchers.IO) { albumRepository.add(albumId, result.ids) }
                refresh()
                announceImport(result.copy(failed = result.failed + items.size - downloaded.size), insideAlbum = albumId != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GooglePhotosPicker.PickerException) {
                _messages.tryEmit(
                    when (e.code) {
                        401, 403 -> "Google Fotos no dio permiso. Revisá que tu cuenta sea usuario de prueba del proyecto."
                        else -> "Google Fotos: ${e.message}"
                    },
                )
            } catch (e: Exception) {
                _messages.tryEmit("No se pudieron traer las fotos: ${e.message ?: "sin conexión"}")
            } finally {
                _googlePhotos.value = null
                _importing.value = null
                pickerUri = null
                sessionId?.let { id -> viewModelScope.launch { picker.deleteSession(id) } }
                cache.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /** Vuelve a abrir la pantalla de Google Fotos de la elección en curso. */
    fun reopenGooglePhotos() {
        pickerUri?.let { _openUrl.tryEmit(it) }
    }

    fun cancelGooglePhotos() {
        googleJob?.cancel()
    }

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    fun openPhoto(id: String?) {
        _openPhotoId.value = id
    }

    private val noteSaves = mutableMapOf<String, Job>()

    /** Guarda la nota con una pausa: se llama en cada letra, pero solo escribe cuando se deja de tipear. */
    fun setNote(id: String, note: String) {
        noteSaves[id]?.cancel()
        noteSaves[id] = viewModelScope.launch {
            delay(600)
            repository.update(id, note = note)
            refresh()
            noteSaves.remove(id)
        }
    }

    fun toggleFavorite(photo: Photo) = updatePhoto { repository.update(photo.id, favorite = !photo.favorite) }

    fun setFocus(id: String, x: Float, y: Float) = updatePhoto { repository.update(id, focus = x to y) }

    fun toggleAlbum(photoId: String, album: Album) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (photoId in album.photoIds) {
                    albumRepository.remove(album.id, listOf(photoId))
                } else {
                    albumRepository.add(album.id, listOf(photoId))
                }
            }
            refresh()
        }
    }

    fun deletePhoto(id: String) {
        _openPhotoId.value = null
        delete(setOf(id))
    }

    /** Vuelve a poner las fotos de un borrado reciente. */
    fun undoDelete(removed: RemovedPhotos) {
        viewModelScope.launch {
            repository.restore(removed)
            refresh()
        }
    }

    /** El aviso de "Deshacer" se cerró: ya se pueden borrar los archivos. */
    fun confirmDelete(removed: RemovedPhotos) {
        viewModelScope.launch { repository.purge(removed) }
    }

    private fun delete(ids: Set<String>) {
        viewModelScope.launch {
            val removed = repository.remove(ids)
            refresh()
            val count = removed.size
            _undo.tryEmit(
                UndoDelete(if (count == 1) "Foto borrada de Fototin" else "$count fotos borradas de Fototin", removed),
            )
        }
    }

    /**
     * Busca fotos repetidas ya guardadas y las quita, quedándose con la más antigua de cada grupo.
     * Antes le pasa la marca de favorita, la nota y los álbumes de las copias. Se puede deshacer.
     */
    fun removeDuplicates() {
        if (_cleaning.value) return
        _cleaning.value = true
        viewModelScope.launch {
            val duplicates = repository.duplicatesInLibrary()
            if (duplicates.isEmpty()) {
                _cleaning.value = false
                _messages.tryEmit("No hay fotos repetidas")
                return@launch
            }
            repository.mergeInto(duplicates)
            withContext(Dispatchers.IO) { albumRepository.replacePhotos(duplicates) }
            val removed = repository.remove(duplicates.keys)
            refresh()
            _cleaning.value = false
            val count = removed.size
            _undo.tryEmit(
                UndoDelete(if (count == 1) "Se quitó 1 foto repetida" else "Se quitaron $count fotos repetidas", removed),
            )
        }
    }

    fun selectAll() {
        _selection.value = visiblePhotos.value.mapTo(HashSet()) { it.id }
    }

    fun setGridSize(size: Int) {
        settings.gridSize = size
        _gridSize.value = size
    }

    fun setDynamicColor(enabled: Boolean) {
        settings.dynamicColor = enabled
        _dynamicColor.value = enabled
    }

    fun backup(uri: Uri) = runBackup {
        val count = BackupManager(getApplication()).export(uri)
        "Copia guardada con $count fotos"
    }

    fun restore(uri: Uri) = runBackup {
        val added = BackupManager(getApplication()).restore(uri)
        refresh()
        when (added) {
            0 -> "La copia no tenía fotos nuevas"
            1 -> "Se restauró 1 foto"
            else -> "Se restauraron $added fotos"
        }
    }

    private fun runBackup(action: suspend () -> String) {
        if (_backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch {
            val message = runCatching { action() }.getOrElse { "No se pudo completar: ${it.message ?: "error desconocido"}" }
            _backupBusy.value = false
            _messages.tryEmit(message)
        }
    }

    fun setSortByTaken(enabled: Boolean) {
        settings.sortByTaken = enabled
        _sortByTaken.value = enabled
    }

    /** Vuelve a leer los widgets puestos, por ejemplo al volver a la app. */
    fun refreshWidgets() {
        viewModelScope.launch { _widgets.value = WidgetSupport.placedWidgets(getApplication()) }
    }

    fun advanceWidget(widget: PlacedWidget) {
        viewModelScope.launch {
            WidgetSupport.advance(getApplication(), widget)
            refreshWidgets()
        }
    }

    fun openAlbum(id: String?) {
        _currentAlbumId.value = id
        clearSelection()
    }

    fun toggleSelection(id: String) = _selection.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Borra las fotos seleccionadas de Fototin (las originales no se tocan). Se puede deshacer. */
    fun deleteSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        _selection.value = emptySet()
        delete(ids)
    }

    fun removeSelectedFromAlbum() {
        val albumId = _currentAlbumId.value ?: return
        withSelection { ids -> withContext(Dispatchers.IO) { albumRepository.remove(albumId, ids) } }
    }

    fun addSelectedToAlbum(albumId: String) {
        val name = _albums.value.firstOrNull { it.id == albumId }?.name
        withSelection { ids -> withContext(Dispatchers.IO) { albumRepository.add(albumId, ids) } }
        name?.let { _messages.tryEmit("Agregadas a «$it»") }
    }

    /** Crea un álbum con las fotos seleccionadas (si hay) y lo abre. */
    fun createAlbum(name: String) {
        if (name.isBlank()) return
        val ids = _selection.value
        _selection.value = emptySet()
        viewModelScope.launch {
            val album = withContext(Dispatchers.IO) { albumRepository.create(name, ids) }
            _currentAlbumId.value = album.id
            refresh()
        }
    }

    fun renameAlbum(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { albumRepository.rename(id, name) }
            refresh()
        }
    }

    fun deleteAlbum(id: String) {
        if (_currentAlbumId.value == id) _currentAlbumId.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { albumRepository.delete(id) }
            refresh()
        }
    }

    fun setInterval(minutes: Int) {
        settings.intervalMinutes = minutes
        _interval.value = minutes
        RotationScheduler.schedule(getApplication())
    }

    fun setShuffle(enabled: Boolean) {
        settings.shuffle = enabled
        _shuffle.value = enabled
    }

    private fun updatePhoto(action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            refresh()
        }
    }

    /**
     * Cuenta cómo salió una importación. Si se eligieron varias fotos fuera de un álbum, ofrece
     * guardarlas como álbum: incluye las que ya estaban, así el álbum queda completo.
     */
    private fun announceImport(result: ImportResult, insideAlbum: Boolean) {
        val ids = (result.ids + result.existingIds).distinct()
        if (!insideAlbum && ids.size >= 2) {
            _albumOffers.tryEmit(AlbumOffer(importMessage(result), ids))
        } else {
            _messages.tryEmit(importMessage(result))
        }
    }

    /** Crea un álbum con fotos que se acaban de traer y lo abre. */
    fun createAlbumWith(name: String, ids: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val album = withContext(Dispatchers.IO) { albumRepository.create(name, ids) }
            _currentAlbumId.value = album.id
            refresh()
            _messages.tryEmit("Álbum «${album.name}» creado con ${ids.size} fotos")
        }
    }

    private fun importMessage(result: ImportResult): String {
        val added = result.ids.size
        val parts = buildList {
            add(
                when (added) {
                    0 -> "No se agregaron fotos"
                    1 -> "Se agregó 1 foto"
                    else -> "Se agregaron $added fotos"
                },
            )
            if (result.duplicates > 0) add(if (result.duplicates == 1) "1 ya estaba" else "${result.duplicates} ya estaban")
            if (result.failed > 0) add(if (result.failed == 1) "1 no se pudo abrir" else "${result.failed} no se pudieron abrir")
        }
        return parts.joinToString(" · ")
    }

    private fun withSelection(action: suspend (Set<String>) -> Unit) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        _selection.value = emptySet()
        viewModelScope.launch {
            action(ids)
            refresh()
        }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        _photos.value = repository.list()
        _albums.value = albumRepository.list()
    }

    private suspend fun refresh() {
        reload()
        WidgetSupport.updateAll(getApplication())
        _widgets.value = WidgetSupport.placedWidgets(getApplication())
    }
}
