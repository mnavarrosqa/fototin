package app.fototin.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Dibuja el widget Collage: varias fotos en un solo bitmap del tamaño del widget, con el diseño
 * elegido. Usa las mismas piezas que [PhotoStyler] (recorte con foco, sombras, etiquetas).
 */
object CollageStyler {

    /** Proporción a partir de la cual el mosaico pasa a una sola fila o columna. */
    private const val STRIP_ASPECT = 2.4f

    /**
     * @param captions nota o fecha de cada foto (o null para no mostrar nada).
     * @param seed varía la inclinación de las polaroids según las fotos, siempre igual para las mismas.
     */
    fun render(
        sources: List<StyledSource>,
        style: CollageStyle,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        cornerDp: Float = 20f,
        captions: List<String?> = emptyList(),
        seed: Int = 0,
        maxOutputPx: Int = PhotoStyler.MAX_OUTPUT_PX,
    ): Bitmap {
        val (out, canvas, dp) = PhotoStyler.canvasFor(widthPx, heightPx, density, maxOutputPx)
        val bounds = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        if (sources.isEmpty()) return out
        when (style) {
            CollageStyle.MOSAIC -> drawCells(canvas, sources, mosaicCells(bounds, sources.size, 4 * dp), bounds, cornerDp * dp, dp, captions)
            CollageStyle.MAGAZINE -> drawCells(canvas, sources, magazineCells(bounds, sources.size, 4 * dp), bounds, cornerDp * dp, dp, captions)
            CollageStyle.POLAROIDS -> drawPolaroids(canvas, sources, bounds, dp, captions, seed)
            CollageStyle.PHOTOBOOTH -> drawPhotobooth(canvas, sources, bounds, dp)
            CollageStyle.FILM -> drawFilm(canvas, sources, bounds, dp)
        }
        return out
    }

    // ---- Mosaico y Revista: celdas que llenan el widget ----

    private fun drawCells(
        canvas: Canvas,
        sources: List<StyledSource>,
        cells: List<RectF>,
        bounds: RectF,
        outer: Float,
        dp: Float,
        captions: List<String?>,
    ) {
        // Las esquinas exteriores siguen el radio del launcher; las interiores son más chicas.
        val inner = 8 * dp
        cells.zip(sources).forEachIndexed { i, (cell, source) ->
            val left = cell.left == bounds.left
            val top = cell.top == bounds.top
            val right = cell.right == bounds.right
            val bottom = cell.bottom == bounds.bottom
            fun corner(isOuter: Boolean) = if (isOuter) outer else inner
            PhotoStyler.drawPhoto(
                canvas, source, cell,
                PhotoStyler.roundRect(cell, corner(left && top), corner(right && top), corner(right && bottom), corner(left && bottom)),
            )
            // La etiqueta solo entra en celdas de cierto tamaño.
            val caption = captions.getOrNull(i)
            if (caption != null && min(cell.width(), cell.height()) > 72 * dp) {
                PhotoStyler.drawPill(canvas, caption, cell, dp * 0.85f, centered = false)
            }
        }
    }

    private fun mosaicCells(bounds: RectF, count: Int, gap: Float): List<RectF> {
        val aspect = bounds.width() / bounds.height()
        val wide = aspect >= 1f
        // Muy ancho o muy angosto (pantallas externas de foldables, launchers desplegados):
        // todas en fila o en columna.
        if (count > 1 && aspect >= STRIP_ASPECT) return strip(bounds, count, gap, horizontal = true)
        if (count > 1 && aspect <= 1f / STRIP_ASPECT) return strip(bounds, count, gap, horizontal = false)
        return when (count) {
            1 -> listOf(bounds)
            2 -> PhotoStyler.split(bounds, gap, horizontal = wide)
            3 -> {
                val (main, rest) = PhotoStyler.split(bounds, gap, horizontal = wide)
                listOf(main) + PhotoStyler.split(rest, gap, horizontal = !wide)
            }
            4 -> grid(bounds, 2, 2, gap)
            5 -> {
                val (main, rest) = PhotoStyler.split(bounds, gap, horizontal = wide)
                listOf(main) + grid(rest, 2, 2, gap)
            }
            else -> if (wide) grid(bounds, 3, 2, gap) else grid(bounds, 2, 3, gap)
        }
    }

