package app.fototin.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fototin.data.Album
import app.fototin.data.AlbumRepository
import app.fototin.data.ImportResult
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.PhotoRepository
import app.fototin.data.Settings
import app.fototin.widget.PlacedWidget
import app.fototin.widget.RotationScheduler
import app.fototin.widget.WidgetSupport
import kotlinx.coroutines.Dispatchers
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
            _messages.tryEmit(importMessage(result))
        }
    }

    fun openPhoto(id: String?) {
        _openPhotoId.value = id
    }

    fun setNote(id: String, note: String) = updatePhoto { repository.update(id, note = note) }

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
        viewModelScope.launch {
            repository.remove(listOf(id))
            refresh()
            _messages.tryEmit("Foto borrada de Fototin")
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

    /** Borra las fotos seleccionadas de Fototin (las originales no se tocan). */
    fun deleteSelected() {
        val count = _selection.value.size
        withSelection { ids -> repository.remove(ids) }
        if (count > 0) _messages.tryEmit(if (count == 1) "Foto borrada de Fototin" else "$count fotos borradas de Fototin")
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
