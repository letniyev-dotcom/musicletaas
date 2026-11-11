package com.moyavolna.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyavolna.app.ui.components.MiniPlayer
import com.moyavolna.app.ui.components.NavBar
import com.moyavolna.app.ui.components.Tab
import com.moyavolna.app.ui.screens.CollectionScreen
import com.moyavolna.app.ui.screens.FullPlayer
import com.moyavolna.app.ui.screens.HomeScreen
import com.moyavolna.app.ui.screens.SearchScreen
import com.moyavolna.app.ui.theme.MoyaVolnaTheme

@Composable
fun MoyaVolnaRoot(vm: MainViewModel = viewModel()) {
    MoyaVolnaTheme {
        // ВНИМАНИЕ: search НЕ собираем здесь — иначе каждый символ в поиске
        // перерисовывал бы весь экран (лаг ввода). SearchScreen собирает flow сам.
        val playerUi by vm.player.collectAsState()
        val favorites by vm.favorites.collectAsState()
        val dislikes by vm.dislikes.collectAsState()
        val activeMood by vm.activeMood.collectAsState()
        val waveColors by vm.wavePalette.collectAsState()

        var tab by remember { mutableStateOf(Tab.Home) }
        var fullOpen by remember { mutableStateOf(false) }

        val current = playerUi.current

        // ЕДИНЫЙ мини-плеер (n4041): один экземпляр для экранов «Поиск» и «Коллекция».
        // Рендерится на уровне корня (ниже), а НЕ внутри каждого экрана — так его
        // pagerState и анимация свайпа миниатюры сохраняются при переключении между
        // этими табами, а не пересоздаются заново.
        val miniPlayerSlot: @Composable () -> Unit = {
            if (current != null && !fullOpen) {
                MiniPlayer(
                    track = current,
                    queue = playerUi.queue,
                    currentIndex = playerUi.index,
                    isPlaying = playerUi.isPlaying,
                    position = vm.position,
                    durationMs = playerUi.durationMs,
                    accentColor = waveColors.firstOrNull(),
                    onPlayPause = { vm.togglePlayPause() },
                    onExpand = { fullOpen = true },
                    onSelectTrack = { vm.selectQueueIndex(it) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // ---- main content ----
            when (tab) {
                Tab.Home -> HomeScreen(
                    moods = vm.moods,
                    activeMood = activeMood,
                    queue = playerUi.queue,
                    currentIndex = playerUi.index,
                    current = current,
                    waveByTrack = playerUi.waveByTrack,
                    waveByCollection = playerUi.waveByCollection,
                    isPlaying = playerUi.isPlaying,
                    isFavorite = { t -> favorites.any { it.id == t.id } },
                    waveColors = waveColors,
                    onPlayPauseOrStart = { vm.onPrimaryPlay() },
                    onNext = { vm.next() },
                    onPrev = { vm.previous() },
                    onMood = { vm.selectMood(it) },
                    onFavorite = { c -> vm.toggleFavorite(c) },
                    onSelectTrack = { vm.selectQueueIndex(it) },
                    onOpenFull = { fullOpen = true },
                    onResetWave = { vm.resetWave() },
                )
                Tab.Search -> SearchScreen(
                    searchFlow = vm.search,
                    currentId = current?.id,
                    isPlaying = playerUi.isPlaying,
                    // Read the COLLECTED favorites state so liking a track in search
                    // recomposes the row (раньше лайк не обновлялся — только дизлайк).
                    isFavorite = { id -> favorites.any { it.id == id } },
                    isDisliked = { dislikes.contains(it) },
                    onQueryChange = vm::onQueryChange,
                    onSubmit = { vm.runSearch() },
                    onTrackClick = { t, list -> vm.playSingle(t, list) },
                    onToggleFavorite = { vm.toggleFavorite(it) },
                    onDislike = { vm.toggleDislike(it) },
                )
                Tab.Collection -> CollectionScreen(
                    favorites = favorites,
                    currentId = current?.id,
                    isPlaying = playerUi.isPlaying,
                    // Из коллекции трек играет как ПЛЕЙЛИСТ из лайков (не «волна по треку»).
                    // playCollectionTrack: если коллекция уже играет — мгновенный seek
                    // вместо тяжёлой пересборки очереди (убирает лаг переключения, n-lag).
                    onTrackClick = { t, list -> vm.playCollectionTrack(t, list) },
                    onToggleFavorite = { vm.toggleFavorite(it) },
                    onPlayAll = { if (favorites.isNotEmpty()) vm.playQueue(favorites, 0) },
                )
            }

            // ---- ЕДИНЫЙ мини-плеер для экранов «Поиск» и «Коллекция» (n4041) ----
            // Раньше плашка жила ВНУТРИ каждого экрана через bottomOverlay-слот, поэтому
            // при переключении таба поиск↔коллекция её subtree (и pagerState) пересоздавался
            // заново → анимация свайпа миниатюры обрывалась, а сама миниатюра «была разной».
            // Теперь это ОДИН экземпляр, отрендеренный на уровне корня. Пока активен любой из
            // этих двух табов, call-site остаётся в композиции → состояние pager-а (и свайп)
            // сохраняется при переходе между ними.
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)) {
                if (tab == Tab.Search || tab == Tab.Collection) {
                    miniPlayerSlot()
                }
            }

            // ---- navbar ----
            NavBar(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // ---- full player overlay ----
            AnimatedVisibility(
                visible = fullOpen && current != null,
                // Только slide, без fade: альфа-композитинг всего экрана на каждый
                // кадр был тяжёлым и плеер «лагал» при открытии. Чистый slide +
                // FastOutSlowIn = плавно и дёшево.
                enter = slideInVertically(
                    animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    initialOffsetY = { it },
                ),
                exit = slideOutVertically(
                    animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    targetOffsetY = { it },
                ),
            ) {
                current?.let { c ->
                    FullPlayer(
                        track = c,
                        isPlaying = playerUi.isPlaying,
                        position = vm.position,
                        durationMs = playerUi.durationMs,
                        // Читаем СОБРАННОЕ состояние, чтобы лайк/дизлайк перерисовывались
                        // мгновенно в полном плеере (иначе сердечко «лагало»).
                        isFavorite = favorites.any { it.id == c.id },
                        isDisliked = dislikes.contains(c.id),
                        onCollapse = { fullOpen = false },
                        onPlayPause = { vm.togglePlayPause() },
                        onNext = { vm.next() },
                        onPrev = { vm.previous() },
                        onSeek = { vm.seekTo(it) },
                        onToggleFavorite = { vm.toggleFavorite(c) },
                        onToggleDislike = { vm.toggleDislike(c) },
                    )
                }
            }
        }
    }
}
