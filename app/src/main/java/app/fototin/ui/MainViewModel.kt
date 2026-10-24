package app.fototin.ui

import android.app.Application
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fototin.data.Album
import app.fototin.data.AlbumRepository
import app.fototin.data.Photo
import app.fototin.data.PhotoPool
import app.fototin.data.PhotoRepository
import app.fototin.data.Settings
import app.fototin.widget.FotoWidget
import app.fototin.widget.RotationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Fotos del álbum actual (o todas), las más nuevas primero. */
    val visiblePhotos: StateFlow<List<Photo>> = combine(_photos, currentAlbum) { photos, album ->
        if (album == null) {
            photos.asReversed()
        } else {
            val byId = photos.associateBy { it.id }
            album.photoIds.mapNotNull { byId[it] }.asReversed()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            repository.ensureThumbnails()
        }
    }

    /** Importa fotos; si se está mirando un álbum, también las suma a ese álbum. */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val albumId = _currentAlbumId.value
        viewModelScope.launch {
            _importing.value = ImportProgress(0, uris.size)
            val ids = repository.import(uris) { done, total -> _importing.value = ImportProgress(done, total) }
            if (albumId != null) withContext(Dispatchers.IO) { albumRepository.add(albumId, ids) }
            _importing.value = null
            refresh()
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
    fun deleteSelected() = withSelection { ids -> repository.remove(ids) }

    fun removeSelectedFromAlbum() {
        val albumId = _currentAlbumId.value ?: return
        withSelection { ids -> withContext(Dispatchers.IO) { albumRepository.remove(albumId, ids) } }
    }

    fun addSelectedToAlbum(albumId: String) =
        withSelection { ids -> withContext(Dispatchers.IO) { albumRepository.add(albumId, ids) } }

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
        FotoWidget().updateAll(getApplication())
    }
}
