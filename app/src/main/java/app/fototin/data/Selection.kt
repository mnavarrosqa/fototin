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
    return (saved + pool.filter { it !in saved }).take(count.coerceIn(1, 6))
}

/**
 * Cambia una sola foto del collage: la del lugar [slot] (que va rotando), por una que no esté ya en
 * el collage. Devuelve las fotos nuevas, el próximo lugar a cambiar y el nuevo [seen].
 */
fun rotateOne(
    ids: List<String>,
    current: List<String>,
    slot: Int,
    seen: Set<String>,
    shuffle: Boolean,
    random: kotlin.random.Random = kotlin.random.Random,
): Triple<List<String>, Int, Set<String>> {
    if (current.isEmpty()) return Triple(current, 0, seen)
    val position = slot.mod(current.size)
    val leaving = current[position]
    // Las que se pueden poner: todas menos las que siguen en el collage.
    val candidates = ids.filter { it == leaving || it !in current }
    if (candidates.size <= 1) return Triple(current, (position + 1) % current.size, seen)
    val (next, newSeen) = pickNext(candidates, listOf(leaving), seen, 1, shuffle, random)
    val updated = current.toMutableList().apply { set(position, next.first()) }
    return Triple(updated, (position + 1) % current.size, newSeen)
}

/**
 * Si a un widget que cambia cada [minutes] le toca cambiar, sabiendo que cambió por última vez en
 * [lastChange]. Se dan 2 minutos de margen porque WorkManager no corre exactamente a horario.
 */
fun isDue(minutes: Int, lastChange: Long, now: Long): Boolean =
    minutes > 0 && now - lastChange >= minutes * 60_000L - 2 * 60_000L
