package com.moyavolna.app.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Unofficial Yandex Music API (api.music.yandex.net). Auth + client headers are
 * added by an OkHttp interceptor in [MusicRepository]. "Моя волна" is the rotor
 * station `user:onyourwave`.
 */
interface YandexApi {

    @GET("rotor/station/{station}/tracks")
    suspend fun stationTracks(
        @Path("station") station: String,
        @Query("settings2") settings2: Boolean = true,
        @Query("queue") queue: String? = null,
    ): YResult<RotorTracksResult>

    @GET("tracks/{id}/download-info")
    suspend fun downloadInfo(@Path("id") id: String): YResult<List<DownloadInfoItem>>

    /** The downloadInfoUrl returns XML (host/path/ts/s). */
    @GET
    suspend fun downloadInfoXml(@Url url: String): ResponseBody

    @GET("search")
    suspend fun search(
        @Query("text") text: String,
        @Query("type") type: String = "track",
        @Query("page") page: Int = 0,
        @Query("nocorrect") noCorrect: Boolean = false,
    ): YResult<YandexSearchResult>

    @POST("rotor/station/{station}/settings3")
    suspend fun stationSettings(
        @Path("station") station: String,
        @Body body: RequestBody,
    ): ResponseBody

    @POST("rotor/station/{station}/feedback")
    suspend fun stationFeedback(
        @Path("station") station: String,
        @Query("batch-id") batchId: String?,
        @Body body: RequestBody,
    ): ResponseBody

    @GET("account/status")
    suspend fun accountStatus(): YResult<AccountStatus>

    /** Mark tracks as «Не нравится» — they stop showing up in «Моей волне». */
    @FormUrlEncoded
    @POST("users/{uid}/dislikes/tracks/add-multiple")
    suspend fun addDislikes(
        @Path("uid") uid: Long,
        @Field("track-ids") trackIds: String,
    ): ResponseBody

    /** Снять «Не нравится» (лайк/дизлайк взаимоисключающие). */
    @FormUrlEncoded
    @POST("users/{uid}/dislikes/tracks/remove")
    suspend fun removeDislikes(
        @Path("uid") uid: Long,
        @Field("track-ids") trackIds: String,
    ): ResponseBody

    /** Лайк трека — попадает в «Мне нравится» и персонализирует волну. */
    @FormUrlEncoded
    @POST("users/{uid}/likes/tracks/add-multiple")
    suspend fun addLikes(
        @Path("uid") uid: Long,
        @Field("track-ids") trackIds: String,
    ): ResponseBody

    /** Снять лайк. */
    @FormUrlEncoded
    @POST("users/{uid}/likes/tracks/remove")
    suspend fun removeLikes(
        @Path("uid") uid: Long,
        @Field("track-ids") trackIds: String,
    ): ResponseBody
}
