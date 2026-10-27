package app.fototin.data

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Búsqueda de fotos por lo que el usuario recuerda: una palabra de la nota, el mes o el año.
 * No distingue mayúsculas ni tildes: «cumpleanos» encuentra «Cumpleaños».
 */
object PhotoSearch {

    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** Todas las palabras de [query] tienen que aparecer en la nota o en la fecha de la foto. */
    fun matches(photo: Photo, query: String, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = normalize(photo.note) + " " + dateText(photo.takenAt, zone)
        return words.all { it in text }
    }

    fun filter(photos: List<Photo>, query: String): List<Photo> =
        if (query.isBlank()) photos else photos.filter { matches(it, query) }

    /** «12 marzo 2024», más «setiembre» para quien lo escribe así. */
    private fun dateText(millis: Long, zone: TimeZone): String {
        val calendar = Calendar.getInstance(zone, Locale.ROOT).apply { timeInMillis = millis }
        val month = calendar.get(Calendar.MONTH)
        val extra = if (month == Calendar.SEPTEMBER) " setiembre" else ""
        return "${calendar.get(Calendar.DAY_OF_MONTH)} ${MONTHS[month]} ${calendar.get(Calendar.YEAR)}$extra"
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
}
