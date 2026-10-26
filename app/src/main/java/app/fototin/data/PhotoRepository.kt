package app.fototin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/** Fotos recién sacadas de la colección, con su posición, por si se deshace. */
class RemovedPhotos internal constructor(internal val entries: List<Pair<Int, PhotoRepository.Entry>>) {
    val size: Int get() = entries.size
}

/** Resultado de una importación. */
data class ImportResult(val ids: List<String>, val duplicates: Int, val failed: Int)

data class Photo(
    val id: String,
    val file: File,
    val thumb: File,
    val width: Int,
    val height: Int,
    /** Fecha en que se sacó la foto (o se importó, si no se conoce), en milisegundos. */
    val takenAt: Long,
    /** Punto a mantener visible al recortar (0..1), por ejemplo el centro de las caras. */
    val focusX: Float = FocusFinder.DEFAULT_X,
    val focusY: Float = FocusFinder.DEFAULT_Y,
    /** Nota corta que escribe el usuario; los widgets la muestran en lugar de la fecha. */
    val note: String = "",
    val favorite: Boolean = false,
) {
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

/**
 * Guarda una copia reducida de cada foto elegida, más una miniatura para la grilla, dentro del
 * almacenamiento privado de la app.
 *
 * Copiar (en vez de guardar la Uri) evita que el widget se quede sin foto cuando el permiso
 * temporal del selector o de "Compartir" expira, y funciona igual para fotos locales y de
 * Google Fotos. Los datos de cada foto se guardan en `photos.json`.
 */
class PhotoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val photosDir = File(appContext.filesDir, "photos").apply { mkdirs() }
    private val thumbsDir = File(appContext.filesDir, "thumbs").apply { mkdirs() }
    private val trashDir = File(appContext.filesDir, "trash").apply { mkdirs() }
    private val indexFile = File(appContext.filesDir, "photos.json")

    /**
     * Fotos en el orden en que se importaron.
     *
     * Se llama muy seguido (cada vez que se dibuja un widget), así que se sirve desde memoria: el
     * índice se lee del disco una sola vez por proceso y se actualiza en cada escritura.
     */
    fun list(): List<Photo> = synchronized(lock) {
        if (!reconciled) {
            reconcile()
            reconciled = true
        }
        cachedPhotos ?: readIndex().map { it.toPhoto() }.also { cachedPhotos = it }
    }

    /**
     * Acomoda el índice con los archivos reales: suma fotos de versiones anteriores que no tenían
     * índice y quita las que ya no existen.
     */
    private fun reconcile() {
        // Lo que quedó en la papelera de una sesión anterior ya no se puede deshacer.
        trashDir.listFiles()?.forEach { it.delete() }
        val entries = readIndex()
        val known = entries.mapTo(HashSet()) { it.id }
        val orphans = photosDir.listFiles { f -> f.extension == "jpg" && f.nameWithoutExtension !in known }
            ?.sortedBy { it.name }
            .orEmpty()
            .map { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                Entry(file.nameWithoutExtension, bounds.outWidth, bounds.outHeight, file.lastModified(), -1f, -1f)
            }
        val all = (entries + orphans).filter { photoFile(it.id).exists() }
        if (orphans.isNotEmpty() || all.size != entries.size) writeIndex(all)
    }

    /** El índice tal como está en el disco, para copias de seguridad. */
    fun exportIndexJson(): String = synchronized(lock) { toJson(readIndex()) }

    /**
     * Fotos de una copia de seguridad que conviene restaurar: las que no están ya (ni con el mismo
     * id ni con el mismo archivo original).
     */
    fun restorableIds(json: String): Set<String> = synchronized(lock) {
        val current = readIndex()
        val ids = current.mapTo(HashSet()) { it.id }
        val hashes = current.mapNotNullTo(HashSet()) { it.hash }
        val images = current.mapNotNullTo(mutableListOf()) { it.fingerprint() }
        parseEntries(json).filter { entry ->
            val image = entry.fingerprint()
            val sameImage = image != null && images.any { ImageHash.sameImage(it, image) }
            if (!sameImage && image != null) images += image
            entry.id !in ids && (entry.hash == null || entry.hash !in hashes) && !sameImage
        }.mapTo(HashSet()) { it.id }
    }

    /** Suma al índice las fotos restauradas, cuyos archivos ya se copiaron. Devuelve cuántas. */
    fun addRestored(json: String, ids: Set<String>): Int = synchronized(lock) {
        val incoming = parseEntries(json).filter { it.id in ids && photoFile(it.id).exists() }
        if (incoming.isNotEmpty()) writeIndex(readIndex() + incoming)
        incoming.size
    }

    /** Archivo donde se guarda (o se restaura) una foto o su miniatura. */
    fun fileFor(id: String, thumbnail: Boolean): File = if (thumbnail) thumbFile(id) else photoFile(id)

    /**
     * Importa las fotos de a varias a la vez. Las que ya estaban (mismo archivo original) se
     * saltean.
     */
    suspend fun import(uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult =
        withContext(Dispatchers.IO) {
            val done = AtomicInteger()
            val duplicates = AtomicInteger()
            val permits = Semaphore(IMPORT_PARALLELISM)
            val existing = synchronized(lock) { readIndex() }
            // Dos controles: el archivo idéntico (rápido) y la huella visual, que reconoce la misma
            // foto aunque llegue recomprimida o sin datos EXIF por otro camino.
            val knownFiles: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
                existing.mapNotNullTo(this) { it.hash }
            }
            val knownImages = existing.mapNotNullTo(mutableListOf()) { it.fingerprint() }
            val isNewImage = { fingerprint: Fingerprint ->
                synchronized(knownImages) {
                    if (knownImages.any { ImageHash.sameImage(it, fingerprint) }) {
                        false
                    } else {
                        knownImages += fingerprint
                        true
                    }
                }
            }
            val results = coroutineScope {
                uris.map { uri ->
                    async {
                        permits.withPermit {
                            runCatching {
                                val hash = fingerprint(uri)
                                val entry = if (hash != null && !knownFiles.add(hash)) null else importOne(uri, hash, isNewImage)
                                if (entry == null) duplicates.incrementAndGet()
                                entry
                            }.also { onProgress(done.incrementAndGet(), uris.size) }
                        }
                    }
                }.awaitAll()
            }
            val imported = results.mapNotNull { it.getOrNull() }
            synchronized(lock) { writeIndex(readIndex() + imported) }
            ImportResult(imported.map { it.id }, duplicates.get(), results.count { it.isFailure })
        }

    /**
     * Fotos repetidas que ya están en la colección: para cada una, el id de la que se queda (la
     * primera que se agregó).
     */
    suspend fun duplicatesInLibrary(): Map<String, String> = withContext(Dispatchers.IO) {
        completeMissingData()
        synchronized(lock) { findDuplicates(readIndex().map { it.id to it.fingerprint() }) }
    }

    /**
     * Antes de quitar repetidas, pasa a la foto que se queda lo que la repetida tenía de más:
     * la marca de favorita y la nota, si la otra no tiene.
     */
    suspend fun mergeInto(duplicates: Map<String, String>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val byId = readIndex().associateBy { it.id }
            writeIndex(
                readIndex().map { kept ->
                    val copies = duplicates.filterValues { it == kept.id }.keys.mapNotNull { byId[it] }
                    if (copies.isEmpty()) return@map kept
                    kept.copy(
                        favorite = kept.favorite || copies.any { it.favorite },
                        note = kept.note.ifBlank { copies.firstOrNull { it.note.isNotBlank() }?.note.orEmpty() },
                    )
                },
            )
        }
    }

    /** Cambia la nota, la marca de favorita o el punto de foco de una foto. */
    suspend fun update(
        id: String,
        note: String? = null,
        favorite: Boolean? = null,
        focus: Pair<Float, Float>? = null,
    ) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeIndex(
                readIndex().map { e ->
                    if (e.id != id) return@map e
                    e.copy(
                        note = note?.trim() ?: e.note,
                        favorite = favorite ?: e.favorite,
                        focusX = focus?.first ?: e.focusX,
                        focusY = focus?.second ?: e.focusY,
                    )
                },
            )
        }
    }

    /**
     * Saca las fotos de la colección y mueve sus archivos a una papelera, para poder deshacerlo
     * con [restore]. Los archivos se borran de verdad con [purge] (o al abrir la app la próxima vez).
     */
    suspend fun remove(ids: Collection<String>): RemovedPhotos = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readIndex()
            val removed = current.withIndex().filter { it.value.id in ids }.map { it.index to it.value }
            writeIndex(current.filter { it.id !in ids })
            removed.forEach { (_, entry) ->
                photoFile(entry.id).renameTo(trashFile(entry.id, thumbnail = false))
                thumbFile(entry.id).renameTo(trashFile(entry.id, thumbnail = true))
            }
            RemovedPhotos(removed)
        }
    }

    /** Devuelve a la colección fotos sacadas con [remove], en el lugar donde estaban. */
    suspend fun restore(removed: RemovedPhotos) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val entries = readIndex().toMutableList()
            removed.entries.sortedBy { it.first }.forEach { (position, entry) ->
                if (!trashFile(entry.id, thumbnail = false).renameTo(photoFile(entry.id))) return@forEach
                trashFile(entry.id, thumbnail = true).renameTo(thumbFile(entry.id))
                entries.add(position.coerceAtMost(entries.size), entry)
            }
            writeIndex(entries)
        }
    }

    /** Borra definitivamente los archivos de fotos sacadas con [remove]. */
    suspend fun purge(removed: RemovedPhotos) = withContext(Dispatchers.IO) {
        removed.entries.forEach { (_, entry) ->
            trashFile(entry.id, thumbnail = false).delete()
            trashFile(entry.id, thumbnail = true).delete()
        }
    }

    /**
     * Completa lo que les falte a las fotos de versiones anteriores: miniatura y punto de foco.
     * Devuelve true si cambió algo.
     */
    suspend fun completeMissingData(): Boolean = withContext(Dispatchers.IO) {
        list().filter { !it.thumb.exists() }.forEach { photo ->
            decodeForSize(photo.file, THUMB_PX)?.let {
                saveThumb(it, photo.thumb)
                it.recycle()
            }
        }
        val missingFocus = synchronized(lock) { readIndex() }.filter { it.focusX < 0f }
        val found = missingFocus.associate { entry ->
            val focus = decodeForSize(photoFile(entry.id), FocusFinder.ANALYSIS_PX)?.let { bitmap ->
                FocusFinder.find(bitmap).also { bitmap.recycle() }
            } ?: (FocusFinder.DEFAULT_X to FocusFinder.DEFAULT_Y)
            entry.id to focus
        }
        if (found.isNotEmpty()) {
            synchronized(lock) {
                writeIndex(readIndex().map { e -> found[e.id]?.let { (x, y) -> e.copy(focusX = x, focusY = y) } ?: e })
            }
        }
        // Huella visual de las fotos importadas antes de que existiera.
        val hashes = synchronized(lock) { readIndex() }.filter { it.visualHash == null }.mapNotNull { entry ->
            decodeForSize(photoFile(entry.id), HASH_SOURCE_PX)?.let { bitmap ->
                entry.id to visualHash(bitmap).also { bitmap.recycle() }
            }
        }.toMap()
        if (hashes.isNotEmpty()) {
            synchronized(lock) { writeIndex(readIndex().map { e -> hashes[e.id]?.let { e.copy(visualHash = it) } ?: e }) }
        }
        found.isNotEmpty() || hashes.isNotEmpty()
    }

    /** Huella SHA-256 del archivo original, para reconocer fotos repetidas. */
    private fun fingerprint(uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Devuelve null si [isNewImage] dice que la foto ya está (y entonces no guarda nada). */
    private fun importOne(uri: Uri, hash: String?, isNewImage: (Fingerprint) -> Boolean): Entry? {
        val resolver = appContext.contentResolver
        // ImageDecoder ya aplica la rotación EXIF.
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE_PX) {
                val scale = MAX_SIDE_PX.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val visual = visualHash(bitmap)
        if (!isNewImage(Fingerprint(visual, bitmap.width.toFloat() / bitmap.height))) {
            bitmap.recycle()
            return null
        }
        val id = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        photoFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        saveThumb(bitmap, thumbFile(id))
        val (fx, fy) = FocusFinder.find(bitmap)
        val entry = Entry(
            id, bitmap.width, bitmap.height, takenAt(uri) ?: System.currentTimeMillis(), fx, fy,
            hash = hash, visualHash = visual,
        )
        bitmap.recycle()
        return entry
    }

    private fun takenAt(uri: Uri): Long? {
        val resolver = appContext.contentResolver
        val fromExif = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                raw?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }
            }
        }.getOrNull()
        if (fromExif != null) return fromExif
        // El selector de fotos y la galería suelen exponer la fecha aunque el EXIF venga limpio.
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
    }

    private fun saveThumb(source: Bitmap, out: File) {
        val longest = max(source.width, source.height)
        val scale = minOf(1f, THUMB_PX.toFloat() / longest)
        val thumb = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        out.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (thumb !== source) thumb.recycle()
    }

    private fun photoFile(id: String) = File(photosDir, "$id.jpg")
    private fun thumbFile(id: String) = File(thumbsDir, "$id.jpg")
    private fun trashFile(id: String, thumbnail: Boolean) = File(trashDir, if (thumbnail) "${id}_thumb.jpg" else "$id.jpg")

    private fun Entry.toPhoto() = Photo(
        id, photoFile(id), thumbFile(id), width, height, takenAt,
        if (focusX < 0f) FocusFinder.DEFAULT_X else focusX,
        if (focusY < 0f) FocusFinder.DEFAULT_Y else focusY,
        note,
        favorite,
    )

    private fun readIndex(): List<Entry> {
        cachedEntries?.let { return it }
        if (!indexFile.exists()) return emptyList()
        return parseEntries(runCatching { indexFile.readText() }.getOrDefault("[]")).also { cachedEntries = it }
    }

    private fun parseEntries(json: String): List<Entry> =
        runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(
                    o.getString("id"), o.optInt("w"), o.optInt("h"), o.optLong("t"),
                    o.optDouble("fx", -1.0).toFloat(), o.optDouble("fy", -1.0).toFloat(),
                    note = o.optString("n"),
                    favorite = o.optBoolean("fav"),
                    hash = o.optString("hash").ifEmpty { null },
                    visualHash = if (o.has("ph")) o.optLong("ph") else null,
                )
            }
        }.getOrDefault(emptyList())

    private fun writeIndex(entries: List<Entry>) {
        // Escribir y renombrar para no dejar un índice a medias si la app se cierra.
        val tmp = File(indexFile.path + ".tmp")
        tmp.writeText(toJson(entries))
        tmp.renameTo(indexFile)
        cachedEntries = entries
        cachedPhotos = null
    }

    private fun toJson(entries: List<Entry>): String {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject().put("id", it.id).put("w", it.width).put("h", it.height).put("t", it.takenAt)
                    .put("fx", it.focusX.toDouble()).put("fy", it.focusY.toDouble())
                    .put("n", it.note).put("fav", it.favorite).put("hash", it.hash ?: "")
                    .apply { it.visualHash?.let { ph -> put("ph", ph) } },
            )
        }
        return array.toString()
    }

    /** Un foco negativo significa "todavía no calculado". */
    internal data class Entry(
        val id: String,
        val width: Int,
        val height: Int,
        val takenAt: Long,
        val focusX: Float,
        val focusY: Float,
        val note: String = "",
        val favorite: Boolean = false,
        val hash: String? = null,
        /** Huella visual ([ImageHash]); null en fotos que todavía no se analizaron. */
        val visualHash: Long? = null,
    ) {
        fun fingerprint(): Fingerprint? =
            visualHash?.takeIf { width > 0 && height > 0 }?.let { Fingerprint(it, width.toFloat() / height) }
    }

    companion object {
        private const val MAX_SIDE_PX = 1600
        private const val THUMB_PX = 360
        private const val IMPORT_PARALLELISM = 3
        /** Resolución desde la que se calcula la huella visual de fotos ya guardadas. */
        private const val HASH_SOURCE_PX = 128

        /** Huella visual de un bitmap: lo achica en dos pasos (para suavizarlo) y lo pasa a grises. */
        fun visualHash(bitmap: Bitmap): Long {
            val small = Bitmap.createScaledBitmap(bitmap, ImageHash.GRID_WIDTH * 8, ImageHash.GRID_HEIGHT * 8, true)
            val grid = Bitmap.createScaledBitmap(small, ImageHash.GRID_WIDTH, ImageHash.GRID_HEIGHT, true)
            val pixels = IntArray(ImageHash.GRID_WIDTH * ImageHash.GRID_HEIGHT)
            grid.getPixels(pixels, 0, ImageHash.GRID_WIDTH, 0, 0, ImageHash.GRID_WIDTH, ImageHash.GRID_HEIGHT)
            if (small !== bitmap) small.recycle()
            grid.recycle()
            val gray = IntArray(pixels.size) { i ->
                val c = pixels[i]
                (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            }
            return ImageHash.dHash(gray)
        }

        /** Protege `photos.json` y su copia en memoria: los usan la app, los widgets y el worker. */
        private val lock = Any()
        private var cachedEntries: List<Entry>? = null
        private var cachedPhotos: List<Photo>? = null
        private var reconciled = false

        /** Decodifica [file] con una resolución cercana a [targetPx] en su lado más largo. */
        fun decodeForSize(file: File, targetPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null
            var sample = 1
            while (longest / (sample * 2) >= targetPx) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
