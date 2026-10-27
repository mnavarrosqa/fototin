package app.fototin.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en GitHub. */
data class Release(
    val versionCode: Int,
    val versionName: String,
    /** Novedades, una por renglón. */
    val notes: List<String>,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Busca versiones nuevas en las releases de GitHub, las descarga y se las pasa al instalador de
 * Android. Como todas las versiones se firman con la misma clave, se instalan encima sin perder nada.
 */
class Updater(context: Context) {

    private val appContext = context.applicationContext

    val installedVersionCode: Int
        get() = appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()

    val installedVersionName: String
        get() = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()

    /** La última versión publicada, o null si no hay una más nueva que la instalada. */
    suspend fun checkForUpdate(): Release? = withContext(Dispatchers.IO) {
        val release = latestRelease()
        release.takeIf { it.versionCode > installedVersionCode }
    }

    private fun latestRelease(): Release {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val json = try {
            if (connection.responseCode !in 200..299) error("GitHub respondió ${connection.responseCode}")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        val tag = json.getString("tag_name")
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?: error("La versión $tag no tiene APK")
        return Release(
            versionCode = versionCodeOf(tag) ?: error("No se entiende la versión $tag"),
            versionName = tag.removePrefix("v"),
            notes = parseNotes(json.optString("body")),
            apkUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    /** Descarga el APK de [release] informando el avance de 0 a 1. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "fototin-${release.versionCode}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) error("No se pudo descargar (${connection.responseCode})")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    /** Si Android todavía no dejó que Fototin instale apps, hay que pedírselo al usuario. */
    fun canInstall(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    /**
     * Le pasa el APK al instalador de Android. Android muestra su confirmación y, al terminar,
     * cierra Fototin y la abre actualizada.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("fototin.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                Intent(appContext, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/mnavarrosqa/fototin/releases/latest"

        /** Las versiones se publican como "v0.2.<versionCode>". */
        fun versionCodeOf(tag: String): Int? = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull()

        /** Pasa las notas de la release (markdown simple) a renglones de texto. */
        fun parseNotes(body: String): List<String> =
            body.lines()
                .map { it.trim() }
                .filter { it.startsWith("- ") || it.startsWith("* ") }
                .map { it.drop(2).trim() }
                .filter { it.isNotEmpty() }
    }
}
