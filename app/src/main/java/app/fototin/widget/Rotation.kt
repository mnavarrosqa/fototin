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
import app.fototin.data.collageSelection
import app.fototin.data.pickNext
import app.fototin.data.rotateOne
import java.util.Calendar
import java.util.concurrent.TimeUnit

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

/** Cambia las fotos de un collage: todas juntas o de a una, según cómo esté configurado. */
suspend fun advanceCollage(context: Context, glanceId: GlanceId) {
    val shuffle = Settings(context).shuffle
    updateAppWidgetState(context, glanceId) { prefs ->
        val pool = PhotoPool.resolve(context, WidgetSource.from(prefs[WidgetKeys.SOURCE]))
        if (pool.isEmpty()) return@updateAppWidgetState
        val style = CollageStyle.from(prefs[WidgetKeys.COLLAGE_STYLE])
        val count = style.clampCount(prefs[WidgetKeys.COUNT] ?: CollageWidget.DEFAULT_COUNT)
        val current = collageSelection(pool, prefs[WidgetKeys.PHOTO_IDS], count).map { it.id }
        val ids = pool.map { it.id }
        if (prefs[WidgetKeys.ROTATE_ONE] == true) {
            // De a una: el collage se va renovando de a poco.
            val (next, slot, seen) = rotateOne(ids, current, prefs[WidgetKeys.SLOT] ?: 0, prefs[WidgetKeys.SEEN].orEmpty(), shuffle)
            prefs[WidgetKeys.PHOTO_IDS] = next.joinToString(",")
            prefs[WidgetKeys.SLOT] = slot
            prefs[WidgetKeys.SEEN] = seen
        } else {
            val (next, seen) = pickNext(ids, current, prefs[WidgetKeys.SEEN].orEmpty(), count, shuffle)
            prefs[WidgetKeys.PHOTO_IDS] = next.joinToString(",")
            prefs[WidgetKeys.SEEN] = seen
        }
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

/**
 * Redibuja los widgets una vez por día, pasada la medianoche. Así los Recuerdos cambian de día
 * aunque la rotación automática esté apagada.
 */
class DailyRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetSupport.updateAll(applicationContext)
        return Result.success()
    }
}

object RotationScheduler {
    private const val WORK_NAME = "rotate_photos"
    private const val DAILY_WORK_NAME = "daily_refresh"

    fun schedule(context: Context) {
        scheduleDaily(context)
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

    private fun scheduleDaily(context: Context) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
        }
        val request = PeriodicWorkRequestBuilder<DailyRefreshWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        // KEEP: si ya está programado, no correr la hora cada vez que se abre la app.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Cancela la rotación si ya no queda ningún widget (de foto ni collage) en la pantalla. */
    fun cancelIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val remaining = listOf(FotoWidgetReceiver::class.java, CollageWidgetReceiver::class.java)
            .sumOf { manager.getAppWidgetIds(ComponentName(context, it)).size }
        if (remaining == 0) {
            cancel(context)
            WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK_NAME)
        }
    }
}
