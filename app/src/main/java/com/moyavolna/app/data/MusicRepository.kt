package com.moyavolna.app.data

import com.moyavolna.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Talks to the unofficial Yandex Music API. Powers «Моя волна» (rotor station
 * `user:onyourwave`), search, and resolves signed MP3 stream URLs.
 *
 * The OAuth token is embedded via BuildConfig (set from local.properties).
 */
class MusicRepository {

    companion object {
        private const val BASE = "https://api.music.yandex.net/"
        const val WAVE_STATION = "user:onyourwave"
        private const val CLIENT = "YandexMusicAndroid/24023621"
        private const val UA = "Yandex-Music-API"
        private const val SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA"
    }

    private val token = BuildConfig.YANDEX_TOKEN

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", "OAuth $token")
                .header("X-Yandex-Music-Client", CLIENT)
                .header("User-Agent", UA)
                .build()
            chain.proceed(req)
        }
        .build()

    private val api: YandexApi = Retrofit.Builder()
        .baseUrl(BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YandexApi::class.java)

    val hasToken: Boolean get() = token.isNotBlank()

    /** The id we pass back as `queue` to fetch the next wave batch. */
    @Volatile var lastBatchId: String? = null
        private set
    @Volatile private var lastTrackId: String? = null

    /**
     * The active rotor station. Default — «Моя волна» (`user:onyourwave`); after
     * playing a track from search мы переключаемся на `track:<id>` — «Волна по треку»,
     * чтобы дальше играла волна по этому треку, как в Я.Музыке.
     */
    @Volatile var station: String = WAVE_STATION
        private set

    /** Disliked track ids (set by the ViewModel) — отфильтровываются из волны. */
    @Volatile var dislikedIds: Set<String> = emptySet()

    /** Cached Yandex account uid (нужен для dislikes-эндпоинта). */
    @Volatile private var cachedUid: Long? = null

    /** Fetch a batch of tracks from the active [station]. [continueWave] pages on from the last track. */
    // ВСЕ сетевые suspend-функции репозитория выполняются на Dispatchers.IO и через
    // withContext возвращают корутину на её исходный диспетчер (для viewModelScope —
    // Main). Это КРИТИЧНО: раньше Retrofit-suspend возобновлялся на фоновом потоке
    // OkHttp, и следующие за ним вызовы MediaController (setMediaItems/prepare/play)
    // шли НЕ с главного потока → IllegalStateException «wrong thread» → вылет при
    // нажатии на трек (для не-скачанных треков, т.к. скачанные не делают сетевой
    // вызов и оставались на Main). Теперь после любого repo-вызова мы снова на Main.
    suspend fun waveBatch(continueWave: Boolean): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val queue = if (continueWave) lastTrackId else null
            val result = api.stationTracks(station, true, queue).result
            lastBatchId = result?.batchId
            val tracks = result?.sequence.orEmpty()
                .mapNotNull { it.track?.let(Track::from) }
                .filter { it.id.isNotBlank() && it.id !in dislikedIds } // никогда не возвращаем дизлайкнутые
            tracks.lastOrNull()?.let { lastTrackId = it.id }
            tracks
        }.getOrDefault(emptyList())
    }

    /** Switch back to the default «Моя волна» station. */
    fun resetToMyWave() {
        station = WAVE_STATION
        lastTrackId = null
    }

    /**
     * Seed «Моя волна» by a specific track («Волна по треку» as in Я.Музыке) and
     * return its first batch. The seed track itself is dropped (it's already playing).
     */
    suspend fun seedWaveByTrack(trackId: String): List<Track> {
        station = "track:$trackId"
        lastTrackId = trackId
        return waveBatch(continueWave = false).filter { it.id != trackId }
    }

    private suspend fun accountUid(): Long? {
        cachedUid?.let { return it }
        return runCatching { api.accountStatus().result?.account?.uid }.getOrNull()
            ?.also { cachedUid = it }
    }

    /**
     * Mark a track «Не нравится»: tell Yandex (so the rotor avoids it server-side)
     * and send rotor feedback so текущая волна сразу перестаёт его предлагать.
     */
    suspend fun dislikeTrack(trackId: String) {
        runCatching {
            accountUid()?.let { uid -> api.addDislikes(uid, trackId) }
        }
        feedback("dislike", trackId)
    }

    /** Снять «Не нравится» на сервере. */
    suspend fun removeDislike(trackId: String) {
        runCatching { accountUid()?.let { uid -> api.removeDislikes(uid, trackId) } }
    }

    /** Лайкнуть трек на сервере («Мне нравится»). */
    suspend fun likeTrack(trackId: String) {
        runCatching { accountUid()?.let { uid -> api.addLikes(uid, trackId) } }
    }

    /** Снять лайк на сервере. */
    suspend fun unlikeTrack(trackId: String) {
        runCatching { accountUid()?.let { uid -> api.removeLikes(uid, trackId) } }
    }

    /** Change the wave mood (best-effort) then return a fresh batch. */
    suspend fun applyMood(moodEnergy: String, diversity: String, language: String = "any"): List<Track> =
        withContext(Dispatchers.IO) {
            resetToMyWave() // настроение всегда настраивает «Мою волну», а не «волну по треку»
            runCatching {
                // settings3 expects a JSON body — the old form-urlencoded body returned
                // 400, so выбор настроения раньше молча НЕ применялся (волна не менялась).
                val json = """{"moodEnergy":"$moodEnergy","diversity":"$diversity","language":"$language"}"""
                    .toRequestBody("application/json".toMediaType())
                api.stationSettings(WAVE_STATION, json)
            }
            lastTrackId = null
            waveBatch(continueWave = false)
        }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            api.search(query).result?.tracks?.results.orEmpty().mapNotNull(Track::from)
        }.getOrDefault(emptyList())
    }

    /** Resolve a directly-playable, signed MP3 URL for a track. */
    suspend fun resolveStreamUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val items = api.downloadInfo(trackId).result.orEmpty()
            val best = items.filter { it.codec == "mp3" && it.preview != true }
                .maxByOrNull { it.bitrateInKbps ?: 0 }
                ?: items.firstOrNull() ?: return@runCatching null
            val infoUrl = best.downloadInfoUrl ?: return@runCatching null
            val xml = api.downloadInfoXml(infoUrl).string()
            val host = xmlTag(xml, "host") ?: return@runCatching null
            val path = xmlTag(xml, "path") ?: return@runCatching null
            val ts = xmlTag(xml, "ts") ?: return@runCatching null
            val s = xmlTag(xml, "s") ?: return@runCatching null
            val sign = md5(SIGN_SALT + path.substring(1) + s)
            "https://$host/get-mp3/$sign/$ts$path"
        }.getOrNull()
    }

    /** Fire-and-forget rotor feedback so the wave stays personalised. */
    suspend fun feedback(type: String, trackId: String?, totalPlayedSec: Double? = null) {
        withContext(Dispatchers.IO) {
            runCatching {
                val ts = System.currentTimeMillis() / 1000.0
                val sb = StringBuilder("{\"type\":\"$type\",\"timestamp\":$ts")
                trackId?.let { sb.append(",\"trackId\":\"$it\"") }
                totalPlayedSec?.let { sb.append(",\"totalPlayedSeconds\":$it") }
                sb.append("}")
                val body: RequestBody = sb.toString().toRequestBody("application/json".toMediaType())
                api.stationFeedback(station, lastBatchId, body)
            }
        }
    }

    private fun xmlTag(xml: String, tag: String): String? {
        val open = "<$tag>"; val close = "</$tag>"
        val i = xml.indexOf(open); if (i < 0) return null
        val j = xml.indexOf(close, i + open.length); if (j < 0) return null
        return xml.substring(i + open.length, j).trim()
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
}
