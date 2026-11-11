package com.moyavolna.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyavolna.app.R
import com.moyavolna.app.data.Track
import com.moyavolna.app.ui.Mood
import com.moyavolna.app.ui.components.ElasticOverscroll
import com.moyavolna.app.ui.theme.TextPrimary

@Composable
fun HomeScreen(
    moods: List<Mood>,
    activeMood: String?,
    queue: List<Track>,
    currentIndex: Int,
    current: Track?,
    waveByTrack: Track?,
    waveByCollection: Boolean,
    isPlaying: Boolean,
    isFavorite: (Track) -> Boolean,
    waveColors: List<Color>,
    onPlayPauseOrStart: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onMood: (Mood) -> Unit,
    onFavorite: (Track) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onOpenFull: () -> Unit,
    onResetWave: () -> Unit,
) {
    // The now-playing плашка показывается, пока есть текущий трек — даже если очередь
    // ещё не загрузилась (восстановленный последний трек). Так она больше не пропадает.
    val pillList = if (queue.isNotEmpty()) queue else listOfNotNull(current)
    val pillIndex = if (queue.isNotEmpty()) currentIndex.coerceIn(0, queue.size - 1) else 0
    // Accent colour pulled from the playing track's artwork — волны и сердечко
    // подстраиваются под цвет трека. Раньше цвет ПРЫГАЛ мгновенно при смене трека
    // (резкий скачок, «не плавно»), пока сама «волна» переливалась плавно. Теперь
    // акцент элементов плавно перетекает тем же кроссфейдом, что и блоб → цвет
    // подстраивается мягко, в такт волне, без рывков.
    val rawAccent = waveColors.firstOrNull() ?: Color(0xFFB084F5)
    val trackAccent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(900),
        label = "trackAccent",
    )
    // Title mirrors the active wave: «моё летаа» by default, the mood otherwise.
    val title = moods.firstOrNull { it.id == activeMood }?.label ?: "моё\nлетаа"

    // ВОЛНА живёт ПОЗАДИ всего контента как полноэкранный фон, а сам контент
    // (приветствие → контролы → камешки) лежит сверху прозрачной колонкой. Раньше
    // блоб был зажат в отдельный weight-контейнер, и его свечение упиралось в края
    // этого прямоугольника → было видно «обрезание» сверху/снизу. Теперь свечение на
    // весь экран и плавно гаснет в чёрный к краям — без рамок и обрезки.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
      com.moyavolna.app.ui.components.WaveBlob(
          isPlaying = isPlaying,
          colors = waveColors,
          modifier = Modifier.fillMaxSize(),
      )

      Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(54.dp))

        // greeting + avatar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Добрый день, Александр",
                color = Color(0xFFC9C2DC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFB084F5), Color(0xFF6E54C9)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("А", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }

        // ===== контролы + now-pill по центру свободного места (волна — фоном сзади) =====
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // FIXED-height title slot sized for two lines, content centred —
                // so switching between 1-line («Любимое») and 2-line («моё летаа»)
                // labels NEVER shifts the controls below (раньше всё прыгало).
                // Плашку «Волна по треку» показываем ТОЛЬКО когда реально запущена волна
                // по конкретному треку (из поиска). Для Моей волны / настроений — обычный
                // большой заголовок (название настроения или «моё летаа»).
                Box(
                    Modifier.height(112.dp).fillMaxWidth().padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (waveByTrack != null) {
                        WaveByTrackPlate(
                            label = "ВОЛНА ПО ТРЕКУ",
                            // Фиксируем SEED-трек — название и обложку волны, а не
                            // текущего играющего. Волна по треку = «похожие на этот»,
                            // и в плашке должен оставаться один (исходный) трек.
                            title = waveByTrack.title,
                            artworkUrl = waveByTrack.artworkLarge ?: waveByTrack.artworkSmall,
                            artworkKey = waveByTrack.id,
                            accent = trackAccent,
                            onOpenFull = onOpenFull,
                            onReset = onResetWave,
                        )
                    } else if (waveByCollection) {
                        WaveByTrackPlate(
                            label = "ВОЛНА ПО КОЛЛЕКЦИИ",
                            // Название коллекции, а НЕ текущего трека.
                            title = "Мне нравится",
                            artworkUrl = null,
                            artworkKey = "collection",
                            accent = trackAccent,
                            onOpenFull = onOpenFull,
                            onReset = onResetWave,
                            coverIcon = R.drawable.sol_heart,
                        )
                    } else {
                        Text(
                            title,
                            color = TextPrimary,
                            fontSize = 50.sp,
                            lineHeight = 50.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.8).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(R.drawable.sol_prev, onPrev)
                    Spacer(Modifier.width(20.dp))
                    // Центральная кнопка: без подложки, белая иконка, крупнее,
                    // с полной анимацией смены play/pause (масштаб + поворот + фейд).
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPlayPauseOrStart,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            // Иконка переключается ПОЧТИ МГНОВЕННО (короткий быстрый
                            // фейд+лёгкий масштаб), не дожидаясь затухания звука: тап →
                            // сразу play/pause на иконке, а звук плавно гаснет/возрастает
                            // уже после (фейд громкости живёт во ViewModel).
                            transitionSpec = {
                                (scaleIn(initialScale = 0.82f, animationSpec = tween(110)) +
                                    fadeIn(tween(90))) togetherWith
                                    (scaleOut(targetScale = 0.82f, animationSpec = tween(90)) +
                                        fadeOut(tween(70)))
                            },
                            label = "playpause",
                        ) { playing ->
                            Icon(
                                painterResource(if (playing) R.drawable.sol_pause else R.drawable.sol_play),
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    GlassButton(R.drawable.sol_next, onNext)
                }

                // Push the mini-pill a touch lower than the controls.
                Spacer(Modifier.height(34.dp))

                // now-playing pill — a real peeking CAROUSEL over the queue. The
                // current track is the centred card; the previous & next tracks peek
                // in from both edges (видно соседние плашки слева и справа), and they
                // slide straight off the screen edge без обрезки (the pager spans the
                // full width, so nothing is clipped by a narrow container). Swipe to
                // change track; controls/auto-advance scroll the carousel in sync.
                if (pillList.isEmpty()) {
                    Spacer(Modifier.height(56.dp))
                } else {
                    // Идентичность очереди = ID ПЕРВОГО трека. Меняется ТОЛЬКО когда
                    // стартует ДРУГАЯ волна (выбор интереса/коллекции/трека → новый
                    // список с другим первым треком) — тогда pager пересоздаётся и
                    // мгновенно встаёт на нужную карточку (миниатюра не «едет» сама).
                    // ВАЖНО: размер/последний трек в ключ НЕ входят — иначе автоподгрузка
                    // волны (maybeLoadMoreWave дописывает треки) меняла ключ и при
                    // нажатии next/prev pager пересоздавался → резкий скачок вместо
                    // плавного скролла. Теперь внутри одной волны ключ стабилен.
                    val queueKey = remember(pillList) {
                        pillList.firstOrNull()?.id ?: "∅"
                    }
                    // ВАЖНО: число страниц pager'а должно ВСЕГДА отражать ТЕКУЩИЙ размер
                    // очереди. Pager ключуется только по id первого трека (чтобы автоподгрузка
                    // волны не пересоздавала его и скролл оставался плавным), но раньше лямбда
                    // pageCount = { pillList.size } ЗАМОРАЖИВАЛАСЬ на размере списка в момент
                    // создания pager'а. Когда очередь меняла длину в пределах одной волны
                    // (автоподгрузка/перестроение), счётчик страниц устаревал → можно было
                    // листать В ПУСТОТУ (getOrNull(page) → null → пустые карточки), хотя
                    // реальных треков столько не было; смена раздела пересоздавала экран и
                    // «возвращала» миниатюры. rememberUpdatedState держит размер живым, не
                    // пересоздавая сам pager.
                    val livePageCount = rememberUpdatedState(pillList.size)
                    val pagerState = androidx.compose.runtime.key(queueKey) {
                        rememberPagerState(
                            initialPage = pillIndex.coerceIn(0, (pillList.size - 1).coerceAtLeast(0)),
                        ) { livePageCount.value }
                    }
                    // Follow external track changes (controls, auto-advance) — плавно
                    // докручиваем карусель к новому индексу, если юзер не свайпает сам.
                    LaunchedEffect(pillIndex, pillList.size) {
                        if (pillIndex !in pillList.indices) return@LaunchedEffect
                        if (pagerState.settledPage == pillIndex) return@LaunchedEffect
                        if (pagerState.isScrollInProgress) return@LaunchedEffect
                        val delta = kotlin.math.abs(pagerState.currentPage - pillIndex)
                        if (delta <= 1) {
                            // Соседняя карточка — плавно докручиваем (промежуточных нет).
                            pagerState.animateScrollToPage(
                                pillIndex,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 360,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                ),
                            )
                        } else {
                            // Любой НЕсоседний переход — мгновенно, без пролистывания
                            // через каждый промежуточный трек между A и B.
                            pagerState.scrollToPage(pillIndex)
                        }
                    }
                    // Commit user swipes back to the player (only when there's a real queue).
                    val idx = rememberUpdatedState(pillIndex)
                    LaunchedEffect(pagerState, queue.size) {
                        // Дебаунс: ждём 220мс СТАБИЛЬНОЙ settledPage перед тем как
                        // переключать реальный трек в VM. Без этого при быстром свайпе
                        // через несколько треков pager на коротко settle'ил на
                        // промежуточных страницах → ExoPlayer начинал играть каждую
                        // промежуточную, и юзер слышал «приставание к каждому треку».
                        // Теперь играет только тот, на котором юзер РЕАЛЬНО остановился.
                        snapshotFlow {
                            if (pagerState.isScrollInProgress) -1 else pagerState.settledPage
                        }.collect { sp ->
                            if (sp < 0) return@collect
                            if (queue.isEmpty()) return@collect
                            if (sp !in queue.indices) return@collect
                            if (sp == idx.value) return@collect
                            kotlinx.coroutines.delay(220)
                            onSelectTrack(sp)
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 40.dp),
                        pageSpacing = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { page ->
                        val tr = pillList.getOrNull(page) ?: return@HorizontalPager
                        val isActive = page == pagerState.currentPage
                        NowPlayingPill(
                            track = tr,
                            isPlaying = isPlaying && isActive,
                            isCurrentFavorite = isFavorite(tr),
                            accent = trackAccent,
                            onOpenFull = onOpenFull,
                            onFavorite = { onFavorite(tr) },
                            // Без alpha/scale — плашка не моргает прозрачностью
                            // и не дёргает размер при свайпе. Соседи просто
                            // видны кусочками за contentPadding.
                        )
                    }
                }
            }
        }

        // ===== интересы (камешки) — pinned just above the navbar =====
        Text(
            "Настрой свою волну",
            color = Color(0xFF8A85A0),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        // iOS-style elastic rubber-band overscroll instead of Android's stretch.
        ElasticOverscroll(maxHorizontal = 40.dp, maxVertical = 0.dp) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(moods.size) { i ->
                    Pebble(moods[i], active = moods[i].id == activeMood) { onMood(moods[i]) }
                }
            }
        }

        // room for the floating navbar
        Spacer(Modifier.height(104.dp))
      }
    }
}

