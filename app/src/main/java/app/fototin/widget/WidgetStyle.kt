package app.fototin.widget

enum class WidgetStyle(val label: String) {
    ROUNDED("Redondeado"),
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
