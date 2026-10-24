package app.fototin.data

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector
import kotlin.math.max

/**
 * Busca caras en una foto para que, al recortarla en el widget, queden a la vista.
 *
 * Usa el detector de caras que viene con Android: no necesita librerías ni conexión.
 */
object FocusFinder {

    const val DEFAULT_X = 0.5f
    /** Sin caras, un poco arriba del centro: suele ser donde está lo importante. */
    const val DEFAULT_Y = 0.45f

    /** Resolución con la que se analiza la foto. */
    const val ANALYSIS_PX = 640

    private const val MAX_FACES = 8
    private const val MIN_CONFIDENCE = 0.4f

    /** Devuelve el punto de foco normalizado (0..1). */
    fun find(source: Bitmap): Pair<Float, Float> = runCatching {
        val scale = minOf(1f, ANALYSIS_PX.toFloat() / max(source.width, source.height))
        // FaceDetector necesita RGB_565 y un ancho par.
        val width = ((source.width * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val rgb565 = scaled.copy(Bitmap.Config.RGB_565, false)
        if (scaled !== source) scaled.recycle()

        val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val count = FaceDetector(width, height, MAX_FACES).findFaces(rgb565, faces)
        rgb565.recycle()

        val found = faces.take(count).filterNotNull().filter { it.confidence() >= MIN_CONFIDENCE }
        if (found.isEmpty()) return DEFAULT_X to DEFAULT_Y

        // Promedio de las caras, pesado por su tamaño; el punto medio de los ojos se baja un poco
        // para apuntar al centro de la cara.
        var sumX = 0f
        var sumY = 0f
        var total = 0f
        val mid = PointF()
        found.forEach { face ->
            face.getMidPoint(mid)
            val weight = face.eyesDistance()
            sumX += mid.x * weight
            sumY += (mid.y + face.eyesDistance() * 0.4f) * weight
            total += weight
        }
        (sumX / total / width).coerceIn(0f, 1f) to (sumY / total / height).coerceIn(0f, 1f)
    }.getOrDefault(DEFAULT_X to DEFAULT_Y)
}
