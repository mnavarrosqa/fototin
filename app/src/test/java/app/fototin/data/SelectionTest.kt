package app.fototin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class SelectionTest {

    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun `en orden avanza y da la vuelta`() {
        assertEquals(listOf("c"), pickNext(ids, listOf("b"), emptySet(), 1, shuffle = false).first)
        assertEquals(listOf("a"), pickNext(ids, listOf("e"), emptySet(), 1, shuffle = false).first)
    }

    @Test
    fun `en orden arranca por la primera si la actual ya no existe`() {
        assertEquals(listOf("a", "b"), pickNext(ids, listOf("zzz"), emptySet(), 2, shuffle = false).first)
    }

    @Test
    fun `aleatorio no repite hasta mostrar todas`() {
        val random = Random(42)
        var current = listOf("a")
        var seen = emptySet<String>()
        val shown = mutableListOf<String>()
        repeat(ids.size - 1) {
            val (next, newSeen) = pickNext(ids, current, seen, 1, shuffle = true, random = random)
            shown += next
            current = next
            seen = newSeen
        }
        // Las cuatro siguientes a "a" son justo las otras cuatro, sin repetir.
        assertEquals(setOf("b", "c", "d", "e"), shown.toSet())
        assertEquals(4, shown.size)
    }

    @Test
    fun `aleatorio nunca repite la foto actual`() {
        val random = Random(7)
        repeat(50) {
            val (next, _) = pickNext(ids, listOf("c"), emptySet(), 1, shuffle = true, random = random)
            assertTrue(next.single() != "c")
        }
    }

    @Test
    fun `collage elige varias fotos distintas`() {
        val (next, _) = pickNext(ids, listOf("a", "b"), emptySet(), 3, shuffle = true, random = Random(1))
        assertEquals(3, next.toSet().size)
    }

    @Test
    fun `pide mas fotos de las que hay`() {
        val (next, _) = pickNext(listOf("a", "b"), emptyList(), emptySet(), 4, shuffle = true)
        assertEquals(listOf("a", "b"), next)
    }

    @Test
    fun `collage mantiene las guardadas y completa con otras`() {
        val pool = ids.map { photo(it) }
        val chosen = collageSelection(pool, "d,zzz,b", 3)
        assertEquals(listOf("d", "b", "a"), chosen.map { it.id })
    }

    @Test
    fun `cambiar de a una reemplaza un solo lugar y va rotando`() {
        val (first, slot, _) = rotateOne(ids, listOf("a", "b", "c"), 0, emptySet(), shuffle = false)
        // En orden: "a" pasa a la siguiente que no está en el collage.
        assertEquals(listOf("d", "b", "c"), first)
        assertEquals(1, slot)
        val (second, nextSlot, _) = rotateOne(ids, first, slot, emptySet(), shuffle = false)
        assertEquals(listOf("d", "e", "c"), second)
        assertEquals(2, nextSlot)
    }

    @Test
    fun `cambiar de a una nunca repite una foto que ya esta en el collage`() {
        val random = Random(3)
        var current = listOf("a", "b", "c")
        var slot = 0
        var seen = emptySet<String>()
        repeat(30) {
            val (next, nextSlot, nextSeen) = rotateOne(ids, current, slot, seen, shuffle = true, random = random)
            assertEquals(3, next.toSet().size)
            current = next
            slot = nextSlot
            seen = nextSeen
        }
    }

    @Test
    fun `sin fotos de sobra el collage queda igual`() {
        val (same, _, _) = rotateOne(listOf("a", "b"), listOf("a", "b"), 0, emptySet(), shuffle = true)
        assertEquals(listOf("a", "b"), same)
    }

    private fun photo(id: String) = Photo(id, File("$id.jpg"), File("t$id.jpg"), 100, 100, 0L)
}
