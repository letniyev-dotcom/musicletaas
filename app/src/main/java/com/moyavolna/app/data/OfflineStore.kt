package com.moyavolna.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Caches the MP3 of liked tracks on local storage so «любимые» песни can be
 * played fully offline (без интернета). Files live in filesDir/offline/{id}.mp3.
 *
 * The download is a plain GET of the already-signed Yandex get-mp3 URL (the same
 * URL the player streams), written atomically via a .part temp file.
 */
class OfflineStore(context: Context) {

    private val dir = File(context.filesDir, "offline").apply { mkdirs() }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun fileFor(id: String) = File(dir, "$id.mp3")

    fun isDownloaded(id: String): Boolean = fileFor(id).let { it.exists() && it.length() > 0 }

    /** A `file://` uri for the cached track, or null if it isn't downloaded yet. */
    fun localUri(id: String): String? =
        if (isDownloaded(id)) Uri.fromFile(fileFor(id)).toString() else null

    /** Download [url] into the offline cache for [id]. Returns true on success. */
    suspend fun download(id: String, url: String): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(id)) return@withContext true
        val target = fileFor(id)
        val part = File(dir, "$id.part")
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Yandex-Music-API")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                part.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            }
            if (part.length() > 0) {
                // renameTo может вернуть false на некоторых ФС (например когда target
                // уже существует или при разных mount points). В этом случае
                // фоллбэк на копирование+удаление через File.copyTo, чтобы файл
                // ГАРАНТИРОВАННО оказался на месте — иначе мы возвращали true,
                // но трек в офлайне не появлялся.
                if (target.exists()) target.delete()
                val renamed = part.renameTo(target)
                if (!renamed) {
                    runCatching { part.copyTo(target, overwrite = true) }
                    part.delete()
                }
                target.exists() && target.length() > 0
            } else {
                part.delete(); false
            }
        }.getOrElse {
            part.delete(); false
        }
    }

    fun delete(id: String) {
        runCatching { fileFor(id).delete() }
        runCatching { File(dir, "$id.part").delete() }
    }
}
