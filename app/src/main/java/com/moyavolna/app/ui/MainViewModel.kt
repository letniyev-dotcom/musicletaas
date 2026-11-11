package com.moyavolna.app.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.moyavolna.app.R
import com.moyavolna.app.data.FavoritesStore
import com.moyavolna.app.data.MusicRepository
import com.moyavolna.app.data.Track
import com.moyavolna.app.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A glossy «камешек» mood that re-tunes the wave. */
data class Mood(
    val id: String,
    val label: String,
    val emojiRes: Int,
    val moodEnergy: String,
    val diversity: String,
    val c1: Long,
    val c2: Long,
    val c3: Long,
) {
    fun gradient(): List<Color> = listOf(Color(c1), Color(c2), Color(c3))
}

data class SearchState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
)

data class PlayerUi(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val isWave: Boolean = false,
    // Не-null ТОЛЬКО когда играет «волна по конкретному треку» (запущена с трека из поиска).
    // Для обычной Моей волны и волн по настроению — null, чтобы плашка «Волна по» не висела везде.
    val waveByTrack: Track? = null,
    // true когда играет «волна по коллекции» (запущена из «Мне нравится»).
    val waveByCollection: Boolean = false,
    val loadingWave: Boolean = false,
    // Full queue + current index drive the home «now-playing» carousel so the
    // соседние плашки (prev/next track) реально видны по бокам.
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository()
    private val favStore = FavoritesStore(app)
    private val dislikesStore = com.moyavolna.app.data.DislikesStore(app)
    private val lastTrackStore = com.moyavolna.app.data.LastTrackStore(app)
    private val offline = com.moyavolna.app.data.OfflineStore(app)

    /** Drives the volume cross-/in-/out-fades so nothing ever cuts in abruptly. */
    private var fadeJob: kotlinx.coroutines.Job? = null

    // Mirrors Yandex Music's «Настройки Моей волны»: «по характеру» (diversity)
    // and «под настроение» (moodEnergy). Each камешек maps to a real rotor
    // setting, so selecting one genuinely re-tunes the station.
    val moods: List<Mood> = listOf(
        Mood("default", "Моё\nлетаа", R.drawable.emoji_wave, "all", "default", 0xFFB084F5, 0xFF7C5CE0, 0xFF5232B0),
        Mood("favorite", "Любимое", R.drawable.emoji_love, "all", "favorite", 0xFFFF9AD1, 0xFFE865B0, 0xFFB53A86),
        Mood("unfamiliar", "Незнакомое", R.drawable.emoji_discover, "all", "discover", 0xFF8FE0FF, 0xFF4FB6E0, 0xFF2E7FB5),
        Mood("popular", "Популярное", R.drawable.emoji_popular, "all", "popular", 0xFFFFC36B, 0xFFF59A3B, 0xFFD2701F),
        Mood("active", "Бодрое", R.drawable.emoji_energy, "active", "default", 0xFFB7A6FF, 0xFF7C6BE0, 0xFF5841C2),
        Mood("fun", "Весёлое", R.drawable.emoji_party, "fun", "default", 0xFFFF9A6B, 0xFFE0563B, 0xFFB5331F),
        Mood("calm", "Спокойное", R.drawable.emoji_chill, "calm", "default", 0xFF6FE0D6, 0xFF2FB6C2, 0xFF1C8794),
        Mood("sad", "Грустное", R.drawable.emoji_sad, "sad", "default", 0xFF8FA8E0, 0xFF5C73C2, 0xFF3A4C8F),
        Mood("focus", "Фокус", R.drawable.emoji_focus, "calm", "favorite", 0xFF8FB4FF, 0xFF5B7BE0, 0xFF3A4FB5),
    )

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    // Позиция воспроизведения вынесена в ОТДЕЛЬНЫЙ flow: она тикает ~5 раз/сек,
    // и если бы она жила в PlayerUi, всё дерево UI перерисовывалось бы на каждый
    // тик → лаги при прокрутке/анимациях. Прогресс-бары собирают этот flow
    // локально (в листовых composable), не трогая остальной экран.
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    // Треки, помеченные «Не нравится» — больше не появляются в волне.
    private val _dislikes = MutableStateFlow<Set<String>>(emptySet())
    val dislikes: StateFlow<Set<String>> = _dislikes.asStateFlow()

    private val _activeMood = MutableStateFlow<String?>(null)
    val activeMood: StateFlow<String?> = _activeMood.asStateFlow()

    // Three glow colours for the «волна», derived from the current track artwork.
    private val defaultWave = listOf(Color(0xFFE879C9), Color(0xFFB084F5), Color(0xFF7B97FF))
    private val _wavePalette = MutableStateFlow(defaultWave)
    val wavePalette: StateFlow<List<Color>> = _wavePalette.asStateFlow()
    private val imageLoader = ImageLoader(app)
    private var paletteTrackId: String? = null

    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()
    private var stagedWave: List<Track> = emptyList()
    @Volatile private var loadingMore = false
    // Tracks which song we last reported to the rotor, so each new track sends
    // trackFinished/trackStarted — keeps «волну» personalised and moving forward.
    private var feedbackTrackId: String? = null
    // Last track we persisted, so we don't rewrite prefs on every player event.
    private var persistedTrackId: String? = null
    // Снимок прошлого сеанса, ожидающий продолжения по нажатию play (n8513).
    private var pendingRestore: com.moyavolna.app.data.PlaybackSnapshot? = null
    // Троттлинг сохранения снимка во время воспроизведения.
    private var lastSnapshotSaveMs = 0L

    init {
        _favorites.value = favStore.load()
        _dislikes.value = dislikesStore.load()
        repo.dislikedIds = _dislikes.value
        // Восстанавливаем ВЕСЬ прошлый сеанс (n8513): всю очередь, текущий индекс и
        // позицию — чтобы плашка/карусель показывали все миниатюры, а не одну, и
        // воспроизведение продолжалось с места остановки. Сам звук НЕ стартует —
        // ждём нажатия play (onPrimaryPlay подхватит pendingRestore).
        lastTrackStore.loadState()?.let { snap ->
            val idx = snap.index.coerceIn(0, snap.queue.size - 1)
            val restored = snap.copy(index = idx)
            pendingRestore = restored
            persistedTrackId = snap.queue[idx].id
            _position.value = snap.positionMs.coerceAtLeast(0L)
            _player.update {
                it.copy(
                    current = snap.queue[idx],
                    queue = snap.queue,
                    index = idx,
                    durationMs = 0L,
                    isWave = snap.isWave,
                    waveByCollection = snap.waveByCollection,
                )
            }
            updateWavePalette(snap.queue[idx])
        }
        connectController()
        viewModelScope.launch { stagedWave = repo.waveBatch(continueWave = false) }
        warmOfflineFavorites()
    }

    private fun connectController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            // Защита от утечки listener'а: если controller уже был — снимаем старый
            // listener перед заменой. Иначе при повторном connect (например, при
            // системном пересоздании сессии или race-condition с onCleared) старый
            // controller продолжал держать ссылку на playerListener → утечка ViewModel.
            controller?.removeListener(playerListener)
            controller = future.get()
            controller?.apply {
                addListener(playerListener)
                // Never loop the same tracks — the wave должна течь дальше.
                repeatMode = Player.REPEAT_MODE_OFF
                shuffleModeEnabled = false
            }
            refreshPlayerUi()
        }, ContextCompat.getMainExecutor(ctx))
        startPositionLoop()
    }

    // ---- smooth volume fades (плавная пауза / возобновление / переключение) ----
    private suspend fun fadeVolume(c: MediaController, from: Float, to: Float, durationMs: Long) {
        // Кадр ~каждые 11мс + smoothstep-кривая → мягкое, «масляное» затухание/появление.
        val steps = (durationMs / 11).toInt().coerceIn(10, 56)
        val stepDelay = (durationMs / steps).coerceAtLeast(6L)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)   // smoothstep: плавный старт и финиш
            c.volume = (from + (to - from) * eased).coerceIn(0f, 1f)
            delay(stepDelay)
        }
        c.volume = to.coerceIn(0f, 1f)
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) {
            refreshPlayerUi()
            maybeLoadMoreWave()
        }
    }

    private fun refreshPlayerUi() {
        val c = controller ?: return
        // Пока в плеер ничего не загружено (восстановленный сеанс ещё не запущен
        // нажатием play) — НЕ трогаем UI-состояние, иначе затрём восстановленную
        // очередь/текущий трек/позицию пустыми значениями (n8513).
        if (c.mediaItemCount == 0) return
        val idx = c.currentMediaItemIndex
        val track = queue.getOrNull(idx)
        // Report rotor feedback when the wave moves to a new track — без этого
        // станция плохо обновляется и начинает повторять одни и те же треки.
        if (track != null && track.id != feedbackTrackId) {
            val prev = feedbackTrackId
            feedbackTrackId = track.id
            if (_player.value.isWave) {
                viewModelScope.launch {
                    prev?.let { repo.feedback("trackFinished", it) }
                    repo.feedback("trackStarted", track.id)
                }
            }
        }
        // Смена трека → запоминаем и сохраняем полный снимок сеанса в конце метода
        // (после обновления _player с актуальными queue/index).
        val trackJustChanged = track != null && track.id != persistedTrackId
        if (trackJustChanged) persistedTrackId = track!!.id
        // При СМЕНЕ трека сбрасываем позицию и длительность в 0, чтобы прогресс не
        // оставался «залитым» от предыдущего трека (durationMs прошлого / position
        // прошлого давали frac≈1 в первые миллисекунды нового трека → плашка
        // выглядела сразу полностью заполненной).
        val prevTrackId = _player.value.current?.id
        val trackChanged = track != null && track.id != prevTrackId
        if (trackChanged) {
            _position.value = 0L
        }
        // Новая длительность доступна только после onTimelineChanged/onMediaItemTransition,
        // и c.duration в момент смены трека может быть ещё C.TIME_UNSET (-9223372036854775807)
        // → coerceAtLeast(0) даёт ОГРОМНОЕ число, а frac = 0/огромное = 0 (ок),
        // но если позиция уже была ненулевой — мы держим прошлое значение.
        val rawDur = c.duration
        val newDur = if (rawDur > 0L) rawDur else if (trackChanged) 0L else _player.value.durationMs
        _player.update {
            it.copy(
                current = track ?: it.current,
                // playWhenReady, а не isPlaying: при переключении трека плеер на миг
                // уходит в буферизацию (isPlaying=false) и кнопка дёргалась pause→play.
                // playWhenReady отражает НАМЕРЕНИЕ играть и стабилен между треками.
                isPlaying = c.playWhenReady,
                durationMs = newDur,
                queue = queue,
                index = idx.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
            )
        }
        updateWavePalette(track ?: _player.value.current)
        // Снимок при смене трека: очередь могла дорасти (подгрузка волны), индекс сменился.
        if (trackJustChanged) saveSnapshot()
    }

    /** Сохраняет полный снимок текущего сеанса (очередь + индекс + позиция) для n8513. */
    private fun saveSnapshot() {
        val st = _player.value
        if (st.queue.isEmpty()) return
        lastTrackStore.saveState(
            com.moyavolna.app.data.PlaybackSnapshot(
                queue = st.queue,
                index = st.index.coerceIn(0, st.queue.size - 1),
                positionMs = _position.value.coerceAtLeast(0L),
                isWave = st.isWave,
                waveByCollection = st.waveByCollection,
            )
        )
        lastSnapshotSaveMs = System.currentTimeMillis()
    }

    /** Recolour the «волна» from the playing track's cover art. */
    private fun updateWavePalette(track: Track?) {
        if (track == null) return
        val url = track.artworkLarge ?: track.artworkSmall ?: return
        if (track.id == paletteTrackId) return
        paletteTrackId = track.id
        // Warm Coil's memory cache with the full-size cover under a stable key, so
        // открытие FullPlayer берёт обложку из памяти мгновенно (без лага на декод).
        track.artworkLarge?.let { big ->
            runCatching {
                imageLoader.enqueue(
                    ImageRequest.Builder(getApplication())
                        .data(big)
                        .size(600)
                        .memoryCacheKey("art_${track.id}")
                        .build(),
                )
            }
        }
        viewModelScope.launch {
            extractPalette(url)?.let { _wavePalette.value = it }
        }
    }

    private suspend fun extractPalette(url: String): List<Color>? = withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(getApplication())
                .data(url).allowHardware(false).size(160).build()
            val result = imageLoader.execute(req)
            val bmp = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@withContext null
            val p = Palette.from(bmp).maximumColorCount(20).generate()
            val a = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: p.dominantSwatch?.rgb
            val b = p.lightVibrantSwatch?.rgb ?: p.lightMutedSwatch?.rgb ?: a
            val cc = p.darkVibrantSwatch?.rgb ?: p.mutedSwatch?.rgb ?: a
            if (a == null) return@withContext null
            listOf(Color(a), Color(b ?: a), Color(cc ?: a)).map { it.liven() }
        } catch (_: Throwable) {
            null
        }
    }

    /** Push muted artwork colours toward something that glows on pure black. */
    private fun Color.liven(): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(this.toArgb(), hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(0.55f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Guards against firing the end-of-track crossfade more than once per track.
    @Volatile private var autoAdvancing = false

    private fun startPositionLoop() {
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isPlaying) {
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    val rawDur = c.duration
                    val dur = if (rawDur > 0L) rawDur else 0L
                    // КРИТИЧНО: пишем position ТОЛЬКО когда durationMs уже известна.
                    // Без этого ExoPlayer сразу после seekToNextMediaItem мог вернуть
                    // currentPosition>0 при ещё-неопределённой duration → плашка на
                    // миг показывала frac=1.0 (полная заливка) пока durationMs не
                    // обновится. Теперь синхронизируем оба значения вместе.
                    if (dur > 0L) {
                        _position.value = pos
                        if (_player.value.durationMs != dur) {
                            _player.update { it.copy(durationMs = dur) }
                        }
                        // Периодически (раз в ~5с) сохраняем позицию, чтобы после
                        // перезахода продолжить с места остановки (n8513), не записывая
                        // prefs на каждый тик.
                        if (System.currentTimeMillis() - lastSnapshotSaveMs > 5000L) saveSnapshot()
                    }
                    // Pre-empt the natural end with a smooth crossfade: ExoPlayer's
                    // own auto-advance gives no fade (трек кончился → пауза → новый).
                    // A touch before the end we fade the current track out and the
                    // next one in, so одна плавно угасает, другая плавно возрастает —
                    // без паузы на стыке.
                    if (dur > 0 && c.hasNextMediaItem() && fadeJob?.isActive != true && !autoAdvancing) {
                        val remaining = dur - pos
                        if (remaining in 60..540) {
                            autoAdvancing = true
                            autoAdvance()
                            viewModelScope.launch { delay(1100); autoAdvancing = false }
                        }
                    }
                    delay(220)
                } else {
                    // PERF: когда плеер не играет, спим дольше (550мс вместо 220мс).
                    // Раньше цикл крутился постоянно, дёргая c.currentPosition / c.duration
                    // ~5 раз/сек даже на паузе → лишний расход CPU и батарея. Когда
                    // воспроизведение возобновится, следующая итерация быстро вернётся
                    // в нормальный ритм (через onIsPlayingChanged → onEvents → refreshPlayerUi).
                    delay(550)
                }
            }
        }
    }

    // ---- moods / wave ----
    fun selectMood(mood: Mood) {
        _activeMood.value = mood.id
        _player.update { it.copy(loadingWave = true) }
        viewModelScope.launch {
            val fresh = repo.applyMood(mood.moodEnergy, mood.diversity)
            if (fresh.isNotEmpty()) {
                stagedWave = fresh
                // Раньше: если играла «Волна по коллекции» / «Волна по треку», то
                // selectMood формально стартовал новую волну, но плашка оставалась
                // прежней («Волна по коллекции») И флаги isWave/waveByCollection не
                // сбрасывались → юзер думал «ничего не происходит». А если
                // refreshPlayerUi ещё не успел поставить isWave=true (короткая гонка
                // после playQueue) — настроение вообще не применялось к звуку.
                // Теперь: настроение ВСЕГДА переключает на свежую Мою волну с этим
                // настроением, и плашка «Волна по …» убирается явно.
                _player.update { it.copy(waveByTrack = null, waveByCollection = false) }
                startWave(fresh)
            }
            _player.update { it.copy(loadingWave = false) }
        }
    }

    /** Big play button: start wave if nothing is loaded, resume the restored track, else toggle. */
    fun onPrimaryPlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            // Ничего не загружено в плеер. Приоритет — восстановленный прошлый сеанс:
            // продолжаем ту же очередь с того же трека и позиции (n8513). Если снимка
            // нет — фолбэк на «волну по последнему треку» или «Мою волну».
            val restore = pendingRestore
            if (restore != null) {
                pendingRestore = null
                resumeSnapshot(restore)
            } else {
                val last = _player.value.current
                if (last != null) playSingle(last) else playWave()
            }
        } else {
            togglePlayPause()
        }
    }

    /**
     * Продолжает восстановленный сеанс (n8513): загружает сохранённую очередь,
     * встаёт на сохранённый индекс и сразу seek-ает на сохранённую позицию —
     * воспроизведение продолжается ровно с места остановки. Если ссылки на потоки
     * протухли — мягкий фолбэк на волну по последнему треку.
     */
    private fun resumeSnapshot(snap: com.moyavolna.app.data.PlaybackSnapshot) {
        val c = controller ?: return
        viewModelScope.launch {
            try {
                val resolved = resolveStreams(snap.queue)
                if (resolved.isEmpty()) {
                    val last = _player.value.current
                    if (last != null) playSingle(last) else playWave()
                    return@launch
                }
                val startId = snap.queue.getOrNull(snap.index)?.id
                val start = resolved.indexOfFirst { it.first.id == startId }.coerceAtLeast(0)
                queue = resolved.map { it.first }
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.setMediaItems(
                    resolved.map { toMediaItem(it.first, it.second) },
                    start,
                    snap.positionMs.coerceAtLeast(0L),   // продолжаем с места остановки
                )
                c.prepare()
                fadeJob?.cancel()
                c.volume = 0f
                c.play()
                _player.update {
                    it.copy(isWave = snap.isWave, waveByTrack = null, waveByCollection = snap.waveByCollection)
                }
                feedbackTrackId = queue.getOrNull(start)?.id   // без дубля trackStarted
                refreshPlayerUi()
                launch { fadeVolume(c, 0f, 1f, 300) }
            } catch (t: Throwable) {
                _player.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun playWave() {
        pendingRestore = null // явный старт новой волны отменяет ожидание восстановления
        repo.resetToMyWave() // большая кнопка/«Моя волна» всегда запускает именно Мою волну
        // Показываем спиннер «загрузка волны», но НЕ ставим isPlaying=true до того,
        // как реально запустили проигрывание — иначе кнопка показывала «пауза»,
        // хотя плеер ещё не играл (рассинхрон UI и состояния плеера).
        _player.update { it.copy(loadingWave = true) }
        viewModelScope.launch {
            val staged = stagedWave
            val batch = if (staged.isNotEmpty()) {
                stagedWave = emptyList()             // не переигрываем один и тот же первый батч
                staged
            } else {
                // продолжаем волну дальше (а не сначала), чтобы треки не повторялись
                repo.waveBatch(continueWave = true).ifEmpty { repo.waveBatch(continueWave = false) }
            }
            startWave(batch)
            _player.update { it.copy(loadingWave = false) }
        }
    }

    private suspend fun startWave(batch: List<Track>) {
        val c = controller ?: return
        try {
            val resolved = resolveStreams(batch)
            if (resolved.isEmpty()) { _player.update { it.copy(isPlaying = false) }; return }
            queue = resolved.map { it.first }
            c.repeatMode = Player.REPEAT_MODE_OFF
            c.setMediaItems(resolved.map { toMediaItem(it.first, it.second) }, 0, 0L)
            c.prepare()
            fadeJob?.cancel()
            c.volume = 0f
            c.play()
            // Моя волна / волна по настроению — НЕ волна по треку/коллекции.
            _player.update { it.copy(isWave = true, waveByTrack = null, waveByCollection = false) }
            feedbackTrackId = queue.firstOrNull()?.id   // avoid a duplicate trackStarted
            refreshPlayerUi()
            repo.feedback("radioStarted", null)
            queue.firstOrNull()?.let { repo.feedback("trackStarted", it.id) }
            fadeVolume(c, 0f, 1f, 320)
        } catch (t: Throwable) {
            _player.update { it.copy(isPlaying = false) }
        }
    }

    private fun maybeLoadMoreWave() {
        if (loadingMore || !_player.value.isWave) return
        val c = controller ?: return
        // Подгружаем продолжение волны раньше (за 3 трека до конца, а не за 2), чтобы
        // следующие треки всегда были готовы заранее и не возникало паузы на подгрузке.
        if (queue.isEmpty() || c.currentMediaItemIndex < queue.size - 3) return
        loadingMore = true
        viewModelScope.launch {
            // Дедуплицируем не только против уже-в-очереди треков, но и против ТЕКУЩЕГО
            // играющего: ротор Яндекса иногда возвращает тот же трек первым в новом
            // батче → пользователь слышал один и тот же трек подряд («треки
            // повторяются в моей волне»). Также фильтруем дизлайки на всякий случай.
            val existing = queue.map { it.id }.toMutableSet()
            _player.value.current?.id?.let { existing.add(it) }
            existing.addAll(_dislikes.value)
            val fresh = mutableListOf<Pair<Track, String>>()
            // Pull batches until we have genuinely NEW tracks — the rotor often
            // overlaps the current track into the next batch, which made the same
            // songs loop forever. We de-dupe so the wave always moves on.
            var attempts = 0
            while (fresh.size < 4 && attempts < 4) {
                val next = repo.waveBatch(continueWave = true).filter { it.id !in existing }
                if (next.isEmpty()) { attempts++; continue }
                val resolved = resolveStreams(next)
                resolved.forEach { if (existing.add(it.first.id)) fresh += it }
                attempts++
            }
            if (fresh.isNotEmpty()) {
                queue = queue + fresh.map { it.first }
                controller?.addMediaItems(fresh.map { toMediaItem(it.first, it.second) })
            }
            loadingMore = false
        }
    }

    /** Resolve a playable uri per track — offline cache first, then network. */
    private suspend fun resolveStreams(tracks: List<Track>): List<Pair<Track, String>> = coroutineScope {
        tracks.map { t ->
            async { (offline.localUri(t.id) ?: repo.resolveStreamUrl(t.id))?.let { t to it } }
        }.awaitAll().filterNotNull()
    }

    // ---- search ----
    private var searchJob: kotlinx.coroutines.Job? = null
    // Последний введённый текст храним в ОБЫЧНОМ поле, а НЕ в наблюдаемом StateFlow.
    // Раньше каждый символ писался в _search.query → SearchScreen (который собирает
    // этот flow) перерисовывался на КАЖДОЕ нажатие, и ввод дёргался/«залипал», а потом
    // символы вставлялись пачкой. Теперь текст живёт только локально в поле ввода, а
    // в наблюдаемое состояние попадают ТОЛЬКО результаты → печать плавная, без лагов.
    @Volatile private var pendingQuery: String = ""

    // Поиск в РЕАЛЬНОМ ВРЕМЕНИ: каждый ввод запускает поиск с дебаунсом 280мс
    // (предыдущий запрос отменяется). Спиннер показываем только пока нет ни одного
    // результата — иначе старые результаты остаются на экране, пока летят новые.
    fun onQueryChange(q: String) {
        pendingQuery = q
        searchJob?.cancel()
        val query = q.trim()
        if (query.isEmpty()) {
            _search.update { it.copy(results = emptyList(), searched = false, loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            _search.update { it.copy(loading = it.results.isEmpty(), searched = true) }
            // distinctBy(id): Яндекс может вернуть один и тот же трек дважды; без
            // дедупликации LazyColumn с key=id падал (duplicate key) → вылет.
            val res = repo.search(query).distinctBy { it.id }
            _search.update { it.copy(results = res, loading = false) }
        }
    }

    fun runSearch() {
        searchJob?.cancel()
        val q = pendingQuery.trim()
        if (q.isEmpty()) return
        _search.update { it.copy(loading = it.results.isEmpty(), searched = true) }
        searchJob = viewModelScope.launch {
            val res = repo.search(q).distinctBy { it.id }
            _search.update { it.copy(results = res, loading = false) }
        }
    }

    // ---- playback ----
    /**
     * Play a track chosen from search, then — как в Я.Музыке — продолжить «Волной по
     * этому треку»: после выбранного трека играет персональная волна, построенная по нему.
     */
    fun playSingle(track: Track, contextList: List<Track>? = null) {
        val c = controller ?: return
        pendingRestore = null // явный выбор трека отменяет ожидание восстановления
        // НЕ ставим isPlaying=true оптимистично: если резолв стрима упадёт или
        // прервётся, кнопка останется в «играет», хотя плеер на паузе → юзер
        // видит рассинхрон. Реальное состояние выставит refreshPlayerUi() после
        // c.play() через onEvents-листенер.
        viewModelScope.launch {
            // Всё воспроизведение обёрнуто в try/catch: если резолв стрима, IPC к
            // плееру или сетевой ответ кинут исключение — приложение НЕ падает
            // (раньше тап по треку в поиске мог вылетать). В худшем случае трек
            // просто не запустится, а не уронит весь экран.
            try {
                val url = offline.localUri(track.id) ?: repo.resolveStreamUrl(track.id)
                if (url == null) {
                    // Не удалось получить ссылку — откатываем кнопку в «пауза».
                    _player.update { it.copy(isPlaying = false) }
                    return@launch
                }
                queue = listOf(track)
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.setMediaItems(listOf(toMediaItem(track, url)), 0, 0L)
                c.prepare()
                fadeJob?.cancel()
                c.volume = 0f
                c.play()
                // Это уже волна (по треку) — дальше станция течёт сама и подгружается.
                _player.update { it.copy(isWave = true, waveByTrack = track, waveByCollection = false) }
                feedbackTrackId = track.id
                refreshPlayerUi()
                launch { fadeVolume(c, 0f, 1f, 300) }
                // Подтягиваем волну по треку и добавляем её ПОСЛЕ выбранного трека.
                val seeded = repo.seedWaveByTrack(track.id)
                    .filter { it.id != track.id && it.id !in _dislikes.value }
                val resolved = resolveStreams(seeded.take(10))
                if (resolved.isNotEmpty()) {
                    queue = queue + resolved.map { it.first }
                    c.addMediaItems(resolved.map { toMediaItem(it.first, it.second) })
                    refreshPlayerUi()
                }
            } catch (t: Throwable) {
                _player.update { it.copy(isPlaying = false) }
            }
        }
    }

    /**
     * «Волна по коллекции» (из «Мне нравится»): сначала играют твои лайкнутые треки,
     * а когда они подходят к концу — волна продолжается сама (персональная Моя волна,
     * собранная по твоим лайкам). Плашка «Волна по коллекции» висит с самого старта,
     * а не только когда треки закончились.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        pendingRestore = null // явный запуск плейлиста отменяет ожидание восстановления
        repo.resetToMyWave() // продолжение будет персональной Моей волной (по лайкам)
        // НЕ выставляем isPlaying оптимистично — пусть кнопка переключится после
        // реального c.play() (refreshPlayerUi через onEvents). Иначе при ошибке
        // резолва кнопка останется «играет», а плеер фактически на паузе.
        viewModelScope.launch {
            try {
                val resolved = resolveStreams(tracks.take(30))
                if (resolved.isEmpty()) { _player.update { it.copy(isPlaying = false) }; return@launch }
                val start = resolved.indexOfFirst { it.first.id == tracks.getOrNull(startIndex)?.id }
                    .coerceAtLeast(0)
                queue = resolved.map { it.first }
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.setMediaItems(resolved.map { toMediaItem(it.first, it.second) }, start, 0L)
                c.prepare()
                fadeJob?.cancel()
                c.volume = 0f
                c.play()
                // isWave=true → когда лайки заканчиваются, maybeLoadMoreWave продолжает волну.
                _player.update { it.copy(isWave = true, waveByTrack = null, waveByCollection = true) }
                feedbackTrackId = queue.getOrNull(start)?.id
                refreshPlayerUi()
                launch { fadeVolume(c, 0f, 1f, 300) }
            } catch (t: Throwable) {
                _player.update { it.copy(isPlaying = false) }
            }
        }
    }

    /** Крестик на плашке: сбрасываем «волну по треку/коллекции» обратно в Мою волну. */
    fun resetWave() {
        // Мгновенно убираем плашку — не ждём загрузки новой волны (без «ожидания»).
        _player.update { it.copy(waveByTrack = null, waveByCollection = false) }
        playWave()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        fadeJob?.cancel()
        val willPlay = !c.playWhenReady
        // Мгновенно переключаем кнопку — не ждём окончания фейда, поэтому без «медленности».
        _player.update { it.copy(isPlaying = willPlay) }
        // Фиксируем playWhenReady СРАЗУ, а не внутри корутины — иначе быстрые тапы
        // play→pause→play могли пройти в «неправильном» порядке (фейд предыдущего
        // тапа ещё идёт, кнопка уже показывает новое состояние, но playWhenReady
        // меняется только в конце фейда → реальное состояние плеера расходилось с
        // UI: «трек играет, хотя стоит на паузе или наоборот»).
        if (willPlay) {
            if (!c.playWhenReady) c.play()
        } else {
            if (c.playWhenReady) c.pause()
        }
        fadeJob = viewModelScope.launch {
            if (willPlay) {
                c.volume = 0f
                fadeVolume(c, 0f, 1f, 280)          // мягкое плавное появление (кнопка уже нажата)
            } else {
                fadeVolume(c, c.volume, 0f, 200)   // мягкое затухание (кнопка уже на паузе)
                c.volume = 1f                       // готов к следующему запуску
            }
        }
        // На паузе фиксируем позицию в снимке — частый момент закрытия приложения (n8513).
        if (!willPlay) saveSnapshot()
    }

    fun next() {
        if (_player.value.isWave) _player.value.current?.let { viewModelScope.launch { repo.feedback("skip", it.id) } }
        crossSwitch { it.seekToNextMediaItem() }
    }
    fun previous() = crossSwitch { it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    /**
     * Тап по треку в «Коллекции». Если коллекция уже играет и этот трек есть в уже
     * загруженной очереди — делаем МГНОВЕННЫЙ seek (crossSwitch), без пересборки
     * очереди и без сетевого резолва всех ссылок. Раньше каждый тап вызывал playQueue
     * → resolveStreams(до 30 треков) по сети → переключение «подвисало» и миниатюра
     * двигалась с лагами. Если же коллекция ещё не играет (или трек вне очереди) —
     * запускаем её как плейлист.
     */
    fun playCollectionTrack(track: Track, favorites: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount > 0 && _player.value.waveByCollection) {
            val qi = queue.indexOfFirst { it.id == track.id }
            if (qi >= 0) { selectQueueIndex(qi); return }
        }
        val start = favorites.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playQueue(favorites, start)
    }

    /** Jump to an arbitrary queue index — used by the home now-playing carousel. */
    fun selectQueueIndex(i: Int) {
        val c = controller ?: return
        if (i !in queue.indices || i == c.currentMediaItemIndex) return
        if (_player.value.isWave) _player.value.current?.let { t ->
            viewModelScope.launch { repo.feedback("skip", t.id) }
        }
        crossSwitch { it.seekTo(i, 0L) }
    }

    /**
     * Natural end-of-track advance with a longer fade-out so the outgoing track
     * audibly glides down before the next one fades in. No "skip" feedback —
     * refreshPlayerUi reports trackFinished/trackStarted to the rotor so the wave
     * keeps personalising itself.
     */
    private fun autoAdvance() = crossSwitch(outMs = 460) { it.seekToNextMediaItem() }

    /**
     * Smooth track change: fade the current track out, switch, then fade the new
     * one in — so одна затухает, другая плавно появляется, без резких склеек.
     */
    private fun crossSwitch(outMs: Long = 240, switch: (MediaController) -> Unit) {
        val c = controller ?: return
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch {
            val wasPlaying = c.playWhenReady
            if (wasPlaying) fadeVolume(c, c.volume, 0f, outMs)
            switch(c)
            c.volume = 0f
            if (!c.isPlaying) c.play()
            fadeVolume(c, 0f, 1f, 360)
        }
    }

    private fun toMediaItem(t: Track, url: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(t.title)
            .setArtist(t.artist)
            .apply { t.artworkLarge?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(url)
            .setMediaMetadata(meta)
            .build()
    }

    // ---- favorites (с офлайн-кэшем) ----
    /**
     * Лайк (сердечко). Лайк и дизлайк ВЗАИМОИСКЛЮЧАЮЩИЕ, как в Яндекс.Музыке:
     * если трек был дизлайкнут — снимаем дизлайк. Состояние меняется мгновенно
     * (локально), сервер синхронизируется в фоне — поэтому без лагов.
     */
    fun toggleFavorite(track: Track) {
        // Снимаем дизлайк, если он стоял (нельзя одновременно лайк и дизлайк).
        if (isDisliked(track.id)) {
            _dislikes.value = dislikesStore.remove(track.id)
            repo.dislikedIds = _dislikes.value
            viewModelScope.launch { repo.removeDislike(track.id) }
        }
        val updated = favStore.toggle(track)
        _favorites.value = updated
        val nowFavorite = updated.any { it.id == track.id }
        viewModelScope.launch {
            if (nowFavorite) {
                repo.likeTrack(track.id)
                // Скачиваем любимый трек, чтобы он играл офлайн без интернета.
                if (!offline.isDownloaded(track.id)) {
                    val url = repo.resolveStreamUrl(track.id)
                    if (url != null) offline.download(track.id, url)
                }
            } else {
                repo.unlikeTrack(track.id)
                offline.delete(track.id)
            }
        }
    }
    fun isFavorite(id: String): Boolean = _favorites.value.any { it.id == id }

    fun isDisliked(id: String): Boolean = _dislikes.value.contains(id)

    /**
     * «Не нравится» (перечёркнутое сердечко). Тоже toggle и взаимоисключение с лайком:
     * — если уже дизлайкнут → снимаем дизлайк (повторный тап).
     * — иначе → ставим дизлайк, снимаем лайк (если был), убираем трек из очереди и
     *   говорим Яндексу, чтобы волна его избегала. Больше не появится.
     */
    fun toggleDislike(track: Track) {
        if (isDisliked(track.id)) {
            _dislikes.value = dislikesStore.remove(track.id)
            repo.dislikedIds = _dislikes.value
            viewModelScope.launch { repo.removeDislike(track.id) }
            return
        }
        _dislikes.value = dislikesStore.add(track.id)
        repo.dislikedIds = _dislikes.value
        // дизлайкнутый трек не может оставаться лайкнутым / в «Коллекции»
        if (isFavorite(track.id)) {
            _favorites.value = favStore.toggle(track)
            viewModelScope.launch {
                offline.delete(track.id)
                repo.unlikeTrack(track.id)
            }
        }
        viewModelScope.launch { repo.dislikeTrack(track.id) }
        removeFromQueue(track.id)
    }

    /** Убирает трек из текущей очереди после дизлайка (ExoPlayer сам перейдёт к следующему). */
    private fun removeFromQueue(trackId: String) {
        val c = controller ?: return
        for (i in (c.mediaItemCount - 1) downTo 0) {
            if (c.getMediaItemAt(i).mediaId == trackId) c.removeMediaItem(i)
        }
        queue = queue.filterNot { it.id == trackId }
    }

    /** Pre-cache любые ещё не скачанные любимые треки (для офлайна). */
    private fun warmOfflineFavorites() {
        viewModelScope.launch {
            _favorites.value.forEach { t ->
                if (!offline.isDownloaded(t.id)) {
                    val url = repo.resolveStreamUrl(t.id)
                    if (url != null) offline.download(t.id, url)
                }
            }
        }
    }

    override fun onCleared() {
        // Перед закрытием фиксируем актуальную позицию и снимок сеанса (n8513).
        runCatching {
            controller?.let { c ->
                if (c.mediaItemCount > 0) _position.value = c.currentPosition.coerceAtLeast(0L)
            }
            saveSnapshot()
        }
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
