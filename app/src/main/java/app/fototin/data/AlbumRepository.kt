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

    private fun edit(id: String, change: (Album) -> Album) = synchronized(lock) {
        write(read().map { if (it.id == id) change(it) else it })
    }

    private fun read(): List<Album> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val ids = o.getJSONArray("photos")
                Album(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
            }
        }.getOrDefault(emptyList())
    }

    private fun write(albums: List<Album>) {
        val array = JSONArray()
        albums.forEach { album ->
            array.put(
                JSONObject()
                    .put("id", album.id)
                    .put("name", album.name)
                    .put("photos", JSONArray(album.photoIds)),
            )
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(file)
    }

    private companion object {
        val lock = Any()
    }
}
