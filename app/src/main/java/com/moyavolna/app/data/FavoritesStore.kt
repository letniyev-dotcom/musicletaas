package com.moyavolna.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Persists liked tracks for the «Коллекция» tab via SharedPreferences + Gson. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<Track>>() {}.type

    fun load(): List<Track> {
        val json = prefs.getString("tracks", null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Track>>(json, type) }.getOrNull().orEmpty()
    }

    fun save(tracks: List<Track>) {
        prefs.edit().putString("tracks", gson.toJson(tracks)).apply()
    }

    fun toggle(track: Track): List<Track> {
        val current = load().toMutableList()
        val idx = current.indexOfFirst { it.id == track.id }
        if (idx >= 0) current.removeAt(idx) else current.add(0, track)
        save(current)
        return current
    }

    fun isFavorite(id: String): Boolean = load().any { it.id == id }
}
