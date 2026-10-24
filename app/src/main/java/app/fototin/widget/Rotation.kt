package app.fototin.widget

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
 * Pasa el widget [glanceId] a la siguiente foto.
 *
 * En orden aleatorio no repite ninguna foto hasta haber mostrado todas: el widget recuerda cuáles
 * ya vio y empieza una ronda nueva cuando se terminan.
 */
suspend fun advancePhoto(context: Context, glanceId: GlanceId) {
    val shuffle = Settings(context).shuffle
    updateAppWidgetState(context, glanceId) { prefs ->
        val source = WidgetSource.from(prefs[FotoWidget.KEY_SOURCE])
        val ids = PhotoPool.resolve(context, source).map { it.id }
        if (ids.isEmpty()) return@updateAppWidgetState
        val current = prefs[FotoWidget.KEY_PHOTO_ID]?.takeIf { it in ids } ?: ids.first()
        prefs[FotoWidget.KEY_PHOTO_ID] = if (shuffle && ids.size > 1) {
            var seen = prefs[FotoWidget.KEY_SEEN].orEmpty().intersect(ids.toSet()) + current
            var pool = ids.filter { it !in seen }
            if (pool.isEmpty()) {
                seen = setOf(current)
                pool = ids - current
            }
            pool.random().also { prefs[FotoWidget.KEY_SEEN] = seen + it }
        } else {
            ids[(ids.indexOf(current) + 1) % ids.size]
        }
    }
    FotoWidget().update(context, glanceId)
}

/** Se ejecuta al tocar el widget. */
class NextPhotoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        advancePhoto(context, glanceId)
    }
}

class RotateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        GlanceAppWidgetManager(applicationContext)
            .getGlanceIds(FotoWidget::class.java)
            .forEach { advancePhoto(applicationContext, it) }
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
}
