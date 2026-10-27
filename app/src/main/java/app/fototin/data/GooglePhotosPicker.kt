package app.fototin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * Cliente de la Google Photos Picker API: el usuario elige fotos en la pantalla de Google Fotos y la
 * app las descarga. Es la única forma que da Google para que una app lea fotos de Google Fotos.
 *
 * Flujo: [createSession] → abrir [Session.pickerUri] → [getSession] hasta que [Session.ready] →
 * [listItems] → [download] cada una → [deleteSession].
 */
class GooglePhotosPicker(private val token: String) {

    data class Session(
        val id: String,
        val pickerUri: String,
        val ready: Boolean,
        val pollIntervalMs: Long,
        val timeoutMs: Long,
    )

    data class Item(val id: String, val baseUrl: String, val createdAt: Long?, val isPhoto: Boolean)

    class PickerException(val code: Int, message: String) : IOException(message)

    suspend fun createSession(): Session = parseSession(request("POST", "$BASE/sessions", body = "{}"))

    suspend fun getSession(id: String): Session = parseSession(request("GET", "$BASE/sessions/$id"))

    suspend fun deleteSession(id: String) {
        runCatching { request("DELETE", "$BASE/sessions/$id") }
    }

    /** Todas las fotos elegidas en la sesión (los videos se ignoran). */
    suspend fun listItems(sessionId: String): List<Item> {
        val items = mutableListOf<Item>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$BASE/mediaItems?pageSize=100&sessionId=").append(URLEncoder.encode(sessionId, "UTF-8"))
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val json = JSONObject(request("GET", url))
            val array = json.optJSONArray("mediaItems")
            for (i in 0 until (array?.length() ?: 0)) {
                val o = array!!.getJSONObject(i)
                val file = o.optJSONObject("mediaFile") ?: continue
                items += Item(
                    id = o.getString("id"),
                    baseUrl = file.getString("baseUrl"),
                    createdAt = o.optString("createTime").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                    isPhoto = o.optString("type") != "VIDEO",
                )
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return items
    }

    /**
     * Descarga una foto en [dir]. Se pide ya achicada (la app guarda copias de hasta 1600 px), así
     * baja más rápido y gasta menos datos.
     */
    suspend fun download(item: Item, dir: File): File = withContext(Dispatchers.IO) {
        val target = File(dir, "gphotos_${item.id.hashCode().toUInt()}.jpg")
        val connection = open("GET", "${item.baseUrl}=w$DOWNLOAD_PX-h$DOWNLOAD_PX")
        try {
            if (connection.responseCode !in 200..299) throw PickerException(connection.responseCode, "No se pudo descargar una foto")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        target
    }

    private suspend fun request(method: String, url: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val connection = open(method, url)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw PickerException(code, message ?: "Google Fotos respondió con el error $code")
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun parseSession(text: String): Session {
        val json = JSONObject(text)
        val polling = json.optJSONObject("pollingConfig")
        return Session(
            id = json.getString("id"),
            pickerUri = json.getString("pickerUri"),
            ready = json.optBoolean("mediaItemsSet"),
            pollIntervalMs = durationMs(polling?.optString("pollInterval"), default = 3_000).coerceIn(1_000, 10_000),
            timeoutMs = durationMs(polling?.optString("timeoutIn"), default = 30 * 60_000),
        )
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
        private const val BASE = "https://photospicker.googleapis.com/v1"
        private const val DOWNLOAD_PX = 2048

        /** Las duraciones de la API vienen como "5s" o "1799.5s". */
        internal fun durationMs(value: String?, default: Long): Long =
            value?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: default
    }
}
