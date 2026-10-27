package app.fototin.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/** Una foto lista para dibujar, con el punto que conviene mantener a la vista al recortarla. */
class StyledSource(val bitmap: Bitmap, val focusX: Float = 0.5f, val focusY: Float = 0.5f)

/**
 * Dibuja la foto con el estilo elegido en un bitmap del tamaño exacto del widget.
 *
 * Se hace todo en el bitmap (y no con modificadores de Glance) para que formas, sombras y marcos
 * se vean igual en todas las versiones de Android.
 */
object PhotoStyler {

    /** Límite del lado más largo para no superar la memoria permitida a los widgets. */
    private const val MAX_OUTPUT_PX = 900

    internal const val SHADOW = 0x40000000

    /**
     * @param caption texto opcional (por ejemplo, la fecha) que se dibuja sobre la foto o,
     * en la Polaroid y el Marco, en el borde inferior.
     * @param cornerDp radio de las esquinas del estilo Redondeado; conviene usar el del launcher.
     */
    fun render(
        source: StyledSource,
        style: WidgetStyle,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        caption: String? = null,
        cornerDp: Float = 28f,
    ): Bitmap {
        val (out, canvas, dp) = canvasFor(widthPx, heightPx, density)
        val bounds = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())

        when (style) {
            WidgetStyle.FULL -> {
                drawPhoto(canvas, source, bounds, roundRect(bounds, 0f))
                caption?.let { drawPill(canvas, it, bounds, dp, centered = false) }
            }

            WidgetStyle.ROUNDED -> {
                drawPhoto(canvas, source, bounds, roundRect(bounds, cornerDp * dp))
                caption?.let { drawPill(canvas, it, bounds, dp, centered = false) }
            }

            WidgetStyle.ARCH -> {
                val area = centeredArea(inset(bounds, 4 * dp), maxAspect = 0.9f)
                val top = area.width() / 2f
                val bottom = 20 * dp
                drawPhoto(canvas, source, area, roundRect(area, top, top, bottom, bottom), shadow = dp)
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.PETAL -> {
                val area = inset(bounds, 4 * dp)
                val big = min(area.width(), area.height()) * 0.42f
                val small = 14 * dp
                drawPhoto(canvas, source, area, roundRect(area, big, small, big, small), shadow = dp)
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.CIRCLE -> {
                val radius = min(bounds.width(), bounds.height()) / 2f - 5 * dp
                val cx = bounds.centerX()
                val cy = bounds.centerY()
                canvas.drawCircle(cx, cy, radius, shadowPaint(Color.WHITE, dp))
                val inner = radius - 4 * dp
                val area = RectF(cx - inner, cy - inner, cx + inner, cy + inner)
                drawPhoto(canvas, source, area, roundRect(area, inner))
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.HEART -> {
                val side = min(bounds.width(), bounds.height()) - 10 * dp
                val outer = RectF(
                    bounds.centerX() - side / 2f, bounds.centerY() - side / 2f,
                    bounds.centerX() + side / 2f, bounds.centerY() + side / 2f,
                )
                canvas.drawPath(heart(outer), shadowPaint(Color.WHITE, dp))
                val area = inset(outer, 5 * dp)
                drawPhoto(canvas, source, area, heart(area))
                caption?.let { drawPill(canvas, it, RectF(area.left, area.top, area.right, area.bottom - side * 0.12f), dp, centered = true) }
            }

            WidgetStyle.STAMP -> drawStamp(canvas, source, bounds, dp, caption)

            WidgetStyle.POLAROID -> drawPolaroid(canvas, source, bounds, dp, caption, angle = -2.5f, tape = false)

            WidgetStyle.WASHI -> drawPolaroid(canvas, source, bounds, dp, caption, angle = 2f, tape = true)

            WidgetStyle.FRAME -> drawFrame(canvas, source, bounds, dp, caption)
        }
        return out
    }

    internal data class Target(val bitmap: Bitmap, val canvas: Canvas, val dp: Float)

    internal fun canvasFor(widthPx: Int, heightPx: Int, density: Float): Target {
        val fit = min(1f, MAX_OUTPUT_PX.toFloat() / max(widthPx, heightPx).coerceAtLeast(1))
        val w = (widthPx * fit).toInt().coerceAtLeast(1)
        val h = (heightPx * fit).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return Target(bitmap, Canvas(bitmap), density * fit)
    }

    internal fun split(rect: RectF, gap: Float, horizontal: Boolean): List<RectF> =
        if (horizontal) {
            val mid = rect.left + (rect.width() - gap) / 2f
            listOf(RectF(rect.left, rect.top, mid, rect.bottom), RectF(mid + gap, rect.top, rect.right, rect.bottom))
        } else {
            val mid = rect.top + (rect.height() - gap) / 2f
            listOf(RectF(rect.left, rect.top, rect.right, mid), RectF(rect.left, mid + gap, rect.right, rect.bottom))
        }

    private fun drawPolaroid(
        canvas: Canvas,
        source: StyledSource,
        bounds: RectF,
        dp: Float,
        caption: String?,
        angle: Float,
        tape: Boolean,
    ) {
        // Una leve inclinación, como una foto apoyada sobre la mesa.
        canvas.save()
        canvas.rotate(angle, bounds.centerX(), bounds.centerY())
        val card = RectF(bounds).apply {
            inset(max(10 * dp, width() * 0.05f), max(if (tape) 16 * dp else 10 * dp, height() * 0.06f))
        }
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
        if (tape) drawTape(canvas, card, dp)
        canvas.restore()
    }

    /** Cinta washi translúcida pegada arriba de la foto. */
    private fun drawTape(canvas: Canvas, card: RectF, dp: Float) {
        val width = card.width() * 0.38f
        val height = max(14 * dp, card.height() * 0.09f)
        val tape = RectF(card.centerX() - width / 2f, card.top - height / 2f, card.centerX() + width / 2f, card.top + height / 2f)
        canvas.save()
        canvas.rotate(-5f, tape.centerX(), tape.centerY())
        canvas.drawRect(tape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC8F2B8A2.toInt() })
        // Rayitas diagonales para que parezca papel.
        val stripes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40FFFFFF
            strokeWidth = 3 * dp
        }
        canvas.save()
        canvas.clipRect(tape)
        var x = tape.left - tape.height()
        while (x < tape.right) {
            canvas.drawLine(x, tape.bottom, x + tape.height(), tape.top, stripes)
            x += 9 * dp
        }
        canvas.restore()
        canvas.restore()
    }