/**
 * «Волна по {трек}» plate — полностью скруглённая «капсула»: круглая обложка,
 * подпись «ВОЛНА ПО ТРЕКУ» в цвет трека и название (длинное обрезается «…»).
 * Показывается только когда реально играет волна по конкретному треку.
 */
@Composable
private fun WaveByTrackPlate(
    label: String,
    title: String,
    artworkUrl: String?,
    artworkKey: String,
    accent: Color,
    onOpenFull: () -> Unit,
    onReset: () -> Unit,
    // Когда задан — вместо обложки трека показываем эту иконку (напр. сердечко
    // для «волны по коллекции» = «Мне нравится»).
    coverIcon: Int? = null,
) {
    // ФИКСИРОВАННАЯ плашка: всегда на всю ширину (fillMaxWidth) с одинаковой
    // раскладкой — круглая обложка слева, подпись+название по центру (длинное
    // обрезается «…»), крестик справа. Размер плашки НЕ зависит от длины названия
    // или наличия обложки — она всегда одного размера и не «прыгает» (раньше Row
    // подгонялся под контент и плашка расширялась/сужалась при смене трека).
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenFull,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Смена трека (обложка + название) — плавный кроссфейд, без резкого скачка.
        // Занимает фиксированную ширину (weight 1f), поэтому плашка не меняет размер.
        androidx.compose.animation.Crossfade(
            targetState = listOf(artworkKey, label, title, artworkUrl ?: ""),
            animationSpec = androidx.compose.animation.core.tween(380),
            modifier = Modifier.weight(1f),
            label = "wavePlate",
        ) { st ->
            val key = st[0]; val lbl = st[1]; val ttl = st[2]; val url = st[3].ifEmpty { null }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverIcon != null) {
                        // Кружок-аватар коллекции «Мне нравится»: заливка в цвет
                        // акцента + белое сердечко (вместо обложки трека).
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(accent.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(coverIcon),
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    } else {
                        com.moyavolna.app.ui.components.Artwork(url, key, size = 52.dp, corner = 26.dp)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        lbl,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        ttl,
                        color = TextPrimary,
                        fontSize = 21.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // Крестик — сбросить волну по треку/коллекции обратно в Мою волну.
        // Просто «×» нормального размера, БЕЗ кружка вокруг (раньше иконка была
        // circled-close и выглядела как круглая кнопка).
        Box(
            Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onReset,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.sol_x),
                "Сбросить волну",
                tint = TextPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingPill(
    track: Track,
    isPlaying: Boolean,
    isCurrentFavorite: Boolean,
    accent: Color,
    onOpenFull: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Равномерная раскладка: волны слева с краю, название+автор по центру (длинное
    // обрезается …), сердечко справа с краю. Волны и активное сердечко — в цвет трека.
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x14FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenFull,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.moyavolna.app.ui.components.PlayingWave(isPlaying = isPlaying, color = accent)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                    append(track.artist)
                }
                withStyle(SpanStyle(color = Color(0xFFE2DDF0), fontWeight = FontWeight.SemiBold)) {
                    append("  —  ${track.title}")
                }
            },
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Box(
            Modifier.size(26.dp).clip(CircleShape).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onFavorite() },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isCurrentFavorite) R.drawable.sol_heart else R.drawable.sol_heart_out),
                null,
                tint = if (isCurrentFavorite) accent else TextPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun GlassButton(iconRes: Int, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "glassScale",
    )
    Box(
        Modifier
            .size(46.dp)
            .scale(sc)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .clickable(
                interactionSource = src,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun EqBars(isPlaying: Boolean, accent: Color = Color(0xFFB084F5)) {
    // Делегируем общему плавному компоненту, чтобы волна везде стартовала и затухала
    // одинаково мягко (без резкого включения).
    com.moyavolna.app.ui.components.PlayingWave(isPlaying = isPlaying, color = accent)
}

/**
 * A glossy 3D «камешек» that re-tunes the wave. By default it sits slightly
 * dimmed; the selected one animates to full brightness (выбранная становится в
 * обычную яркость). No pulsing, no ripple — just a clean brightness shift, which
 * also keeps the LazyRow scroll buttery (раньше пульсация + перерисовка градиента
 * на каждом кадре лагали при прокрутке).
 */
@Composable
private fun Pebble(mood: Mood, active: Boolean, onClick: () -> Unit) {
    val brightness by animateFloatAsState(
        targetValue = if (active) 1f else 0.6f,
        animationSpec = tween(220),
        label = "peb-bri",
    )
    val stoneShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 26.dp, bottomEnd = 26.dp)
    val body = mood.gradient()
    Box(
        Modifier
            .size(width = 112.dp, height = 104.dp)
            .graphicsLayer { alpha = brightness }
            .clip(stoneShape)
            // drawWithCache builds the radial-gradient shaders ONCE (rebuilt only when
            // the size changes) instead of re-allocating them every draw — keeps the
            // LazyRow scroll buttery (раньше градиенты пересоздавались на каждый кадр).
            .drawWithCache {
                val w = size.width
                val h = size.height
                val bodyBrush = Brush.radialGradient(
                    0f to body[0],
                    0.6f to body[1],
                    1f to body[2],
                    center = Offset(w * 0.3f, h * 0.2f),
                    radius = maxOf(w, h) * 1.15f,
                )
                val glossBrush = Brush.radialGradient(
                    0f to Color(0x99FFFFFF),
                    0.55f to Color(0x22FFFFFF),
                    1f to Color(0x00FFFFFF),
                    center = Offset(w * 0.5f, -h * 0.04f),
                    radius = w * 0.62f,
                )
                onDrawBehind {
                    drawRect(brush = bodyBrush)
                    drawRect(brush = glossBrush)
                }
            }
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    0f to Color(0x70FFFFFF),
                    0.5f to Color(0x1FFFFFFF),
                    1f to Color(0x14000000),
                ),
                shape = stoneShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Image(
                painterResource(mood.emojiRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.height(30.dp).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    mood.label,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
