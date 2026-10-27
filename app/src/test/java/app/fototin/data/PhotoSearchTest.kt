package app.fototin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class PhotoSearchTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun photo(note: String, year: Int, month: Int, day: Int): Photo {
        val millis = Calendar.getInstance(utc).apply { clear(); set(year, month, day, 12, 0) }.timeInMillis
        return Photo(note, File("$note.jpg"), File("$note-t.jpg"), 100, 100, millis, note = note)
    }

    @Test
    fun `encuentra por la nota sin importar tildes ni mayusculas`() {
        val photo = photo("Cumpleaños de Sofía", 2024, Calendar.MARCH, 12)
        assertTrue(PhotoSearch.matches(photo, "cumpleanos", utc))
        assertTrue(PhotoSearch.matches(photo, "SOFIA", utc))
        assertFalse(PhotoSearch.matches(photo, "playa", utc))
    }

    @Test
    fun `encuentra por mes y año`() {
        val photo = photo("", 2023, Calendar.SEPTEMBER, 5)
        assertTrue(PhotoSearch.matches(photo, "septiembre", utc))
        assertTrue(PhotoSearch.matches(photo, "setiembre 2023", utc))
        assertTrue(PhotoSearch.matches(photo, "2023", utc))
        assertFalse(PhotoSearch.matches(photo, "2024", utc))
        assertFalse(PhotoSearch.matches(photo, "marzo", utc))
    }

    @Test
    fun `todas las palabras tienen que coincidir`() {
        val photo = photo("Playa con Toto", 2022, Calendar.JANUARY, 20)
        assertTrue(PhotoSearch.matches(photo, "toto enero", utc))
        assertFalse(PhotoSearch.matches(photo, "toto febrero", utc))
    }

    @Test
    fun `una busqueda vacia muestra todas`() {
        val photos = listOf(photo("a", 2020, Calendar.MAY, 1), photo("b", 2021, Calendar.MAY, 1))
        assertEquals(photos, PhotoSearch.filter(photos, "   "))
    }
}
