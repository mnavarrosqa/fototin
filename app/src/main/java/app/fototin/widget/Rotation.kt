package app.fototin.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fototin.data.PhotoPool
import app.fototin.data.Settings
import app.fototin.data.WidgetSource
import java.util.concurrent.TimeUnit

/**
 * Elige las próximas [count] fotos entre [ids].
 *
 * En orden aleatorio no repite ninguna foto hasta haber mostrado todas: [seen] guarda las que ya
 * salieron en esta ronda y se reinicia cuando se terminan. Devuelve las fotos elegidas y el nuevo
 * [seen].
 */
internal fun pickNext(
    ids: List<String>,
    current: List<String>,
    seen: Set<String>,
    count: Int,
    shuffle: Boolean,
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
        val next = pool.random()
        picked += next
        seenNow = seenNow + next
    }
    return picked to seenNow
}

/** Pasa el widget de una foto a la siguiente. */
suspend fun advancePhoto(context: Context, glanceId: GlanceId) {
    val shuffle = Settings(context).shuffle
    updateAppWidgetState(context, glanceId) { prefs ->
        val ids = PhotoPool.resolve(context, WidgetSource.from(prefs[WidgetKeys.SOURCE])).map { it.id }
        if (ids.isEmpty()) return@updateAppWidgetState
        val current = listOfNotNull(prefs[WidgetKeys.PHOTO_ID]?.takeIf { it in ids } ?: ids.first())
        val (next, seen) = pickNext(ids, current, prefs[WidgetKeys.SEEN].orEmpty(), 1, shuffle)
        prefs[WidgetKeys.PHOTO_ID] = next.first()
        prefs[WidgetKeys.SEEN] = seen
    }
    FotoWidget().update(context, glanceId)
}

/** Cambia todas las fotos de un collage. */
suspend fun advanceCollage(context: Context, glanceId: GlanceId) {
    val shuffle = Settings(context).shuffle
    updateAppWidgetState(context, glanceId) { prefs ->
        val pool = PhotoPool.resolve(context, WidgetSource.from(prefs[WidgetKeys.SOURCE]))
        if (pool.isEmpty()) return@updateAppWidgetState
        val count = prefs[WidgetKeys.COUNT] ?: CollageWidget.DEFAULT_COUNT
        val current = CollageWidget.current(pool, prefs[WidgetKeys.PHOTO_IDS], count).map { it.id }
        val (next, seen) = pickNext(pool.map { it.id }, current, prefs[WidgetKeys.SEEN].orEmpty(), count, shuffle)
        prefs[WidgetKeys.PHOTO_IDS] = next.joinToString(",")
        prefs[WidgetKeys.SEEN] = seen
    }
    CollageWidget().update(context, glanceId)
}

/** Se ejecuta al tocar un widget: avanza según de qué tipo sea. */
class NextPhotoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val manager = GlanceAppWidgetManager(context)
        if (glanceId in manager.getGlanceIds(CollageWidget::class.java)) {
            advanceCollage(context, glanceId)
        } else {
            advancePhoto(context, glanceId)
        }
    }
}

class RotateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        manager.getGlanceIds(FotoWidget::class.java).forEach { advancePhoto(applicationContext, it) }
        manager.getGlanceIds(CollageWidget::class.java).forEach { advanceCollage(applicationContext, it) }
        return Result.success()
    }
}

object RotationScheduler {
    private const val WORK_NAME = "rotate_photos"

    fun schedule(context: Context) {
        val minutes = Settings(context).intervalMinutes
        if (minutes <= 0) {
            cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<RotateWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Cancela la rotación si ya no queda ningún widget (de foto ni collage) en la pantalla. */
    fun cancelIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val remaining = listOf(FotoWidgetReceiver::class.java, CollageWidgetReceiver::class.java)
            .sumOf { manager.getAppWidgetIds(ComponentName(context, it)).size }
        if (remaining == 0) cancel(context)
    }
}
