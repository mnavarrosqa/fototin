package app.fototin.widget

enum class WidgetStyle(val label: String) {
    ROUNDED("Redondeado"),
    POLAROID("Polaroid"),
    CIRCLE("Círculo"),
    FULL("Borde a borde");

    companion object {
        fun from(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: ROUNDED
    }
}
