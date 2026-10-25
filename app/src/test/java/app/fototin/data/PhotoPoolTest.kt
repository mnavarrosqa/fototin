package app.fototin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Calendar

class PhotoPoolTest {

    /** 25 de octubre de 2026, al mediodía. */
    private val now = date(2026, Calendar.OCTOBER, 25)

    @Test
    fun `recuerdos son fotos de este dia en otros años`() {
        val photos = listOf(
            photo("hoy-2020", date(2020, Calendar.OCTOBER, 25)),
            photo("hoy-2026", date(2026, Calendar.OCTOBER, 25)),
            photo("otro-dia", date(2021, Calendar.MARCH, 3)),
        )
        assertEquals(listOf("hoy-2020"), PhotoPool.memories(photos, now).map { it.id })
        assertEquals(listOf("hoy-2020"), PhotoPool.exactMemories(photos, now).map { it.id })
    }

    @Test
    fun `sin fotos del dia usa la misma semana y despues el mes`() {
        val week = listOf(photo("semana", date(2022, Calendar.OCTOBER, 27)), photo("mes", date(2022, Calendar.OCTOBER, 2)))
        assertEquals(listOf("semana"), PhotoPool.memories(week, now).map { it.id })

        val month = listOf(photo("mes", date(2022, Calendar.OCTOBER, 2)), photo("otro", date(2022, Calendar.MAY, 2)))
        assertEquals(listOf("mes"), PhotoPool.memories(month, now).map { it.id })
    }

    @Test
    fun `la semana da la vuelta en fin de año`() {
        val newYear = date(2026, Calendar.JANUARY, 1)
        val photos = listOf(photo("fin-de-año", date(2023, Calendar.DECEMBER, 30)), photo("otra", date(2023, Calendar.JUNE, 1)))
        assertEquals(listOf("fin-de-año"), PhotoPool.memories(photos, newYear).map { it.id })
    }

    @Test
    fun `sin recuerdos usa todas las fotos`() {
        val photos = listOf(photo("de-este-año", date(2026, Calendar.MAY, 1)))
        assertEquals(photos, PhotoPool.memories(photos, now))
    }

    @Test
    fun `leyenda de recuerdos dice cuantos años pasaron`() {
        val photo = photo("p", date(2023, Calendar.OCTOBER, 25))
        assertEquals("Hace 3 años", PhotoPool.caption(photo, WidgetSource.Memories, showText = false, now = now))
        val lastYear = photo("p", date(2025, Calendar.OCTOBER, 25))
        assertEquals("Hace 1 año", PhotoPool.caption(lastYear, WidgetSource.Memories, showText = false, now = now))
    }

    @Test
    fun `la nota tiene prioridad sobre la fecha`() {
        val withNote = photo("p", now).copy(note = "En la playa")
        assertEquals("En la playa", PhotoPool.caption(withNote, WidgetSource.All, showText = true, now = now))
        assertEquals(PhotoPool.formatDate(now), PhotoPool.caption(photo("q", now), WidgetSource.All, showText = true, now = now))
        assertNull(PhotoPool.caption(withNote, WidgetSource.All, showText = false, now = now))
    }

    @Test
    fun `las claves de la fuente van y vuelven`() {
        listOf(WidgetSource.All, WidgetSource.Memories, WidgetSource.Favorites, WidgetSource.FromAlbum("abc")).forEach {
            assertEquals(it, WidgetSource.from(it.key))
        }
        assertEquals(WidgetSource.All, WidgetSource.from(null))
        assertEquals(WidgetSource.All, WidgetSource.from("desconocida"))
    }

    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis

    private fun photo(id: String, takenAt: Long) = Photo(id, File("$id.jpg"), File("t$id.jpg"), 100, 100, takenAt)
}