    /** Estampilla con borde dentado. */
    private fun drawStamp(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, caption: String?) {
        val paper = inset(bounds, 4 * dp)
        canvas.drawRect(paper, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xFC, 0xF7) })
        // Perforaciones: círculos borrados a lo largo de los bordes.
        val hole = 3.5f * dp
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        fun perforate(from: Float, to: Float, at: (Float) -> Pair<Float, Float>) {
            val count = ((to - from) / (hole * 2.8f)).toInt().coerceAtLeast(2)
            val step = (to - from) / count
            for (i in 0..count) {
                val (x, y) = at(from + i * step)
                canvas.drawCircle(x, y, hole, clear)
            }
        }
        perforate(paper.left, paper.right) { it to paper.top }
        perforate(paper.left, paper.right) { it to paper.bottom }
        perforate(paper.top, paper.bottom) { paper.left to it }
        perforate(paper.top, paper.bottom) { paper.right to it }

        val photo = inset(paper, hole + 6 * dp)
        drawPhoto(canvas, source, photo, roundRect(photo, 0f))
        canvas.drawRect(photo, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 0.75f * dp)
            color = 0x22000000
        })
        caption?.let { drawPill(canvas, it, photo, dp, centered = false) }
    }

    private fun drawFrame(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, caption: String?) {
        val frame = inset(bounds, 4 * dp)
        canvas.drawRoundRect(frame, 4 * dp, 4 * dp, shadowPaint(Color.rgb(0x2E, 0x26, 0x22), dp))
        val border = max(5 * dp, min(frame.width(), frame.height()) * 0.035f)
        val mat = inset(frame, border)
        canvas.drawRect(mat, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF7, 0xF2, 0xEA) })
        val matWidth = min(mat.width(), mat.height()) * 0.11f
        val photo = inset(mat, matWidth)
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

    internal fun inset(rect: RectF, by: Float) = RectF(rect).apply { inset(by, by) }

    /** Área centrada dentro de [bounds] que no es más ancha que [maxAspect] veces su alto. */
    private fun centeredArea(bounds: RectF, maxAspect: Float): RectF {
        val area = RectF(bounds)
        val width = min(area.width(), area.height() * maxAspect)
        area.inset((area.width() - width) / 2f, 0f)
        return area
    }

    /** Radios de las esquinas en orden: arriba-izq., arriba-der., abajo-der., abajo-izq. */
    internal fun roundRect(rect: RectF, tl: Float, tr: Float = tl, br: Float = tl, bl: Float = tr): Path =
        Path().apply {
            addRoundRect(rect, floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), Path.Direction.CW)
        }

    private fun heart(r: RectF): Path {
        val w = r.width()
        val h = r.height()
        fun x(f: Float) = r.left + w * f
        fun y(f: Float) = r.top + h * f
        return Path().apply {
            moveTo(x(0.5f), y(0.22f))
            cubicTo(x(0.36f), y(0f), x(0f), y(0.04f), x(0.03f), y(0.38f))
            cubicTo(x(0.06f), y(0.64f), x(0.4f), y(0.84f), x(0.5f), y(0.98f))
            cubicTo(x(0.6f), y(0.84f), x(0.94f), y(0.64f), x(0.97f), y(0.38f))
            cubicTo(x(1f), y(0.04f), x(0.64f), y(0f), x(0.5f), y(0.22f))
            close()
        }
    }

    /**
     * Dibuja la foto recortada para cubrir [dst], con la forma de [shape], centrando el recorte en
     * el punto de foco de la foto sin dejar huecos.
     */
    internal fun drawPhoto(canvas: Canvas, source: StyledSource, dst: RectF, shape: Path, shadow: Float = 0f) {
        if (shadow > 0f) canvas.drawPath(shape, shadowPaint(Color.WHITE, shadow))
        val src = source.bitmap
        val scale = max(dst.width() / src.width, dst.height() / src.height)
        val scaledW = src.width * scale
        val scaledH = src.height * scale
        // min() evita un rango invertido por errores de redondeo cuando la foto cubre justo el área.
        val dx = (dst.centerX() - source.focusX * scaledW).coerceIn(min(dst.right - scaledW, dst.left), dst.left)
        val dy = (dst.centerY() - source.focusY * scaledH).coerceIn(min(dst.bottom - scaledH, dst.top), dst.top)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
        }
        canvas.drawPath(shape, paint)
    }

    /** Etiqueta translúcida sobre la foto. */
    internal fun drawPill(canvas: Canvas, text: String, area: RectF, dp: Float, centered: Boolean) {
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

    internal fun shadowPaint(fill: Int, dp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill
        setShadowLayer(4 * dp, 0f, 1.5f * dp, SHADOW)
    }

    internal fun textPaint(color: Int, typeface: Typeface) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            this.typeface = typeface
        }
}
