package app.fototin.data

/**
 * Elige las próximas [count] fotos entre [ids].
 *
 * En orden aleatorio no repite ninguna foto hasta haber mostrado todas: [seen] guarda las que ya
 * salieron en esta ronda y se reinicia cuando se terminan. Devuelve las fotos elegidas y el nuevo
 * [seen].
 */
fun pickNext(
    ids: List<String>,
    current: List<String>,
    seen: Set<String>,
    count: Int,
    shuffle: Boolean,
    random: kotlin.random.Random = kotlin.random.Random,
): Pair<List<String>, Set<String>> {
    val n = minOf(count, ids.size)
    if (!shuffle || ids.size <= n) {
        val start = current.lastOrNull()?.let { ids.indexOf(it) + 1 } ?: 0
        return List(n) { ids[(start + it) % ids.size] } to emptySet()
    }
    var seenNow = seen.intersect(ids.toSet()) + current
    val picked = mutableListOf<String>()
    repeat(n) {
        var pool = ids.filter { it !in seenNow && it !in picked }
        if (pool.isEmpty()) {
            seenNow = current.toSet() + picked
            pool = ids.filter { it !in seenNow }
        }
        if (pool.isEmpty()) pool = ids.filter { it !in picked }
        val next = pool.random(random)
        picked += next
        seenNow = seenNow + next
    }
    return picked to seenNow
}

/** Fotos de un collage: las guardadas que siguen disponibles, completadas con otras si faltan. */
fun collageSelection(pool: List<Photo>, savedIds: String?, count: Int): List<Photo> {
    val byId = pool.associateBy { it.id }
    val saved = savedIds?.split(',')?.mapNotNull { byId[it] }.orEmpty().distinct()
    return (saved + pool.filter { it !in saved }).take(count.coerceIn(1, 4))
}
