package app.fototin.widget

/**
 * Estilos del widget de una foto.
 *
 * @param alwaysText el estilo siempre lleva texto (la nota o la fecha), aunque no esté activado
 * "Mostrar nota o fecha".
 */
enum class WidgetStyle(val label: String, val alwaysText: Boolean = false) {
    ROUNDED("Redondeado"),
    FIT("Ajustada"),
    CALENDAR("Calendario"),
    POSTCARD("Postal", alwaysText = true),
    POLAROID("Polaroid"),
    WASHI("Washi"),
    ARCH("Arco"),
    CIRCLE("Círculo"),
    HEART("Corazón"),
    PETAL("Pétalo"),
    STAMP("Estampilla"),
    FRAME("Marco"),
    FULL("Borde a borde");

    companion object {
        fun from(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: ROUNDED
    }
}

/** Qué pasa al tocar el widget de una foto. */
enum class TapAction(val label: String) {
    NEXT("Siguiente foto"),
    VIEW("Verla en grande"),
    OPEN_APP("Abrir Fototin");

    companion object {
        fun from(name: String?): TapAction = entries.firstOrNull { it.name == name } ?: NEXT
    }
}

/** Diseños del widget Collage, con cuántas fotos admite cada uno. */
enum class CollageStyle(val label: String, val counts: IntRange) {
    MOSAIC("Mosaico", 2..6),
    MAGAZINE("Revista", 3..5),
    POLAROIDS("Polaroids", 2..5),
    PHOTOBOOTH("Fotomatón", 3..4),
    FILM("Película", 3..5);

    fun clampCount(count: Int): Int = count.coerceIn(counts)

    companion object {
        fun from(name: String?): CollageStyle = entries.firstOrNull { it.name == name } ?: MOSAIC
    }
}
