package com.moyavolna.app.data

import android.content.Context
import com.google.gson.Gson

/**
 * Полный снимок состояния воспроизведения, чтобы после перезахода в приложение
 * восстанавливалась НЕ одна миниатюра, а вся очередь, текущий трек и позиция —
 * и запуск продолжал с того места, где пользователь остановился (n8513).
 */
data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    // true для «Моей волны» / волны по настроению / по треку / по коллекции — чтобы
    // при продолжении доигрывание вело себя так же (подгрузка волны, фидбэк ротору).
    val isWave: Boolean = false,
    val waveByCollection: Boolean = false,
)

/**
 * Хранит снимок последнего сеанса воспроизведения в SharedPreferences.
 * Раньше сохранялся только один трек → после перезапуска показывалась лишь одна
 * миниатюра, а кнопка запуска стартовала новую волну вместо продолжения.
 */
class LastTrackStore(context: Context) {

    private val prefs = context.getSharedPreferences("last_track", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Полный снимок состояния (очередь + индекс + позиция). */
    fun loadState(): PlaybackSnapshot? {
        val json = prefs.getString("state", null) ?: return null
        return runCatching { gson.fromJson(json, PlaybackSnapshot::class.java) }
            .getOrNull()
            ?.takeIf { it.queue.isNotEmpty() }
    }

    fun saveState(snapshot: PlaybackSnapshot) {
        if (snapshot.queue.isEmpty()) return
        prefs.edit().putString("state", gson.toJson(snapshot)).apply()
    }
}
