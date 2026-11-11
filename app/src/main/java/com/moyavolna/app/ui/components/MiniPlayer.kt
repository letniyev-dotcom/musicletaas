package com.moyavolna.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyavolna.app.R
import com.moyavolna.app.data.Track
import com.moyavolna.app.ui.theme.TextPrimary
import kotlinx.coroutines.flow.StateFlow

/**
 * Мини-плеер.
 * Простой horizontal pager + прогресс под текстом.
 * Прогресс рисуем только для АКТИВНОЙ карточки (page == currentPage). При смене
 * трека прогресс мгновенно обнуляется (LaunchedEffect(track.id)).
 */
@Composable
fun MiniPlayer(
    track: Track,
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    position: StateFlow<Long>,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val pillList = if (queue.isNotEmpty()) queue else listOf(track)
    val pillIndex = if (queue.isNotEmpty()) currentIndex.coerceIn(0, queue.size - 1) else 0

    Column(modifier.fillMaxWidth()) {
        // Ключ pager-а — id первого трека очереди. Не пересоздаём при доливе.
        val queueKey = remember(pillList) { pillList.firstOrNull()?.id ?: "∅" }
        val pagerState = key(queueKey) {
            rememberPagerState(
                initialPage = pillIndex.coerceIn(0, (pillList.size - 1).coerceAtLeast(0)),
            ) { pillList.size }
        }

        // Внешние смены индекса (next/prev, autoadvance) — плавно докручиваем pager.
        LaunchedEffect(pillIndex) {
            if (pillIndex !in pillList.indices) return@LaunchedEffect
            if (pagerState.currentPage == pillIndex) return@LaunchedEffect
            if (pagerState.isScrollInProgress) return@LaunchedEffect
            val delta = kotlin.math.abs(pagerState.currentPage - pillIndex)
            if (delta <= 1) {
                // Соседняя карточка — плавно докручиваем (между A и B промежуточных нет).
                pagerState.animateScrollToPage(
                    pillIndex,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                )
            } else {
                // Любой НЕсоседний переход (между A и B есть треки) — выполняем
                // МГНОВЕННО, без пролистывания через промежуточные плашки. Раньше
                // порог был delta > 2, поэтому переход на 2 трека (1 промежуточный)
                // всё равно прокручивался через него. Теперь pager при выборе трека
                // далеко в «Мне нравится» сразу встаёт на нужную карточку и
                // промежуточные треки между A и B не «поскакивают».
                pagerState.scrollToPage(pillIndex)
            }
        }

        // Свайп юзера — фиксируем выбор трека сразу как settled.
        val idx = rememberUpdatedState(pillIndex)
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { sp ->
                if (queue.isEmpty()) return@collect
                if (sp !in queue.indices) return@collect
                if (sp == idx.value) return@collect
                onSelectTrack(sp)
            }
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
            pageSpacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val tr = pillList.getOrNull(page) ?: return@HorizontalPager
            val isActive = page == pagerState.currentPage
            // Прогресс рисуем ТОЛЬКО на карточке РЕАЛЬНО играющего трека
            // (tr.id == track.id, где track — текущий трек из VM). При свайпе на
            // соседнюю карточку B она становится active ещё ДО того, как VM
            // переключит трек (crossSwitch сначала плавно гасит A ~240мс, потом
            // seekTo(B)). В этот момент position/durationMs ещё от A → раньше B
            // «на долю секунды» показывала прогресс предыдущей миниатюры. Теперь
            // B не рисует прогресс, пока сама не станет текущим треком (с нуля).
            val isCurrentTrack = tr.id == track.id
            MiniPill(
                track = tr,
                // n6727: значок play/pause завязываем ТОЛЬКО на глобальный isPlaying и
                // активную страницу (как на главной), без isCurrentTrack. Раньше при свайпе
                // на соседнюю карточку B она становилась active ДО того, как VM переключит
                // трек → isCurrentTrack=false → значок на миг показывал play, потом резко
                // pause. Теперь значок не «моргает» при листании.
                isPlaying = isPlaying && isActive,
                position = if (isActive && isCurrentTrack) position else null,
                durationMs = if (isActive && isCurrentTrack) durationMs else 0L,
                onPlayPause = onPlayPause,
                onOpenFull = onExpand,
            )
        }
    }
}

@Composable
private fun MiniPill(
    track: Track,
    isPlaying: Boolean,
    position: StateFlow<Long>?,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseDark = Color(0xF21C1A24)
    val progressShade = Color(0xFF2D2A3A)

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(baseDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenFull,
            ),
    ) {
        // Прогресс — простой, без интерполяции. На смену трека (track.id) — обнуление.
        if (position != null) {
            key(track.id) {
                SimpleProgressBackground(
                    trackId = track.id,
                    position = position,
                    durationMs = durationMs,
                    color = progressShade,
                )
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkSmall, track.id, size = 44.dp, corner = 10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
            ) {
                Text(
                    track.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    color = Color(0xFF9F99B5),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = tween(110),
                    label = "mini-playpause",
                ) { playing ->
                    Icon(
                        painterResource(if (playing) R.drawable.sol_pause else R.drawable.sol_play),
                        null,
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleProgressBackground(
    trackId: String,
    position: StateFlow<Long>,
    durationMs: Long,
    color: Color,
) {
    val pos: State<Long> = position.collectAsState()
    val durState = rememberUpdatedState(durationMs)
    val colorState = rememberUpdatedState(color)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val dur = durState.value
                if (dur <= 0L) return@drawBehind
                val p = pos.value
                if (p <= 0L) return@drawBehind
                val frac = (p.toFloat() / dur).coerceIn(0f, 1f)
                if (frac <= 0.001f) return@drawBehind
                drawRect(
                    color = colorState.value,
                    size = size.copy(width = size.width * frac),
                )
            },
    )
    // Ключуем trackId, чтобы при смене трека Compose пересоздавал блок:
    // collectAsState заново инициализируется и старая «висящая» позиция уходит.
    LaunchedEffect(trackId) { /* trigger recomposition tree reset on track change */ }
}
