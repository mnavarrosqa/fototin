package app.fototin.ui

import android.app.Application
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fototin.data.PhotoRepository
import app.fototin.data.Settings
import app.fototin.widget.FotoWidget
import app.fototin.widget.RotationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PhotoRepository(app)
    private val settings = Settings(app)

    private val _photos = MutableStateFlow(repository.list())
    val photos: StateFlow<List<File>> = _photos.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _interval = MutableStateFlow(settings.intervalMinutes)
    val interval: StateFlow<Int> = _interval.asStateFlow()

    private val _shuffle = MutableStateFlow(settings.shuffle)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            repository.import(uris)
            _importing.value = false
            refresh()
        }
    }

    fun remove(file: File) {
        viewModelScope.launch {
            repository.remove(file)
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

    private suspend fun refresh() {
        _photos.value = repository.list()
        FotoWidget().updateAll(getApplication())
    }
}
