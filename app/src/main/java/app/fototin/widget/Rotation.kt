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
import app.fototin.data.PhotoRepository
import app.fototin.data.Settings
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Pasa el widget [glanceId] a la siguiente foto (o a una al azar). */
suspend fun advancePhoto(context: Context, glanceId: GlanceId) {
    val count = PhotoRepository(context).list().size
    if (count == 0) return
    val shuffle = Settings(context).shuffle
    updateAppWidgetState(context, glanceId) { prefs ->
        val current = (prefs[FotoWidget.KEY_INDEX] ?: 0).mod(count)
        prefs[FotoWidget.KEY_INDEX] = when {
            count == 1 -> 0
            // Elegir entre las demás fotos para que nunca repita la actual.
            shuffle -> (current + 1 + Random.nextInt(count - 1)) % count
            else -> (current + 1) % count
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
