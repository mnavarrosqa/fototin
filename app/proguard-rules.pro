# Glance instancia las acciones del widget por reflexión a partir del nombre de la clase.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# WorkManager crea los workers por reflexión.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
