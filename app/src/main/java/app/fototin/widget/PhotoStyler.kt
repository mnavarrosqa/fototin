package app.fototin.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Dibuja la foto con el estilo elegido en un bitmap del tamaño exacto del widget.
 *
 * Se hace todo en el bitmap (y no con modificadores de Glance) para que formas, sombras y marcos
 * se vean igual en todas las versiones de Android.
 */
object PhotoStyler {

    /** Límite del lado más largo para no superar la memoria permitida a los widgets. */
    private const val MAX_OUTPUT_PX = 900

    private const val SHADOW = 0x40000000

    /**
     * @param caption texto opcional (por ejemplo, la fecha) que se dibuja sobre la foto o,
     * en la Polaroid, en el borde inferior.
     */
    fun render(
        source: Bitmap,
        style: WidgetStyle,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        caption: String? = null,
    ): Bitmap {
        val fit = min(1f, MAX_OUTPUT_PX.toFloat() / max(widthPx, heightPx))
        val w = (widthPx * fit).toInt().coerceAtLeast(1)
        val h = (heightPx * fit).toInt().coerceAtLeast(1)
        val dp = density * fit

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())

        when (style) {
            WidgetStyle.FULL -> {
                drawPhoto(canvas, source, bounds, roundRect(bounds, 0f))
                caption?.let { drawPill(canvas, it, bounds, dp, centered = false) }
            }

            WidgetStyle.ROUNDED -> {
                drawPhoto(canvas, source, bounds, roundRect(bounds, 28 * dp))
                caption?.let { drawPill(canvas, it, bounds, dp, centered = false) }
            }

            WidgetStyle.ARCH -> {
                val area = centeredArea(RectF(bounds).apply { inset(4 * dp, 4 * dp) }, maxAspect = 0.9f)
                val top = area.width() / 2f
                val bottom = 20 * dp
                drawPhoto(canvas, source, area, roundRect(area, top, top, bottom, bottom), shadow = dp)
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.PETAL -> {
                val area = RectF(bounds).apply { inset(4 * dp, 4 * dp) }
                val big = min(area.width(), area.height()) * 0.42f
                val small = 14 * dp
                drawPhoto(canvas, source, area, roundRect(area, big, small, big, small), shadow = dp)
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.CIRCLE -> {
                val radius = min(w, h) / 2f - 5 * dp
                val cx = w / 2f
                val cy = h / 2f
                canvas.drawCircle(cx, cy, radius, shadowPaint(Color.WHITE, dp))
                val inner = radius - 4 * dp
                val area = RectF(cx - inner, cy - inner, cx + inner, cy + inner)
                drawPhoto(canvas, source, area, roundRect(area, inner))
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.POLAROID -> drawPolaroid(canvas, source, bounds, dp, caption)

            WidgetStyle.FRAME -> drawFrame(canvas, source, bounds, dp, caption)
        }
        return out
    }

    private fun drawPolaroid(canvas: Canvas, source: Bitmap, bounds: RectF, dp: Float, caption: String?) {
        // Una leve inclinación, como una foto apoyada sobre la mesa.
        canvas.save()
        canvas.rotate(-2.5f, bounds.centerX(), bounds.centerY())
        val card = RectF(bounds).apply { inset(max(10 * dp, width() * 0.05f), max(10 * dp, height() * 0.05f)) }
        canvas.drawRoundRect(card, 3 * dp, 3 * dp, shadowPaint(Color.WHITE, dp))
        val margin = min(card.width(), card.height()) * 0.06f
        val photo = RectF(card.left + margin, card.top + margin, card.right - margin, card.bottom - margin * 3.8f)
        drawPhoto(canvas, source, photo, roundRect(photo, 1.5f * dp))
        if (caption != null) {
            val paint = textPaint(Color.rgb(0x4A, 0x40, 0x3A), Typeface.create("cursive", Typeface.NORMAL)).apply {
                textAlign = Paint.Align.CENTER
                textSize = min(margin * 1.5f, 20 * dp)
            }
            val baseline = (photo.bottom + card.bottom) / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(caption, card.centerX(), baseline, paint)
        }
        canvas.restore()
    }

    private fun drawFrame(canvas: Canvas, source: Bitmap, bounds: RectF, dp: Float, caption: String?) {
        val frame = RectF(bounds).apply { inset(4 * dp, 4 * dp) }
        canvas.drawRoundRect(frame, 4 * dp, 4 * dp, shadowPaint(Color.rgb(0x2E, 0x26, 0x22), dp))
        val border = max(5 * dp, min(frame.width(), frame.height()) * 0.035f)
        val mat = RectF(frame).apply { inset(border, border) }
        canvas.drawRect(mat, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF7, 0xF2, 0xEA) })
        val matWidth = min(mat.width(), mat.height()) * 0.11f
        val photo = RectF(mat).apply { inset(matWidth, matWidth) }
        drawPhoto(canvas, source, photo, roundRect(photo, 0f))
        // Línea fina del bisel del passepartout.
        canvas.drawRect(photo, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 0.75f * dp)
            color = 0x33000000
        })
        if (caption != null) {
            val paint = textPaint(Color.rgb(0x6B, 0x60, 0x58), Typeface.create(Typeface.SERIF, Typeface.ITALIC)).apply {
                textAlign = Paint.Align.CENTER
                textSize = min(matWidth * 0.55f, 13 * dp)
            }
            val baseline = (photo.bottom + mat.bottom) / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(caption, mat.centerX(), baseline, paint)
        }
    }

    /** Área centrada dentro de [bounds] que no es más ancha que [maxAspect] veces su alto. */
    private fun centeredArea(bounds: RectF, maxAspect: Float): RectF {
        val area = RectF(bounds)
        val width = min(area.width(), area.height() * maxAspect)
        area.inset((area.width() - width) / 2f, 0f)
        return area
    }

    /** Radios de las esquinas en orden: arriba-izq., arriba-der., abajo-der., abajo-izq. */
    private fun roundRect(rect: RectF, tl: Float, tr: Float = tl, br: Float = tl, bl: Float = tr): Path =
        Path().apply {
            addRoundRect(rect, floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), Path.Direction.CW)
        }

    /** Dibuja [src] recortado al centro para cubrir [dst], con la forma de [shape]. */
    private fun drawPhoto(canvas: Canvas, src: Bitmap, dst: RectF, shape: Path, shadow: Float = 0f) {
        if (shadow > 0f) canvas.drawPath(shape, shadowPaint(Color.WHITE, shadow))
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
        canvas.drawPath(shape, paint)
    }

    /** Etiqueta translúcida sobre la foto. */
    private fun drawPill(canvas: Canvas, text: String, area: RectF, dp: Float, centered: Boolean) {
        val paint = textPaint(Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply {
            textSize = 11.5f * dp
        }
        val padH = 9 * dp
        val height = 22 * dp
        val width = paint.measureText(text) + padH * 2
        val bottom = area.bottom - 12 * dp
        val left = if (centered) area.centerX() - width / 2f else area.left + 12 * dp
        val pill = RectF(left, bottom - height, left + width, bottom)
        canvas.drawRoundRect(pill, height / 2f, height / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73000000
        })
        val baseline = pill.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, pill.left + padH, baseline, paint)
    }

    private fun shadowPaint(fill: Int, dp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill
        setShadowLayer(4 * dp, 0f, 1.5f * dp, SHADOW)
    }

    private fun textPaint(color: Int, typeface: Typeface) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            this.typeface = typeface
        }
}
