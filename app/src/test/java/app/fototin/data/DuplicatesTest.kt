package app.fototin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuplicatesTest {

    /** Una "foto" de 9×8 en grises con textura. */
    private fun image(seed: Int): IntArray {
        val random = Random(seed)
        return IntArray(ImageHash.GRID_WIDTH * ImageHash.GRID_HEIGHT) { random.nextInt(256) }
    }

    @Test
    fun `la misma imagen con un poco de ruido es la misma foto`() {
        val original = image(1)
        // Recomprimir cambia un poco los valores, pero no el orden de brillo entre zonas vecinas.
        val recompressed = original.map { it + 1 }.toIntArray()
        val a = Fingerprint(ImageHash.dHash(original), 1.5f)
        val b = Fingerprint(ImageHash.dHash(recompressed), 1.5f)
        assertTrue(ImageHash.isComparable(a.hash))
        assertTrue(ImageHash.sameImage(a, b))
    }

    @Test
    fun `imagenes distintas no son la misma foto`() {
        val a = Fingerprint(ImageHash.dHash(image(1)), 1.5f)
        val b = Fingerprint(ImageHash.dHash(image(2)), 1.5f)
        assertFalse(ImageHash.sameImage(a, b))
    }

    @Test
    fun `distinta proporcion no es la misma foto`() {
        val hash = ImageHash.dHash(image(1))
        assertFalse(ImageHash.sameImage(Fingerprint(hash, 1.5f), Fingerprint(hash, 0.75f)))
    }

    @Test
    fun `imagenes lisas no se comparan`() {
        val flat = ImageHash.dHash(IntArray(ImageHash.GRID_WIDTH * ImageHash.GRID_HEIGHT) { 10 })
        assertFalse(ImageHash.sameImage(Fingerprint(flat, 1f), Fingerprint(flat, 1f)))
    }

    @Test
    fun `se queda con la primera de cada grupo`() {
        val a = Fingerprint(ImageHash.dHash(image(1)), 1.5f)
        val b = Fingerprint(ImageHash.dHash(image(2)), 1.5f)
        val result = findDuplicates(
            listOf("vieja" to a, "otra" to b, "copia" to a, "sin-huella" to null, "copia-2" to a),
        )
        assertEquals(mapOf("copia" to "vieja", "copia-2" to "vieja"), result)
    }
}
