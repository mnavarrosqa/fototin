package app.fototin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de seguridad en un .zip con todas las fotos, sus notas y los álbumes, para no perderlas al
 * cambiar de teléfono o reinstalar.
 */
class BackupManager(context: Context) {

    private val appContext = context.applicationContext
    private val photos = PhotoRepository(appContext)
    private val albums = AlbumRepository(appContext)

    /** Guarda la copia en [uri]. Devuelve cuántas fotos incluye. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val list = photos.list()
        val out = appContext.contentResolver.openOutputStream(uri) ?: error("No se pudo crear el archivo")
        ZipOutputStream(out.buffered()).use { zip ->
            // Las fotos ya son JPEG: comprimirlas de nuevo solo gasta batería.
            zip.setLevel(Deflater.NO_COMPRESSION)
            // Los índices van primero para poder decidir qué restaurar mientras se lee.
            zip.putText(INDEX, photos.exportIndexJson())
            zip.putText(ALBUMS, albums.exportJson())
            list.forEach { photo ->
                zip.putFile("photos/${photo.id}.jpg", photo.file)
                if (photo.thumb.exists()) zip.putFile("thumbs/${photo.id}.jpg", photo.thumb)
            }
        }
        list.size
    }

    /** Restaura la copia de [uri] sumándola a lo que ya hay. Devuelve cuántas fotos nuevas agregó. */
    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val input = appContext.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo")
        var index: String? = null
        var albumsJson: String? = null
        var wanted: Set<String> = emptySet()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == INDEX -> zip.readText().also {
                        index = it
                        wanted = photos.restorableIds(it)
                    }
                    name == ALBUMS -> albumsJson = zip.readText()
                    else -> {
                        // Solo nombres conocidos: evita que un .zip armado escriba fuera de la carpeta.
                        val match = FILE_PATTERN.matchEntire(name)
                        if (match != null && match.groupValues[2] in wanted) {
                            val (folder, id) = match.destructured
                            val target = photos.fileFor(id, thumbnail = folder == "thumbs")
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val json = index ?: error("El archivo no es una copia de Fototin")
        val added = photos.addRestored(json, wanted)
        albumsJson?.let { albums.mergeBackup(it) }
        added
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: java.io.File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun ZipInputStream.readText(): String = readBytes().decodeToString()

    private companion object {
        const val INDEX = "photos.json"
        const val ALBUMS = "albums.json"
        val FILE_PATTERN = Regex("^(photos|thumbs)/([A-Za-z0-9_-]+)\\.jpg$")
    }
}
