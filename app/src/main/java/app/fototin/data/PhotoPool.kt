package app.fototin.data

import android.content.Context
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

/** De dónde saca las fotos un widget. */
sealed interface WidgetSource {
    val key: String

    data object All : WidgetSource {
        override val key = "all"
    }

    /** Fotos de un día como hoy en años anteriores. */
    data object Memories : WidgetSource {
        override val key = "memories"
    }

    data class FromAlbum(val albumId: String) : WidgetSource {
        override val key = "album:$albumId"
    }

    companion object {
        fun from(key: String?): WidgetSource = when {
            key == Memories.key -> Memories
            key != null && key.startsWith("album:") -> FromAlbum(key.removePrefix("album:"))
            else -> All
        }
    }
}

/** Calcula qué fotos le tocan a cada widget y qué leyenda llevan. */
object PhotoPool {

    fun resolve(context: Context, source: WidgetSource, now: Long = System.currentTimeMillis()): List<Photo> {
        val photos = PhotoRepository(context).list()
        return when (source) {
            WidgetSource.All -> photos
            WidgetSource.Memories -> memories(photos, now)
            is WidgetSource.FromAlbum -> {
                val album = AlbumRepository(context).get(source.albumId) ?: return photos
                val byId = photos.associateBy { it.id }
                album.photoIds.mapNotNull { byId[it] }
            }
        }
    }

    /**
     * Fotos de este mismo día en años anteriores. Si no hay, se amplía a la misma semana, después
     * al mismo mes, y si tampoco hay, se usan todas las fotos.
     */
    fun memories(photos: List<Photo>, now: Long = System.currentTimeMillis()): List<Photo> {
        val today = calendar(now)
        val past = photos.filter { calendar(it.takenAt).get(Calendar.YEAR) < today.get(Calendar.YEAR) }
        return sequenceOf(
            past.filter { sameDay(calendar(it.takenAt), today) },
            past.filter { daysApart(calendar(it.takenAt), today) <= 3 },
            past.filter { calendar(it.takenAt).get(Calendar.MONTH) == today.get(Calendar.MONTH) },
            photos,
        ).first { it.isNotEmpty() || it === photos }
    }

    /** Fotos sacadas exactamente un día como hoy en años anteriores. */
    fun exactMemories(photos: List<Photo>, now: Long = System.currentTimeMillis()): List<Photo> {
        val today = calendar(now)
        return photos.filter {
            val taken = calendar(it.takenAt)
            taken.get(Calendar.YEAR) < today.get(Calendar.YEAR) && sameDay(taken, today)
        }
    }

    fun caption(photo: Photo, source: WidgetSource, showDate: Boolean, now: Long = System.currentTimeMillis()): String? {
        if (source == WidgetSource.Memories) {
            val years = calendar(now).get(Calendar.YEAR) - calendar(photo.takenAt).get(Calendar.YEAR)
            return when {
                years == 1 -> "Hace 1 año"
                years > 1 -> "Hace $years años"
                else -> formatDate(photo.takenAt)
            }
        }
        return if (showDate) formatDate(photo.takenAt) else null
    }

    fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    private fun calendar(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.MONTH) == b.get(Calendar.MONTH) && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)

    /** Distancia aproximada en días entre dos fechas sin tener en cuenta el año (dando la vuelta en fin de año). */
    private fun daysApart(a: Calendar, b: Calendar): Int {
        val diff = abs(a.get(Calendar.DAY_OF_YEAR) - b.get(Calendar.DAY_OF_YEAR))
        return minOf(diff, 365 - diff)
    }
}
