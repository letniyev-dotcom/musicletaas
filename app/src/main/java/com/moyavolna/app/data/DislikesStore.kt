package com.moyavolna.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the IDs of tracks the user marked «Не нравится» (dislike), so they
 * никогда не появляются снова в «Моей волне». SharedPreferences + Gson, like
 * [FavoritesStore], but we only need the track ids here.
 */
class DislikesStore(context: Context) {

    private val prefs = context.getSharedPreferences("dislikes", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<Set<String>>() {}.type

    fun load(): Set<String> {
        val json = prefs.getString("ids", null) ?: return emptySet()
        return runCatching { gson.fromJson<Set<String>>(json, type) }.getOrNull().orEmpty()
    }

    private fun save(ids: Set<String>) {
        prefs.edit().putString("ids", gson.toJson(ids)).apply()
    }

    /** Mark a track as disliked. Returns the updated set. */
    fun add(id: String): Set<String> {
        val updated = load().toMutableSet().apply { add(id) }
        save(updated)
        return updated
    }

    /** Un-dislike (in case the user taps the button again). */
    fun remove(id: String): Set<String> {
        val updated = load().toMutableSet().apply { remove(id) }
        save(updated)
        return updated
    }

    fun isDisliked(id: String): Boolean = load().contains(id)
}