    /** Una foto protagonista grande y las demás de tamaños distintos, como una página de revista. */
    private fun magazineCells(bounds: RectF, count: Int, gap: Float): List<RectF> {
        val wide = bounds.width() >= bounds.height()
        val (main, rest) = splitAt(bounds, if (wide) 0.6f else 0.58f, gap, horizontal = wide)
        val others = when (count - 1) {
            1 -> listOf(rest)
            2 -> splitAt(rest, 0.6f, gap, horizontal = !wide).toList()
            3 -> {
                val (a, b) = splitAt(rest, 0.55f, gap, horizontal = !wide)
                listOf(a) + PhotoStyler.split(b, gap, horizontal = wide)
            }
            else -> {
                val (a, b) = splitAt(rest, 0.5f, gap, horizontal = !wide)
                splitAt(a, 0.6f, gap, horizontal = wide).toList() + splitAt(b, 0.4f, gap, horizontal = wide).toList()
            }
        }
        return listOf(main) + others
    }

    private fun splitAt(rect: RectF, fraction: Float, gap: Float, horizontal: Boolean): Pair<RectF, RectF> =
        if (horizontal) {
            val mid = rect.left + (rect.width() - gap) * fraction
            RectF(rect.left, rect.top, mid, rect.bottom) to RectF(mid + gap, rect.top, rect.right, rect.bottom)
        } else {
            val mid = rect.top + (rect.height() - gap) * fraction
            RectF(rect.left, rect.top, rect.right, mid) to RectF(rect.left, mid + gap, rect.right, rect.bottom)
        }

    private fun grid(bounds: RectF, columns: Int, rows: Int, gap: Float): List<RectF> =
        strip(bounds, rows, gap, horizontal = false).flatMap { row -> strip(row, columns, gap, horizontal = true) }

    /** [count] celdas iguales una al lado de la otra (o una debajo de la otra). */
    private fun strip(bounds: RectF, count: Int, gap: Float, horizontal: Boolean): List<RectF> {
        val total = (if (horizontal) bounds.width() else bounds.height()) - gap * (count - 1)
        val size = total / count
        return List(count) { i ->
            val start = (if (horizontal) bounds.left else bounds.top) + i * (size + gap)
            val end = if (i == count - 1) (if (horizontal) bounds.right else bounds.bottom) else start + size
            if (horizontal) RectF(start, bounds.top, end, bounds.bottom) else RectF(bounds.left, start, bounds.right, end)
        }
    }

    // ---- Polaroids desparramadas ----

    private val ANGLES = floatArrayOf(-8f, 6f, -3f, 9f, -6f, 4f)

