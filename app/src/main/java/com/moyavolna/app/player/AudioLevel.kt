package com.moyavolna.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared, process-wide audio loudness signal (0f..1f). The [PlaybackService]
 * writes a smoothed RMS amplitude from a [android.media.audiofx.Visualizer]
 * attached to the ExoPlayer; the «волна» on the home screen reads it to pulse
 * in time with the music. Same-process singleton (service + UI share it).
 */
object AudioLevel {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    /** Exponential smoothing so the blob breathes, not jitters. */
    fun push(raw: Float) {
        val v = raw.coerceIn(0f, 1f)
        val prev = _level.value
        _level.value = prev + (v - prev) * 0.35f
    }

    fun reset() { _level.value = 0f }
}
