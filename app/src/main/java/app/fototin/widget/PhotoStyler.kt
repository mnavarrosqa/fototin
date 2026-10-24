package app.fototin.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * Dibuja la foto con el estilo elegido en un bitmap del tamaño exacto del widget.
 *
 * Se hace todo en el bitmap (y no con modificadores de Glance) para que bordes redondeados,
 * sombras y marcos se vean igual en todas las versiones de Android.
 */
object PhotoStyler {

    /** Límite del lado más largo para no superar la memoria permitida a los widgets. */
    private const val MAX_OUTPUT_PX = 900

    fun render(source: Bitmap, style: WidgetStyle, widthPx: Int, heightPx: Int, density: Float): Bitmap {
        val fit = min(1f, MAX_OUTPUT_PX.toFloat() / max(widthPx, heightPx))
        val w = (widthPx * fit).toInt().coerceAtLeast(1)
        val h = (heightPx * fit).toInt().coerceAtLeast(1)
        val dp = density * fit

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())

        when (style) {
            WidgetStyle.FULL -> drawCrop(canvas, source, bounds, 0f)

            WidgetStyle.ROUNDED -> drawCrop(canvas, source, bounds, 24 * dp)

            WidgetStyle.POLAROID -> {
                val card = RectF(bounds).apply { inset(6 * dp, 6 * dp) }
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    setShadowLayer(4 * dp, 0f, 1.5f * dp, 0x55000000)
                }
                canvas.drawRoundRect(card, 4 * dp, 4 * dp, cardPaint)
                val margin = min(card.width(), card.height()) * 0.06f
                val photo = RectF(
                    card.left + margin,
                    card.top + margin,
                    card.right - margin,
                    card.bottom - margin * 3.5f,
                )
                drawCrop(canvas, source, photo, 1.5f * dp)
            }

            WidgetStyle.CIRCLE -> {
                val radius = min(w, h) / 2f - 4 * dp
                val cx = w / 2f
                val cy = h / 2f
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    setShadowLayer(4 * dp, 0f, 1.5f * dp, 0x55000000)
                }
                canvas.drawCircle(cx, cy, radius, ring)
                val inner = radius - 4 * dp
                drawCrop(canvas, source, RectF(cx - inner, cy - inner, cx + inner, cy + inner), inner)
            }
        }
        return out
    }

    /** Dibuja [src] recortado al centro para cubrir [dst], con esquinas de radio [radius]. */
    private fun drawCrop(canvas: Canvas, src: Bitmap, dst: RectF, radius: Float) {
        val scale = max(dst.width() / src.width, dst.height() / src.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                dst.left + (dst.width() - src.width * scale) / 2f,
                dst.top + (dst.height() - src.height * scale) / 2f,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
        }
        canvas.drawRoundRect(dst, radius, radius, paint)
    }
}
