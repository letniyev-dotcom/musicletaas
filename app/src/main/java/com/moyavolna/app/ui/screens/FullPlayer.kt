package com.moyavolna.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import com.moyavolna.app.R
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyavolna.app.data.Track
import com.moyavolna.app.ui.components.Artwork
import com.moyavolna.app.ui.components.gradientFor
import com.moyavolna.app.ui.theme.Accent
import com.moyavolna.app.ui.theme.TextMuted
import com.moyavolna.app.ui.theme.TextPrimary

@Composable
fun FullPlayer(
    track: Track,
    isPlaying: Boolean,
    position: kotlinx.coroutines.flow.StateFlow<Long>,
    durationMs: Long,
    isFavorite: Boolean,
    isDisliked: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDislike: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // Скруглённые углы «под экран устройства» — карточка открывается как лист.
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(40.dp))
            .background(Color.Black),
    ) {
        // ambient gradient from artwork tint
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B1530), Color(0xFF0D0B16), Color.Black)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp)
                .padding(top = 56.dp, bottom = 40.dp),
        ) {
            // top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onCollapse() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.sol_arrow_down), null, tint = TextPrimary, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("МОЁ ЛЕТАА", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(36.dp))

            // big artwork
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Просто Artwork — он сам рисует градиент-подложку с иконкой-нотой,
                // а сверху обложку. Раньше тут был внешний Box с gradientFor + Artwork
                // внутри, и при медленной загрузке открытие плеера на миг показывало
                // только градиент без обложки (двойная подложка + crossfade=false).
                // Теперь crossfade=true: обложка плавно появляется поверх ноты, даже
                // если кэш не прогрет — миниатюра не «пропадает» при открытии плеера.
                Artwork(
                    track.artworkLarge,
                    track.id,
                    size = 300.dp,
                    corner = 28.dp,
                    cacheKey = "art_${track.id}",
                    crossfade = true,
                )
            }

            Spacer(Modifier.height(36.dp))

            // title + artist + heart
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.artist,
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onToggleDislike() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.sol_dislike),
                        null,
                        tint = if (isDisliked) Accent else TextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (isFavorite) R.drawable.sol_heart else R.drawable.sol_heart_out),
                        null,
                        tint = if (isFavorite) Accent else TextPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // progress — позиция собирается ЛОКАЛЬНО внутри, чтобы тик ~5/сек не
            // перерисовывал обложку/кнопки (иначе открытие плеера лагало).
            ProgressSection(position = position, durationMs = durationMs, onSeek = onSeek)

            Spacer(Modifier.weight(1f))

            // controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CtrlIcon(R.drawable.sol_prev, 34.dp, onPrev)
                Spacer(Modifier.width(28.dp))
                // Центральная кнопка: тактильный отклик + лёгкое «вдавливание» при нажатии
                // и кроссфейд иконки play↔pause (как в мини-плеере и на главной).
                val playPress = remember { MutableInteractionSource() }
                val playPressed by playPress.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (playPressed) 0.9f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
                    label = "playScale",
                )
                Box(
                    Modifier
                        .size(76.dp)
                        .scale(playScale)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .clickable(
                            interactionSource = playPress,
                            indication = null,
                        ) { onPlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(120),
                        label = "fullPlayPause",
                    ) { playing ->
                        Icon(
                            painterResource(if (playing) R.drawable.sol_pause else R.drawable.sol_play),
                            null,
                            tint = Color(0xFF141022),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.width(28.dp))
                CtrlIcon(R.drawable.sol_next, 34.dp, onNext)
            }
        }
    }
}

@Composable
private fun ProgressSection(
    position: kotlinx.coroutines.flow.StateFlow<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val positionMs by position.collectAsState()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val dur = durationMs.coerceAtLeast(1L)
    val sliderPos = if (scrubbing) scrubValue else (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    Slider(
        value = sliderPos,
        onValueChange = { scrubbing = true; scrubValue = it },
        onValueChangeFinished = {
            onSeek((scrubValue * dur).toLong())
            scrubbing = false
        },
        colors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt(if (scrubbing) (scrubValue * dur).toLong() else positionMs), color = TextMuted, fontSize = 12.sp)
        Text(fmt(durationMs), color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun CtrlIcon(@androidx.annotation.DrawableRes icon: Int, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "ctrlScale",
    )
    Box(
        Modifier
            .size(56.dp)
            .scale(sc)
            .clip(CircleShape)
            .clickable(
                interactionSource = src,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = TextPrimary, modifier = Modifier.size(size))
    }
}

private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
