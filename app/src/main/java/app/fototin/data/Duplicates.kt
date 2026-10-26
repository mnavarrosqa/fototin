package app.fototin.data

import kotlin.math.abs
import kotlin.math.max

/**
 * Huella visual de una foto (dHash de 64 bits): compara el brillo de zonas vecinas en una versión
 * diminuta de la imagen. No cambia si la foto se achica, se recomprime o pierde sus datos EXIF,
 * así que reconoce la misma foto aunque llegue por caminos distintos (galería, selector, Google
 * Fotos, "Compartir").
 */
object ImageHash {
    const val GRID_WIDTH = 9
    const val GRID_HEIGHT = 8

    /** Diferencia máxima de bits para considerar que dos fotos son la misma. */
    private const val MAX_DISTANCE = 3

    /** Diferencia máxima de proporción (ancho / alto), relativa. */
    private const val MAX_ASPECT_DIFF = 0.03f

    /** [gray] es la imagen en grises de [GRID_WIDTH]×[GRID_HEIGHT], fila por fila. */
    fun dHash(gray: IntArray): Long {
        require(gray.size == GRID_WIDTH * GRID_HEIGHT)
        var hash = 0L
        var bit = 0
        for (y in 0 until GRID_HEIGHT) {
            for (x in 0 until GRID_WIDTH - 1) {
                if (gray[y * GRID_WIDTH + x] > gray[y * GRID_WIDTH + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Una imagen casi lisa (todo negro, todo blanco) da una huella con casi todos los bits iguales:
     * dos fotos distintas de noche se confundirían. Esas no se comparan.
     */
    fun isComparable(hash: Long): Boolean = java.lang.Long.bitCount(hash) in 4..60

    fun sameImage(a: Fingerprint, b: Fingerprint): Boolean =
        isComparable(a.hash) && isComparable(b.hash) &&
            distance(a.hash, b.hash) <= MAX_DISTANCE &&
            abs(a.aspect - b.aspect) <= MAX_ASPECT_DIFF * max(a.aspect, b.aspect)
}

data class Fingerprint(val hash: Long, val aspect: Float)

/**
 * Busca fotos repetidas en una colección. Se queda con la primera de cada grupo (la más antigua) y
 * devuelve, para cada repetida, el id de la que se queda.
 */
fun findDuplicates(items: List<Pair<String, Fingerprint?>>): Map<String, String> {
    val kept = mutableListOf<Pair<String, Fingerprint>>()
    val duplicates = linkedMapOf<String, String>()
    items.forEach { (id, fingerprint) ->
        if (fingerprint == null) return@forEach
        val original = kept.firstOrNull { ImageHash.sameImage(it.second, fingerprint) }
        if (original != null) duplicates[id] = original.first else kept += id to fingerprint
    }
    return duplicates
}