    private fun drawPolaroids(
        canvas: Canvas,
        sources: List<StyledSource>,
        bounds: RectF,
        dp: Float,
        captions: List<String?>,
        seed: Int,
    ) {
        val n = sources.size
        val horizontal = bounds.width() >= bounds.height()
        val long = max(bounds.width(), bounds.height())
        val short = min(bounds.width(), bounds.height())
        // Tarjetas que se superponen un poco: cada una ocupa ~60% del espacio que le toca.
        var cardW = min(short * 0.8f, long / (n * 0.62f + 0.45f))
        cardW = min(cardW, short * 0.86f / 1.2f * if (horizontal) 1f else 1.2f)
        val cardH = cardW * 1.2f
        val along = if (horizontal) cardW else cardH
        val start = along * 0.58f
        val end = long - along * 0.58f
        val crossRoom = (short - (if (horizontal) cardH else cardW)) / 2f
        sources.forEachIndexed { i, source ->
            val t = if (n == 1) 0.5f else i / (n - 1f)
            val a = start + (end - start) * t
            val wobble = (if (i % 2 == 0) -1 else 1) * crossRoom * 0.55f
            val cx = if (horizontal) a else bounds.centerX() + wobble
            val cy = if (horizontal) bounds.centerY() + wobble else a
            val angle = ANGLES[(i + seed.mod(ANGLES.size)) % ANGLES.size]
            canvas.save()
            canvas.rotate(angle, cx, cy)
            val card = RectF(cx - cardW / 2, cy - cardH / 2, cx + cardW / 2, cy + cardH / 2)
            canvas.drawRoundRect(card, 3 * dp, 3 * dp, PhotoStyler.shadowPaint(Color.WHITE, dp))
            val margin = cardW * 0.06f
            val photo = RectF(card.left + margin, card.top + margin, card.right - margin, card.bottom - margin * 3.6f)
            PhotoStyler.drawPhoto(canvas, source, photo, PhotoStyler.roundRect(photo, 1.5f * dp))
            captions.getOrNull(i)?.let { caption ->
                val paint = PhotoStyler.textPaint(Color.rgb(0x4A, 0x40, 0x3A), Typeface.create("cursive", Typeface.NORMAL)).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = min(margin * 1.6f, 16 * dp)
                }
                val baseline = (photo.bottom + card.bottom) / 2f - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(ellipsize(caption, paint, card.width() - margin * 2), card.centerX(), baseline, paint)
            }
            canvas.restore()
        }
    }

    // ---- Fotomatón: tira de fotos de cabina ----

    private fun drawPhotobooth(canvas: Canvas, sources: List<StyledSource>, bounds: RectF, dp: Float) {
        val n = sources.size
        val vertical = bounds.height() >= bounds.width()
        val long = (if (vertical) bounds.height() else bounds.width()) - 8 * dp
        val short = (if (vertical) bounds.width() else bounds.height()) - 8 * dp
        // Grosor de la tira: lo que entre a lo ancho, sin que las fotos queden más largas que anchas.
        var thickness = min(short, long / (n * 0.8f + 0.3f))
        val margin = thickness * 0.07f
        val frame = thickness - margin * 2
        val frameLong = min(frame * 0.82f, (long - margin * (n + 1) - margin * 2.5f) / n)
        val stripLong = frameLong * n + margin * (n + 1) + margin * 2.5f
        thickness = min(thickness, short)
        val strip = if (vertical) {
            RectF(bounds.centerX() - thickness / 2, bounds.centerY() - stripLong / 2, bounds.centerX() + thickness / 2, bounds.centerY() + stripLong / 2)
        } else {
            RectF(bounds.centerX() - stripLong / 2, bounds.centerY() - thickness / 2, bounds.centerX() + stripLong / 2, bounds.centerY() + thickness / 2)
        }
        canvas.drawRoundRect(strip, 4 * dp, 4 * dp, PhotoStyler.shadowPaint(Color.rgb(0xFB, 0xFA, 0xF7), dp))
        sources.forEachIndexed { i, source ->
            val offset = margin + i * (frameLong + margin)
            val cell = if (vertical) {
                RectF(strip.left + margin, strip.top + offset, strip.right - margin, strip.top + offset + frameLong)
            } else {
                RectF(strip.left + offset, strip.top + margin, strip.left + offset + frameLong, strip.bottom - margin)
            }
            PhotoStyler.drawPhoto(canvas, source, cell, PhotoStyler.roundRect(cell, 1.5f * dp))
        }
    }

    // ---- Película: negativo con perforaciones ----

    private fun drawFilm(canvas: Canvas, sources: List<StyledSource>, bounds: RectF, dp: Float) {
        val n = sources.size
        val horizontal = bounds.width() >= bounds.height()
        val film = PhotoStyler.inset(bounds, 2 * dp)
        canvas.drawRoundRect(film, 6 * dp, 6 * dp, PhotoStyler.shadowPaint(Color.rgb(0x1C, 0x1B, 0x1F), dp))
        val thickness = if (horizontal) film.height() else film.width()
        val length = if (horizontal) film.width() else film.height()
        val hole = max(3 * dp, thickness * 0.07f)
        val rail = hole * 2.2f
        // Perforaciones a lo largo de los dos bordes.
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xE6, 0xE1) }
        val count = (length / (hole * 2.4f)).toInt().coerceAtLeast(2)
        val step = length / count
        for (k in 0 until count) {
            val a = (if (horizontal) film.left else film.top) + step * (k + 0.5f)
            listOf(rail / 2f, thickness - rail / 2f).forEach { c ->
                val center = (if (horizontal) film.top else film.left) + c
                val r = if (horizontal) {
                    RectF(a - hole * 0.55f, center - hole * 0.45f, a + hole * 0.55f, center + hole * 0.45f)
                } else {
                    RectF(center - hole * 0.45f, a - hole * 0.55f, center + hole * 0.45f, a + hole * 0.55f)
                }
                canvas.drawRoundRect(r, hole * 0.2f, hole * 0.2f, holePaint)
            }
        }
        // Cuadros entre las perforaciones.
        val gap = hole * 0.8f
        val area = if (horizontal) {
            RectF(film.left + gap, film.top + rail, film.right - gap, film.bottom - rail)
        } else {
            RectF(film.left + rail, film.top + gap, film.right - rail, film.bottom - gap)
        }
        strip(area, n, gap, horizontal).zip(sources).forEach { (cell, source) ->
            PhotoStyler.drawPhoto(canvas, source, cell, PhotoStyler.roundRect(cell, 1.5f * dp))
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
