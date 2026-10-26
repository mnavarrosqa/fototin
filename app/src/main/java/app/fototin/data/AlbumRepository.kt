package app.fototin.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Album(val id: String, val name: String, val photoIds: List<String>)

/**
 * Álbumes: grupos de fotos con nombre que cada widget puede elegir mostrar.
 *
 * Un álbum guarda solo ids; las fotos borradas se ignoran al leerlo (ver [PhotoPool]).
 */
class AlbumRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "albums.json")

    fun list(): List<Album> = synchronized(lock) { read() }

    fun get(id: String): Album? = list().firstOrNull { it.id == id }

    fun create(name: String, photoIds: Collection<String> = emptyList()): Album = synchronized(lock) {
        val album = Album(UUID.randomUUID().toString(), name.trim(), photoIds.distinct())
        write(read() + album)
        album
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim()) }

    fun delete(id: String) = synchronized(lock) { write(read().filter { it.id != id }) }

    fun add(id: String, photoIds: Collection<String>) =
        edit(id) { album -> album.copy(photoIds = (album.photoIds + photoIds).distinct()) }

    fun remove(id: String, photoIds: Collection<String>) =
        edit(id) { album -> album.copy(photoIds = album.photoIds - photoIds.toSet()) }

    /**
     * Al quitar fotos repetidas, los álbumes que tenían una copia pasan a tener la foto que se queda.
     * [replacements] va de la repetida a la que se queda.
     */
    fun replacePhotos(replacements: Map<String, String>) = synchronized(lock) {
        write(
            read().map { album ->
                val extra = album.photoIds.mapNotNull { replacements[it] }
                if (extra.isEmpty()) album else album.copy(photoIds = (album.photoIds + extra).distinct())
            },
        )
    }

    private fun edit(id: String, change: (Album) -> Album) = synchronized(lock) {
        write(read().map { if (it.id == id) change(it) else it })
    }

    /** Los álbumes tal como están guardados, para copias de seguridad. */
    fun exportJson(): String = synchronized(lock) { toJson(read()) }

    /**
     * Suma los álbumes de una copia de seguridad. Si un álbum ya existe, se le agregan las fotos
     * que le falten.
     */
    fun mergeBackup(json: String) = synchronized(lock) {
        val incoming = parse(json)
        val current = read()
        val merged = current.map { album ->
            incoming.firstOrNull { it.id == album.id }
                ?.let { album.copy(photoIds = (album.photoIds + it.photoIds).distinct()) }
                ?: album
        } + incoming.filter { new -> current.none { it.id == new.id } }
        write(merged)
    }

    // Se sirve desde memoria: el archivo se lee una sola vez por proceso.
    private fun read(): List<Album> {
        cached?.let { return it }
        if (!file.exists()) return emptyList()
        return parse(runCatching { file.readText() }.getOrDefault("[]")).also { cached = it }
    }

    private fun parse(json: String): List<Album> =
        runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val ids = o.getJSONArray("photos")
                Album(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
            }
        }.getOrDefault(emptyList())

    private fun write(albums: List<Album>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(toJson(albums))
        tmp.renameTo(file)
        cached = albums
    }

    private fun toJson(albums: List<Album>): String {
        val array = JSONArray()
        albums.forEach { album ->
            array.put(
                JSONObject()
                    .put("id", album.id)
                    .put("name", album.name)
                    .put("photos", JSONArray(album.photoIds)),
            )
        }
        return array.toString()
    }

    private companion object {
        val lock = Any()
        var cached: List<Album>? = null
    }
}
