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
import app.fototin.data.PhotoSearch
import app.fototin.data.PhotoPool
import app.fototin.data.PhotoRepository
import app.fototin.data.RemovedPhotos
import app.fototin.data.Settings
import app.fototin.data.WidgetSource
import app.fototin.notify.MemoriesNotifier
import app.fototin.update.Release
import app.fototin.update.Updater
import app.fototin.widget.PlacedWidget
import app.fototin.widget.RotationScheduler
import app.fototin.widget.WidgetSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import android.util.Log
import java.io.File
import java.io.IOException

/** Una versión nueva: disponible para instalar o descargándose. */
sealed interface UpdateState {
    val release: Release

    data class Available(override val release: Release) : UpdateState
    data class Downloading(override val release: Release, val progress: Float) : UpdateState
}

private const val TAG = "Fototin"

private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

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

    /** Mirando solo las favoritas (una colección fija, no un álbum). */
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    /** Búsqueda abierta y lo que se escribió (palabras de la nota, mes o año). */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Fotos del álbum actual (o todas): las más nuevas primero, por fecha de la foto o de agregada. */
    val visiblePhotos: StateFlow<List<Photo>> = combine(_photos, currentAlbum, _sortByTaken, _favoritesOnly, _query) { photos, album, byTaken, favorites, query ->
        val inView = if (favorites) {
            photos.filter { it.favorite }
        } else if (album == null) {
            photos
        } else {
            val byId = photos.associateBy { it.id }
            album.photoIds.mapNotNull { byId[it] }
        }
        val found = PhotoSearch.filter(inView, query)
        if (byTaken) found.sortedByDescending { it.takenAt } else found.asReversed()
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

    private val updater = Updater(app)

    /** Estado de la actualización: null si no hay versión nueva (o se descartó el aviso). */
    private val _update = MutableStateFlow<UpdateState?>(null)
    val update: StateFlow<UpdateState?> = _update.asStateFlow()

    private val _checkingUpdates = MutableStateFlow(false)
    val checkingUpdates: StateFlow<Boolean> = _checkingUpdates.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(settings.autoCheckUpdates)
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    val installedVersion: String = runCatching { updater.installedVersionName }.getOrDefault("")

    /** Novedades de la versión recién instalada (nombre y renglones), para mostrarlas una vez. */
    private val _whatsNew = MutableStateFlow(runCatching { settings.takeReleaseNotes(updater.installedVersionCode) }.getOrNull())
    val whatsNew: StateFlow<Pair<String, List<String>>?> = _whatsNew.asStateFlow()

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
        // Buscar versión nueva al abrir, como mucho cada 6 horas.
        if (settings.autoCheckUpdates && System.currentTimeMillis() - settings.lastUpdateCheck > UPDATE_CHECK_INTERVAL_MS) {
            checkForUpdates(manual = false)
        }
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

    /** Cuenta de Google con la que se autorizó la elección (si Google la informa). */
    private val _googleAccount = MutableStateFlow<String?>(null)
    val googleAccount: StateFlow<String?> = _googleAccount.asStateFlow()

    /** Pantallas de Google Fotos que la actividad tiene que abrir. */
    private val _openUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl: SharedFlow<String> = _openUrl.asSharedFlow()

    /** La misma pantalla, pero forzando el navegador (por si la app de Google Fotos usa otra cuenta). */
    private val _openInBrowser = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openInBrowser: SharedFlow<String> = _openInBrowser.asSharedFlow()

    private var pickerUri: String? = null

    /**
     * Toques para consultar la sesión ya mismo (al volver a la app o con "Ya elegí las fotos").
     * Es un canal y no un flujo compartido: si el toque llega mientras se está consultando a
     * Google, queda guardado en vez de perderse.
     */
    private val pickerPokes = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var manualPickerCheck = false
    @Volatile private var resumedPickerCheck = false

    /** La app necesita un permiso nuevo de Google para retomar una elección que quedó pendiente. */
    private val _needsGoogleAuth = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val needsGoogleAuth: SharedFlow<Unit> = _needsGoogleAuth.asSharedFlow()

    /**
     * Abre la pantalla de Google Fotos para elegir fotos y, cuando el usuario termina, las descarga
     * e importa. [token] es el permiso que dio el usuario (solo lectura de lo que elija).
     */
    fun pickFromGooglePhotos(token: String, account: String?) {
        _googleAccount.value = account
        settings.clearPendingPicker()
        runGooglePhotos(token, resume = null)
    }

    /** Retoma la elección que quedó pendiente si Android cerró la app mientras estaba en Google Fotos. */
    fun resumeGooglePhotos(token: String, account: String?) {
        _googleAccount.value = account
        val pending = settings.pendingPicker() ?: return
        runGooglePhotos(token, resume = pending)
    }

    /** Al volver a la app: consultar ya mismo, o retomar una elección pendiente. */
    fun onAppResumed() {
        if (googleJob?.isActive == true) {
            resumedPickerCheck = true
            pickerPokes.trySend(Unit)
        } else if (settings.pendingPicker() != null) {
            _needsGoogleAuth.tryEmit(Unit)
        }
    }

    /** Botón "Ya elegí las fotos": consulta ahora y, si Google todavía no lo registró, lo explica. */
    fun checkGooglePhotosNow() {
        manualPickerCheck = true
        pickerPokes.trySend(Unit)
    }

    private fun runGooglePhotos(token: String, resume: Settings.PendingPicker?) {
        googleJob?.cancel()
        val albumId = resume?.albumId ?: _currentAlbumId.value
        googleJob = viewModelScope.launch {
            // Un toque viejo no debe adelantar la primera consulta.
            pickerPokes.tryReceive()
            val picker = GooglePhotosPicker(token)
            var sessionId: String? = resume?.sessionId
            var finished = false
            val cache = File(getApplication<Application>().cacheDir, "gphotos").apply { mkdirs() }
            try {
                var session = if (resume == null) {
                    _googlePhotos.value = "Abriendo Google Fotos…"
                    picker.createSession().also { created ->
                        // Se guarda por si Android cierra Fototin mientras se elige en Google Fotos.
                        settings.savePendingPicker(Settings.PendingPicker(created.id, created.pickerUri, albumId, System.currentTimeMillis()))
                        _openUrl.tryEmit(created.pickerUri)
                    }
                } else {
                    _googlePhotos.value = "Retomando la elección de Google Fotos…"
                    picker.getSession(resume.sessionId)
                }
                sessionId = session.id
                pickerUri = session.pickerUri.ifEmpty { resume?.pickerUri.orEmpty() }.takeIf { it.isNotEmpty() }
                // Lo que pasó antes de abrir Google Fotos (como volver de la pantalla de permiso) no cuenta.
                pickerPokes.tryReceive()
                manualPickerCheck = false
                resumedPickerCheck = false
                _googlePhotos.value = waitingText()
                // Google avisa por la sesión cuándo terminó la elección. Se consulta cada pocos
                // segundos, y enseguida al volver a la app o al tocar "Ya elegí las fotos".
                val deadline = System.currentTimeMillis() + session.timeoutMs
                while (!session.ready) {
                    if (System.currentTimeMillis() > deadline) error("Se venció el tiempo para elegir las fotos")
                    withTimeoutOrNull(session.pollIntervalMs) { pickerPokes.receive() }
                    val asked = manualPickerCheck || resumedPickerCheck
                    if (asked) _googlePhotos.value = "Consultando a Google Fotos…"
                    // Al volver de Google Fotos la red puede tardar en reconectarse: un corte no
                    // cancela la elección, se vuelve a consultar en la próxima vuelta.
                    session = try {
                        picker.getSession(session.id)
                    } catch (e: GooglePhotosPicker.PickerException) {
                        throw e
                    } catch (e: IOException) {
                        Log.w(TAG, "Sin conexión al consultar la sesión", e)
                        _googlePhotos.value = "Sin conexión con Google Fotos. Reintentando…"
                        manualPickerCheck = false
                        resumedPickerCheck = false
                        continue
                    }
                    Log.d(TAG, "Sesión ${session.id}: elegidas=${session.ready}")
                    if (session.ready) break
                    if (!session.ready && asked) {
                        // Volvió a la app (o tocó el botón) y Google todavía no tiene la elección:
                        // casi siempre es porque Google Fotos está abierto con otra cuenta.
                        _googlePhotos.value = notReadyText()
                        if (manualPickerCheck) _messages.tryEmit("Google Fotos todavía no registró tu elección")
                    }
                    manualPickerCheck = false
                    resumedPickerCheck = false
                }
                _googlePhotos.value = "Trayendo las fotos elegidas…"
                val items = retryingNetwork { picker.listItems(session.id) }.filter { it.isPhoto }
                Log.d(TAG, "Fotos elegidas: ${items.size}")
                if (items.isEmpty()) {
                    finished = true
                    _messages.tryEmit("No elegiste fotos (los videos no se agregan)")
                    return@launch
                }
                _googlePhotos.value = null
                _importing.value = ImportProgress(0, items.size * 2)
                val downloaded = items.mapIndexedNotNull { i, item ->
                    runCatching { retryingNetwork { picker.download(item, cache) } }
                        .onFailure { Log.w(TAG, "No se pudo descargar ${item.id}", it) }
                        .getOrNull()
                        ?.let { Uri.fromFile(it) to item.createdAt }
                        .also { _importing.value = ImportProgress(i + 1, items.size * 2) }
                }
                if (downloaded.isEmpty()) error("no se pudo descargar ninguna foto")
                val takenAt = downloaded.mapNotNull { (uri, at) -> at?.let { uri to it } }.toMap()
                val result = repository.import(downloaded.map { it.first }, takenAt) { done, _ ->
                    _importing.value = ImportProgress(items.size + done, items.size * 2)
                }
                if (albumId != null) withContext(Dispatchers.IO) { albumRepository.add(albumId, result.ids) }
                refresh()
                finished = true
                announceImport(result.copy(failed = result.failed + items.size - downloaded.size), insideAlbum = albumId != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GooglePhotosPicker.PickerException) {
                finished = true
                Log.w(TAG, "Error de Google Fotos ${e.code}", e)
                _messages.tryEmit(
                    when (e.code) {
                        401, 403 -> "Google Fotos no dio permiso (${e.code}). Revisá que tu cuenta sea usuario de prueba del proyecto."
                        404 -> "La elección de Google Fotos venció. Volvé a intentarlo."
                        else -> "Google Fotos (${e.code}): ${e.message}"
                    },
                )
            } catch (e: IOException) {
                // Sin conexión: la elección queda guardada y se retoma al volver a abrir Fototin.
                Log.w(TAG, "Sin conexión con Google Fotos", e)
                _messages.tryEmit("No hay conexión con Google Fotos. Cuando vuelva, abrí Fototin de nuevo y se retoma la elección.")
            } catch (e: Exception) {
                finished = true
                Log.w(TAG, "No se pudieron traer las fotos", e)
                _messages.tryEmit("No se pudieron traer las fotos: ${e.message ?: "sin conexión"}")
            } finally {
                _googlePhotos.value = null
                _importing.value = null
                pickerUri = null
                manualPickerCheck = false
                resumedPickerCheck = false
                // Si la app se cerró a mitad de camino, la sesión queda guardada para retomarla.
                if (finished) {
                    settings.clearPendingPicker()
                    sessionId?.let { id -> viewModelScope.launch { picker.deleteSession(id) } }
                }
                cache.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /** Reintenta [block] si falla la red (no si Google responde con un error), esperando cada vez más. */
    private suspend fun <T> retryingNetwork(times: Int = 4, block: suspend () -> T): T {
        var wait = 1_000L
        repeat(times - 1) {
            try {
                return block()
            } catch (e: GooglePhotosPicker.PickerException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Falló la red, reintentando en $wait ms", e)
                delay(wait)
                wait *= 2
            }
        }
        return block()
    }

    /** Vuelve a abrir la pantalla de Google Fotos de la elección en curso. */
    fun reopenGooglePhotos() {
        pickerUri?.let { _openUrl.tryEmit(it) }
    }

    /** Abre la elección en el navegador, donde se ve y se puede cambiar la cuenta de Google. */
    fun openGooglePhotosInBrowser() {
        pickerUri?.let { _openInBrowser.tryEmit(it) }
    }

    private fun accountHint(): String =
        _googleAccount.value?.let { "con la cuenta $it" } ?: "con la misma cuenta que autorizaste"

    private fun waitingText() = "Elegí las fotos en Google Fotos ${accountHint()}, tocá «Listo» y volvé a Fototin."

    private fun notReadyText() =
        "Google Fotos todavía no avisa que terminaste. Si ya tocaste «Listo», seguramente Google Fotos " +
            "está abierto con otra cuenta: elegí las fotos desde el navegador ${accountHint()}."

    fun cancelGooglePhotos() {
        settings.clearPendingPicker()
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

    /** Busca una versión nueva en GitHub. Si [manual], avisa también cuando no hay nada nuevo. */
    fun checkForUpdates(manual: Boolean) {
        if (_checkingUpdates.value) return
        _checkingUpdates.value = true
        viewModelScope.launch {
            runCatching { updater.checkForUpdate() }
                .onSuccess { release ->
                    settings.lastUpdateCheck = System.currentTimeMillis()
                    if (release != null) {
                        _update.value = UpdateState.Available(release)
                    } else if (manual) {
                        _messages.tryEmit("Tenés la última versión ($installedVersion)")
                    }
                }
                .onFailure { if (manual) _messages.tryEmit("No se pudo buscar actualizaciones. Revisá la conexión.") }
            _checkingUpdates.value = false
        }
    }

    /** Descarga la versión nueva y abre el instalador de Android. */
    fun downloadAndInstall() {
        val release = (_update.value as? UpdateState.Available)?.release ?: return
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(release, 0f)
            runCatching {
                val apk = updater.download(release) { progress -> _update.value = UpdateState.Downloading(release, progress) }
                settings.saveReleaseNotes(release.versionCode, release.versionName, release.notes)
                updater.install(apk)
            }.onFailure {
                _messages.tryEmit("No se pudo descargar la actualización. Probá de nuevo.")
            }
            // Si el usuario cancela la instalación, el aviso vuelve para intentarlo otra vez.
            _update.value = UpdateState.Available(release)
        }
    }

    fun canInstallUpdates(): Boolean = updater.canInstall()

    fun installPermissionIntent() = updater.installPermissionIntent()

    fun dismissUpdate() {
        _update.value = null
    }

    fun dismissWhatsNew() {
        _whatsNew.value = null
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        settings.autoCheckUpdates = enabled
        _autoCheckUpdates.value = enabled
    }

    /** De dónde saca las fotos el protector de pantalla. */
    private val _screensaverSource = MutableStateFlow(settings.screensaverSource)
    val screensaverSource: StateFlow<WidgetSource> = _screensaverSource.asStateFlow()

    fun setScreensaverSource(source: WidgetSource) {
        settings.screensaverSource = source
        _screensaverSource.value = source
    }

    private val _memoriesNotification = MutableStateFlow(settings.memoriesNotification)
    val memoriesNotification: StateFlow<Boolean> = _memoriesNotification.asStateFlow()

    /** Activa o apaga el aviso de recuerdos (el permiso de notificaciones lo pide la actividad). */
    fun setMemoriesNotification(enabled: Boolean) {
        settings.memoriesNotification = enabled
        _memoriesNotification.value = enabled
        MemoriesNotifier.schedule(getApplication())
    }

    /** Imágenes listas para compartir (la actividad abre el menú de Compartir). */
    private val _shareImage = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareImage: SharedFlow<Uri> = _shareImage.asSharedFlow()

    fun shareWidget(widget: PlacedWidget) {
        viewModelScope.launch {
            val uri = runCatching { WidgetSupport.shareImage(getApplication(), widget) }.getOrNull()
            if (uri != null) _shareImage.tryEmit(uri) else _messages.tryEmit("No se pudo armar la imagen del widget")
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
        _favoritesOnly.value = false
        clearSelection()
    }

    fun openFavorites() {
        _currentAlbumId.value = null
        _favoritesOnly.value = true
        clearSelection()
    }

    fun toggleSelection(id: String) = _selection.update { if (id in it) it - id else it + id }

    /** Al arrastrar el dedo eligiendo fotos: las que ya estaban más las del tramo recorrido. */
    fun selectRange(base: Set<String>, ids: Collection<String>) {
        _selection.value = base + ids
    }

    fun openSearch() {
        _searching.value = true
    }

    fun setQuery(text: String) {
        _query.value = text.take(60)
    }

    fun closeSearch() {
        _searching.value = false
        _query.value = ""
    }

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
            _favoritesOnly.value = false
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
            _favoritesOnly.value = false
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
