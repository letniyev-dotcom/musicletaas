package com.moyavolna.app.data

/** Domain model used across the UI and player. Self-contained & serializable. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val artworkSmall: String?,
    val artworkLarge: String?,
    val genre: String?,
) {
    companion object {
        /** Yandex cover URIs look like `avatars.yandex.net/...%%` — fill in a size. */
        fun coverUrl(coverUri: String?, size: String): String? {
            if (coverUri.isNullOrBlank()) return null
            val withSize = coverUri.replace("%%", size)
            return if (withSize.startsWith("http")) withSize else "https://$withSize"
        }

        fun from(t: YandexTrack): Track? {
            val id = t.id ?: return null
            val artists = t.artists?.mapNotNull { it.name }?.filter { it.isNotBlank() }.orEmpty()
            return Track(
                id = id,
                title = t.title?.takeIf { it.isNotBlank() } ?: "Без названия",
                artist = artists.joinToString(", ").ifBlank { "Неизвестный артист" },
                durationSec = ((t.durationMs ?: 0L) / 1000L).toInt(),
                artworkSmall = coverUrl(t.coverUri, "100x100"),
                artworkLarge = coverUrl(t.coverUri, "600x600"),
                genre = t.albums?.firstOrNull()?.genre,
            )
        }
    }
}
