package com.moyavolna.app.data

import com.google.gson.annotations.SerializedName

/** Generic Yandex envelope: { "result": ... }. */
data class YResult<T>(@SerializedName("result") val result: T?)

// ---- track ----
data class YandexArtist(@SerializedName("name") val name: String?)

data class YandexAlbum(
    @SerializedName("id") val id: Long?,
    @SerializedName("genre") val genre: String?,
)

data class YandexTrack(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("durationMs") val durationMs: Long?,
    @SerializedName("coverUri") val coverUri: String?,
    @SerializedName("available") val available: Boolean?,
    @SerializedName("artists") val artists: List<YandexArtist>?,
    @SerializedName("albums") val albums: List<YandexAlbum>?,
)

// ---- rotor (Моя волна) ----
data class RotorSequenceItem(
    @SerializedName("type") val type: String?,
    @SerializedName("track") val track: YandexTrack?,
    @SerializedName("liked") val liked: Boolean?,
)

data class RotorTracksResult(
    @SerializedName("id") val id: Any?,
    @SerializedName("sequence") val sequence: List<RotorSequenceItem>?,
    @SerializedName("batchId") val batchId: String?,
)

// ---- download-info ----
data class DownloadInfoItem(
    @SerializedName("codec") val codec: String?,
    @SerializedName("bitrateInKbps") val bitrateInKbps: Int?,
    @SerializedName("preview") val preview: Boolean?,
    @SerializedName("downloadInfoUrl") val downloadInfoUrl: String?,
)

// ---- search ----
data class YandexSearchTracks(@SerializedName("results") val results: List<YandexTrack>?)
data class YandexSearchResult(@SerializedName("tracks") val tracks: YandexSearchTracks?)

// account/status → result.account.uid (нужен для likes/dislikes эндпоинтов).
data class AccountInfo(@SerializedName("uid") val uid: Long?)
data class AccountStatus(@SerializedName("account") val account: AccountInfo?)
