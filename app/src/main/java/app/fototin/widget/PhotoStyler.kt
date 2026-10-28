package app.fototin.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    internal const val MAX_OUTPUT_PX = 900

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
        maxOutputPx: Int = MAX_OUTPUT_PX,
    ): Bitmap {
        val (out, canvas, dp) = canvasFor(widthPx, heightPx, density, maxOutputPx)
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

            WidgetStyle.FIT -> drawFit(canvas, source, bounds, dp, cornerDp * dp, caption)

            WidgetStyle.CALENDAR -> drawCalendar(canvas, source, bounds, dp, cornerDp * dp, caption)

            WidgetStyle.POSTCARD -> drawPostcard(canvas, source, bounds, dp, caption)

            WidgetStyle.GLASS -> drawGlass(canvas, source, bounds, dp, cornerDp * dp, caption)

            WidgetStyle.COVER -> drawCover(canvas, source, bounds, dp, cornerDp * dp, caption)

            WidgetStyle.SLIDE -> drawSlide(canvas, source, bounds, dp, caption)

            WidgetStyle.MONO -> {
                val shape = roundRect(bounds, cornerDp * dp)
                drawPhoto(canvas, source, bounds, shape, filter = MONO_FILTER)
                drawVignette(canvas, bounds, shape)
                caption?.let { drawPill(canvas, it, bounds, dp, centered = false) }
            }

            WidgetStyle.BLOB -> {
                val area = centeredArea(inset(bounds, 8 * dp), maxAspect = 1.25f).let { a ->
                    // Tampoco más alta que 1,25 veces su ancho.
                    val h = min(a.height(), a.width() * 1.25f)
                    RectF(a.left, a.centerY() - h / 2f, a.right, a.centerY() + h / 2f)
                }
                val shape = polar(area) { t -> 1f + 0.055f * kotlin.math.sin(3 * t + 0.6f) + 0.035f * kotlin.math.cos(5 * t) }
                drawPhoto(canvas, source, area, shape, shadow = dp)
                caption?.let { drawPill(canvas, it, area, dp, centered = true) }
            }

            WidgetStyle.FLOWER -> {
                val side = min(bounds.width(), bounds.height()) - 10 * dp
                val area = RectF(bounds.centerX() - side / 2f, bounds.centerY() - side / 2f, bounds.centerX() + side / 2f, bounds.centerY() + side / 2f)
                // Borde ondulado, como las formas de Material You.
                val shape = polar(area) { t -> 0.93f + 0.07f * kotlin.math.cos(8 * t) }
                drawPhoto(canvas, source, area, shape, shadow = dp)
                caption?.let { drawPill(canvas, it, inset(area, side * 0.08f), dp, centered = true) }
            }
        }
        return out
    }

    internal data class Target(val bitmap: Bitmap, val canvas: Canvas, val dp: Float)

    internal fun canvasFor(widthPx: Int, heightPx: Int, density: Float, maxOutputPx: Int = MAX_OUTPUT_PX): Target {
        val fit = min(1f, maxOutputPx.toFloat() / max(widthPx, heightPx).coerceAtLeast(1))
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
        val loose = RectF(bounds).apply {
            inset(max(10 * dp, width() * 0.05f), max(if (tape) 16 * dp else 10 * dp, height() * 0.06f))
        }
        // Girada, la tarjeta (y la cinta que sobresale arriba) tiene que entrar con lugar para la sombra.
        val tapeRoom = if (tape) max(14 * dp, loose.height() * 0.09f) else 0f
        val card = scaled(
            loose,
            rotatedFit(loose.width(), loose.height(), angle, bounds.width() - 12 * dp, bounds.height() - 12 * dp - tapeRoom),
        )
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
    internal fun drawTape(canvas: Canvas, card: RectF, dp: Float) {
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

    /** La foto entera, sin recortar, sobre un fondo desenfocado de la misma foto. */
    private fun drawFit(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, corner: Float, caption: String?) {
        // Desenfoque barato: achicar mucho y volver a agrandar con filtrado.
        val src = source.bitmap
        val tiny = Bitmap.createScaledBitmap(src, 24, (24f * src.height / src.width).toInt().coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, 96, (96f * src.height / src.width).toInt().coerceAtLeast(1), true)
        drawPhoto(canvas, StyledSource(blurred, source.focusX, source.focusY), bounds, roundRect(bounds, corner))
        canvas.drawPath(roundRect(bounds, corner), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        if (tiny !== src) tiny.recycle()
        blurred.recycle()
        // La foto entera, con su proporción, centrada.
        val area = inset(bounds, max(8 * dp, min(bounds.width(), bounds.height()) * 0.05f))
        val scale = min(area.width() / src.width, area.height() / src.height)
        val w = src.width * scale
        val h = src.height * scale
        val photo = RectF(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2)
        drawPhoto(canvas, source, photo, roundRect(photo, 10 * dp), shadow = dp)
        caption?.let { drawPill(canvas, it, photo, dp, centered = true) }
    }

    /** La foto con la fecha de hoy en grande, como una hoja de calendario. */
    private fun drawCalendar(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, corner: Float, caption: String?) {
        drawPhoto(canvas, source, bounds, roundRect(bounds, corner))
        // Sombreado abajo para que el texto se lea sobre cualquier foto.
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, bounds.top + bounds.height() * 0.45f, 0f, bounds.bottom,
                0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(roundRect(bounds, corner), scrim)
        val today = Date()
        val size = min(bounds.width(), bounds.height())
        val dayPaint = textPaint(Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD)).apply { textSize = size * 0.26f }
        val day = SimpleDateFormat("d", Locale.getDefault()).format(today)
        val margin = max(12 * dp, size * 0.07f)
        val baseline = bounds.bottom - margin
        canvas.drawText(day, bounds.left + margin, baseline, dayPaint)
        val dayWidth = dayPaint.measureText(day)
        val month = SimpleDateFormat("MMM", Locale.getDefault()).format(today).replace(".", "").uppercase()
        val weekday = SimpleDateFormat("EEEE", Locale.getDefault()).format(today)
        val monthPaint = textPaint(Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply { textSize = size * 0.085f }
        val weekdayPaint = textPaint(0xDDFFFFFF.toInt(), Typeface.create("sans-serif", Typeface.NORMAL)).apply { textSize = size * 0.065f }
        val x = bounds.left + margin + dayWidth + size * 0.035f
        canvas.drawText(month, x, baseline - weekdayPaint.textSize * 1.25f, monthPaint)
        canvas.drawText(weekday, x, baseline, weekdayPaint)
        caption?.let {
            val pill = RectF(bounds.left, bounds.top, bounds.right, bounds.top + 22 * dp + 24 * dp)
            drawPill(canvas, it, pill, dp, centered = false)
        }
    }

    /** Tarjeta postal: la foto, una estampilla con la misma foto, el matasellos y el texto a mano. */
    private fun drawPostcard(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, caption: String?) {
        val card = inset(bounds, 6 * dp)
        canvas.drawRoundRect(card, 4 * dp, 4 * dp, shadowPaint(Color.rgb(0xFB, 0xF7, 0xEF), dp))
        val wide = card.width() >= card.height()
        val pad = min(card.width(), card.height()) * 0.06f
        val photo = if (wide) {
            RectF(card.left + pad, card.top + pad, card.left + card.width() * 0.56f, card.bottom - pad)
        } else {
            RectF(card.left + pad, card.top + pad, card.right - pad, card.top + card.height() * 0.6f)
        }
        drawPhoto(canvas, source, photo, roundRect(photo, 2 * dp))
        val side = if (wide) {
            RectF(photo.right + pad, card.top + pad, card.right - pad, card.bottom - pad)
        } else {
            RectF(card.left + pad, photo.bottom + pad, card.right - pad, card.bottom - pad)
        }
        // Estampilla en la esquina, con la misma foto en miniatura.
        val stampSize = min(side.width() * 0.34f, side.height() * 0.42f)
        val stamp = RectF(side.right - stampSize, side.top, side.right, side.top + stampSize * 1.15f)
        canvas.drawRect(stamp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRect(stamp, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, dp)
            color = 0x33000000
            pathEffect = DashPathEffect(floatArrayOf(2 * dp, 2 * dp), 0f)
        })
        val stampPhoto = inset(stamp, stampSize * 0.1f)
        drawPhoto(canvas, source, stampPhoto, roundRect(stampPhoto, 0f))
        // Matasellos: un círculo y ondas, en tinta gastada.
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 1.2f * dp)
            color = 0x664A3F66
        }
        val r = stampSize * 0.42f
        val cx = stamp.left - r * 0.6f
        val cy = stamp.centerY() + r * 0.3f
        canvas.drawCircle(cx, cy, r, ink)
        for (k in 0..2) {
            val y = cy - r * 0.4f + k * r * 0.4f
            val wave = Path().apply {
                moveTo(cx + r * 0.9f, y)
                var x = cx + r * 0.9f
                var up = true
                while (x < stamp.left + stampSize * 0.6f) {
                    quadTo(x + r * 0.2f, y + if (up) -r * 0.12f else r * 0.12f, x + r * 0.4f, y)
                    x += r * 0.4f
                    up = !up
                }
            }
            canvas.drawPath(wave, ink)
        }
        // Renglones y el texto escrito a mano.
        val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = max(1f, 0.8f * dp)
            color = 0x26000000
        }
        val textTop = stamp.bottom + pad * 0.6f
        val lineGap = max(14 * dp, (side.bottom - textTop) / 3.2f)
        val hand = textPaint(Color.rgb(0x2E, 0x2A, 0x6B), Typeface.create("cursive", Typeface.NORMAL)).apply {
            textSize = min(lineGap * 0.62f, 20 * dp)
        }
        var y = textTop + lineGap
        val words = caption.orEmpty().split(" ").filter { it.isNotEmpty() }.toMutableList()
        while (y <= side.bottom + 1) {
            canvas.drawLine(side.left, y, side.right, y, lines)
            // Las palabras que entren en este renglón.
            var text = ""
            while (words.isNotEmpty() && hand.measureText(if (text.isEmpty()) words[0] else "$text ${words[0]}") <= side.width()) {
                text = if (text.isEmpty()) words.removeAt(0) else "$text ${words.removeAt(0)}"
            }
            if (text.isNotEmpty()) canvas.drawText(text, side.left + 2 * dp, y - lineGap * 0.18f, hand)
            y += lineGap
        }
    }

    /**
     * Tarjeta de vidrio esmerilado sobre la foto, con la nota o la fecha, como los widgets
     * actuales de iOS y Android.
     */
    private fun drawGlass(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, corner: Float, caption: String?) {
        drawPhoto(canvas, source, bounds, roundRect(bounds, corner))
        val margin = max(8 * dp, min(bounds.width(), bounds.height()) * 0.05f)
        val panelHeight = max(44 * dp, bounds.height() * 0.24f).coerceAtMost(bounds.height() - margin * 2)
        val panel = RectF(bounds.left + margin, bounds.bottom - margin - panelHeight, bounds.right - margin, bounds.bottom - margin)
        val panelCorner = max(corner - margin, 12 * dp)
        val panelShape = roundRect(panel, panelCorner)
        // La misma foto, desenfocada, dibujada solo dentro de la tarjeta: queda alineada con la de atrás.
        val blurred = blurredCopy(source.bitmap)
        drawPhoto(canvas, StyledSource(blurred, source.focusX, source.focusY), bounds, panelShape)
        blurred.recycle()
        canvas.drawPath(panelShape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF })
        canvas.drawPath(panelShape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, dp)
            color = 0x66FFFFFF
        })
        val title = caption ?: SimpleDateFormat("EEEE d", Locale.getDefault()).format(Date())
        val paint = textPaint(Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply {
            textSize = min(panel.height() * 0.36f, 17 * dp)
            setShadowLayer(3 * dp, 0f, 0.5f * dp, 0x55000000)
        }
        val pad = panel.height() * 0.32f
        val baseline = panel.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(ellipsize(title, paint, panel.width() - pad * 2), panel.left + pad, baseline, paint)
    }

    /** Tapa de revista: el mes en grande arriba y la nota abajo, en letra de imprenta. */
    private fun drawCover(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, corner: Float, caption: String?) {
        val shape = roundRect(bounds, corner)
        drawPhoto(canvas, source, bounds, shape)
        canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, bounds.top, 0f, bounds.bottom,
                intArrayOf(0x8C000000.toInt(), 0x00000000, 0x00000000, 0x80000000.toInt()),
                floatArrayOf(0f, 0.35f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
        val margin = max(12 * dp, min(bounds.width(), bounds.height()) * 0.07f)
        val today = Date()
        val masthead = SimpleDateFormat("MMMM", Locale.getDefault()).format(today).uppercase()
        val title = textPaint(Color.WHITE, Typeface.create(Typeface.SERIF, Typeface.BOLD)).apply {
            letterSpacing = 0.04f
            textSize = 100f
            // Que el título ocupe el ancho, como en las revistas.
            textSize = min(100f * (bounds.width() - margin * 2) / measureText(masthead), bounds.height() * 0.2f)
        }
        val titleBaseline = bounds.top + margin - title.ascent() * 0.92f
        canvas.drawText(masthead, bounds.left + margin, titleBaseline, title)
        val issue = textPaint(0xDDFFFFFF.toInt(), Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply {
            letterSpacing = 0.12f
            textSize = max(8 * dp, title.textSize * 0.16f)
        }
        val edition = "EDICIÓN " + SimpleDateFormat("d · yyyy", Locale.getDefault()).format(today)
        canvas.drawText(edition, bounds.left + margin, titleBaseline + issue.textSize * 1.6f, issue)
        if (caption != null) {
            val text = textPaint(Color.WHITE, Typeface.create(Typeface.SERIF, Typeface.ITALIC)).apply {
                textSize = min(18 * dp, bounds.height() * 0.09f)
            }
            canvas.drawText(ellipsize(caption, text, bounds.width() - margin * 2), bounds.left + margin, bounds.bottom - margin, text)
        }
    }

    /** Diapositiva de 35 mm: marco de cartón con la foto en la ventana. */
    private fun drawSlide(canvas: Canvas, source: StyledSource, bounds: RectF, dp: Float, caption: String?) {
        val side = min(bounds.width(), bounds.height()) - 10 * dp
        val mount = RectF(bounds.centerX() - side / 2f, bounds.centerY() - side / 2f, bounds.centerX() + side / 2f, bounds.centerY() + side / 2f)
        canvas.drawRoundRect(mount, side * 0.06f, side * 0.06f, shadowPaint(Color.rgb(0xF4, 0xF1, 0xEA), dp))
        val border = side * 0.17f
        val window = RectF(mount.left + border, mount.top + border, mount.right - border, mount.bottom - border * 1.05f)
        drawPhoto(canvas, source, window, roundRect(window, side * 0.025f))
        // Bisel del cartón alrededor de la ventana.
        canvas.drawPath(roundRect(window, side * 0.025f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, dp)
            color = 0x33000000
        })
        val label = textPaint(Color.rgb(0x6F, 0x67, 0x5E), Typeface.MONOSPACE).apply {
            textAlign = Paint.Align.CENTER
            textSize = min(border * 0.32f, 11 * dp)
            letterSpacing = 0.08f
        }
        val top = (mount.top + window.top) / 2f - (label.ascent() + label.descent()) / 2f
        canvas.drawText("FOTOTIN · 35", mount.centerX(), top, label)
        caption?.let {
            val bottom = (window.bottom + mount.bottom) / 2f - (label.ascent() + label.descent()) / 2f
            canvas.drawText(ellipsize(it, label, window.width()), mount.centerX(), bottom, label)
        }
    }

    /** Oscurece apenas los bordes, como las fotos analógicas. */
    private fun drawVignette(canvas: Canvas, bounds: RectF, shape: Path) {
        canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                bounds.centerX(), bounds.centerY(), max(bounds.width(), bounds.height()) * 0.75f,
                intArrayOf(0x00000000, 0x00000000, 0x59000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
    }

    /** Blanco y negro con un poco más de contraste. */
    private val MONO_FILTER = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.12f
            val shift = 128f * (1f - contrast)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            )))
        },
    )

    /** Forma redondeada irregular: el radio de cada ángulo lo da [radius] (1 = el borde de [area]). */
    private fun polar(area: RectF, radius: (Float) -> Float): Path {
        val steps = 120
        val rx = area.width() / 2f
        val ry = area.height() / 2f
        // Se achica para que los picos no se salgan del área.
        val peak = (0 until steps).maxOf { radius(it * 2f * Math.PI.toFloat() / steps) }
        return Path().apply {
            for (i in 0..steps) {
                val t = i * 2f * Math.PI.toFloat() / steps
                val f = radius(t) / peak
                val x = area.centerX() + rx * f * kotlin.math.cos(t)
                val y = area.centerY() + ry * f * kotlin.math.sin(t)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /** Copia muy desenfocada (achicar y volver a agrandar con filtrado). */
    internal fun blurredCopy(src: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(src, 24, (24f * src.height / src.width).toInt().coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, 96, (96f * src.height / src.width).toInt().coerceAtLeast(1), true)
        if (tiny !== src && tiny !== blurred) tiny.recycle()
        return blurred
    }

    internal fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    internal fun inset(rect: RectF, by: Float) = RectF(rect).apply { inset(by, by) }

    /**
     * Cuánto achicar un rectángulo de [w] × [h] para que, girado [degrees] grados, entre en
     * [maxW] × [maxH]. Sin esto, las puntas de las fotos inclinadas se salen del widget y se cortan.
     */
    internal fun rotatedFit(w: Float, h: Float, degrees: Float, maxW: Float, maxH: Float): Float {
        val turn = Math.toRadians(degrees.toDouble())
        val sin = kotlin.math.abs(kotlin.math.sin(turn)).toFloat()
        val cos = kotlin.math.abs(kotlin.math.cos(turn)).toFloat()
        return min(1f, min(maxW / (w * cos + h * sin), maxH / (w * sin + h * cos)))
    }

    /** [rect] achicado por [factor] alrededor de su centro. */
    internal fun scaled(rect: RectF, factor: Float): RectF {
        val w = rect.width() * factor / 2f
        val h = rect.height() * factor / 2f
        return RectF(rect.centerX() - w, rect.centerY() - h, rect.centerX() + w, rect.centerY() + h)
    }

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
    internal fun drawPhoto(
        canvas: Canvas,
        source: StyledSource,
        dst: RectF,
        shape: Path,
        shadow: Float = 0f,
        filter: android.graphics.ColorFilter? = null,
    ) {
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
            colorFilter = filter
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
