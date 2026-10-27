package app.fototin.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fototin.R
import app.fototin.data.PhotoPool
import app.fototin.data.PhotoRepository
import app.fototin.data.Settings
import app.fototin.ui.MainActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Aviso a la mañana cuando hay fotos de un día como hoy en otros años, con la primera de esas
 * fotos en la notificación. Es opcional: se activa en Ajustes.
 */
object MemoriesNotifier {

    private const val WORK_NAME = "memories_notification"
    private const val CHANNEL_ID = "memories"
    private const val NOTIFICATION_ID = 1
    private const val HOUR = 9

    fun schedule(context: Context) {
        if (!Settings(context).memoriesNotification) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<MemoriesWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Desde Android 13 hay que pedir permiso para mostrar notificaciones. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Muestra el aviso si hoy hay recuerdos. Devuelve si lo mostró. */
    fun notifyIfAny(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val memories = PhotoPool.exactMemories(PhotoRepository(context).list())
        if (memories.isEmpty()) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recuerdos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Fotos de un día como hoy en otros años"
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val photo = memories.first()
        val years = Calendar.getInstance().get(Calendar.YEAR) -
            Calendar.getInstance().apply { timeInMillis = photo.takenAt }.get(Calendar.YEAR)
        val picture = PhotoRepository.decodeForSize(photo.file, 1024)
        val title = if (memories.size == 1) "Un recuerdo de hoy" else "${memories.size} recuerdos de hoy"
        val text = if (years == 1) "Hace 1 año" else "Hace $years años"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { picture?.let { setLargeIcon(it).setStyle(NotificationCompat.BigPictureStyle().bigPicture(it).bigLargeIcon(null as android.graphics.Bitmap?)) } }
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }
}

class MemoriesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Settings(applicationContext).memoriesNotification) MemoriesNotifier.notifyIfAny(applicationContext)
        return Result.success()
    }
}
