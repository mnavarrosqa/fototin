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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

data class Photo(
    val id: String,
    val file: File,
    val thumb: File,
    val width: Int,
    val height: Int,
    /** Fecha en que se sacó la foto (o se importó, si no se conoce), en milisegundos. */
    val takenAt: Long,
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
    private val indexFile = File(appContext.filesDir, "photos.json")

    /** Fotos en el orden en que se importaron. */
    fun list(): List<Photo> = synchronized(lock) {
        val entries = readIndex()
        val known = entries.mapTo(HashSet()) { it.id }
        // Fotos importadas por versiones anteriores, que no tenían índice.
        val orphans = photosDir.listFiles { f -> f.extension == "jpg" && f.nameWithoutExtension !in known }
            ?.sortedBy { it.name }
            .orEmpty()
            .map { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                Entry(file.nameWithoutExtension, bounds.outWidth, bounds.outHeight, file.lastModified())
            }
        val all = (entries + orphans).filter { photoFile(it.id).exists() }
        if (orphans.isNotEmpty() || all.size != entries.size) writeIndex(all)
        all.map { it.toPhoto() }
    }

    /** Importa las fotos de a varias a la vez. Devuelve los ids de las que se pudieron importar. */
    suspend fun import(uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<String> =
        withContext(Dispatchers.IO) {
            val done = AtomicInteger()
            val permits = Semaphore(IMPORT_PARALLELISM)
            val imported = coroutineScope {
                uris.map { uri ->
                    async {
                        permits.withPermit {
                            runCatching { importOne(uri) }.getOrNull()
                                .also { onProgress(done.incrementAndGet(), uris.size) }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            synchronized(lock) { writeIndex(readIndex() + imported) }
            imported.map { it.id }
        }

    suspend fun remove(ids: Collection<String>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeIndex(readIndex().filter { it.id !in ids })
            ids.forEach {
                photoFile(it).delete()
                thumbFile(it).delete()
            }
        }
    }

    /** Crea las miniaturas que falten (por ejemplo, de fotos de versiones anteriores). */
    suspend fun ensureThumbnails() = withContext(Dispatchers.IO) {
        list().filter { !it.thumb.exists() }.forEach { photo ->
            decodeForSize(photo.file, THUMB_PX)?.let {
                saveThumb(it, photo.thumb)
                it.recycle()
            }
        }
    }

    private fun importOne(uri: Uri): Entry {
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
        val id = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        photoFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        saveThumb(bitmap, thumbFile(id))
        val entry = Entry(id, bitmap.width, bitmap.height, takenAt(uri) ?: System.currentTimeMillis())
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

    private fun Entry.toPhoto() = Photo(id, photoFile(id), thumbFile(id), width, height, takenAt)

    private fun readIndex(): List<Entry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(o.getString("id"), o.optInt("w"), o.optInt("h"), o.optLong("t"))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().put("id", it.id).put("w", it.width).put("h", it.height).put("t", it.takenAt))
        }
        // Escribir y renombrar para no dejar un índice a medias si la app se cierra.
        val tmp = File(indexFile.path + ".tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(indexFile)
    }

    private data class Entry(val id: String, val width: Int, val height: Int, val takenAt: Long)

    companion object {
        private const val MAX_SIDE_PX = 1600
        private const val THUMB_PX = 360
        private const val IMPORT_PARALLELISM = 3

        /** Protege `photos.json`: lo leen y escriben la app, el widget y el worker. */
        private val lock = Any()

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
