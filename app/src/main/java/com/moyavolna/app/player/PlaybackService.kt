package com.moyavolna.app.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Visualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.math.sqrt

/**
 * Background playback host. Owns a single ExoPlayer + MediaSession so audio
 * keeps playing when the app is backgrounded and shows a media notification
 * with play/pause/skip controls. Streams signed Yandex get-mp3 URLs (which
 * require a User-Agent header).
 *
 * It also attaches a [Visualizer] to the player's audio session and publishes a
 * smoothed loudness value to [AudioLevel] so the home «волна» can pulse to the
 * rhythm of whatever is playing. The visualiser is best-effort: if RECORD_AUDIO
 * isn't granted (or the device refuses) it's silently skipped and the wave falls
 * back to its own gentle breathing.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var visualizer: Visualizer? = null
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Yandex-Music-API")
            .setAllowCrossProtocolRedirects(true)
        // Wrap the HTTP factory in a DefaultDataSource.Factory so the player can
        // open BOTH streamed https://get-mp3 URLs AND local file:// URIs (the
        // offline-cached любимые треки). Passing the raw HTTP factory made
        // downloaded favorites silently fail — DefaultHttpDataSource can't open
        // file:// — so «Мне нравится» didn't play. This routes file/content/asset
        // to local sources and http(s) upstream to the signed Yandex factory.
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        // Буферизуем заметно больше наперёд и стартуем воспроизведение раньше, чтобы
        // треки «не грузились долго»: начинаем играть уже после ~1.2с буфера, при этом
        // держим до 120с запаса вперёд → следующий трек/продолжение готовы заранее,
        // переключение без пауз и лагов.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 1_200,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Preload the start of the next track so переключение происходит мгновенно,
        // без лагов и подвисаний на стыке треков.
        runCatching {
            player.preloadConfiguration =
                ExoPlayer.PreloadConfiguration(20_000_000L) // ~20s ahead
        }

        // Pin a stable audio session id so we can attach a Visualizer to it.
        audioSessionId = try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.generateAudioSessionId()
        } catch (_: Throwable) {
            C.AUDIO_SESSION_ID_UNSET
        }
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            runCatching { player.setAudioSessionId(audioSessionId) }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    ensureVisualizer()
                } else {
                    AudioLevel.reset()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /** Lazily attach the Visualizer once playback starts (permission likely granted by then). */
    private fun ensureVisualizer() {
        if (visualizer != null) return
        val sid = if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) audioSessionId else 0
        try {
            val v = Visualizer(sid)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        vis: Visualizer?, waveform: ByteArray?, samplingRate: Int,
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        var sum = 0.0
                        for (b in waveform) {
                            val s = (b.toInt() and 0xFF) - 128
                            sum += (s * s).toDouble()
                        }
                        val rms = sqrt(sum / waveform.size) / 128.0
                        // Lift the response so quiet passages still move the blob.
                        AudioLevel.push((rms * 2.2).toFloat())
                    }

                    override fun onFftDataCapture(
                        vis: Visualizer?, fft: ByteArray?, samplingRate: Int,
                    ) {}
                },
                Visualizer.getMaxCaptureRate() / 2,
                /* waveform = */ true,
                /* fft = */ false,
            )
            v.enabled = true
            visualizer = v
        } catch (_: Throwable) {
            // RECORD_AUDIO not granted or device unsupported — wave self-breathes.
            visualizer = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        AudioLevel.reset()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
