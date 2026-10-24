package app.fototin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Guarda una copia reducida de cada foto elegida dentro del almacenamiento privado de la app.
 *
 * Copiar (en vez de guardar la Uri) evita que el widget se quede sin foto cuando el permiso
 * temporal del selector o de "Compartir" expira, y funciona igual para fotos locales y de
 * Google Fotos.
 */
class PhotoRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "photos").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name }.orEmpty()

    /** Devuelve cuántas fotos se pudieron importar. */
    suspend fun import(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.count { uri -> runCatching { importOne(uri) }.isSuccess }
    }

    suspend fun remove(file: File) = withContext(Dispatchers.IO) {
        if (file.parentFile == dir) file.delete()
    }

    private fun importOne(uri: Uri) {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        // ImageDecoder ya aplica la rotación EXIF.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
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
        val out = File(dir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }

    companion object {
        private const val MAX_SIDE_PX = 1600

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
