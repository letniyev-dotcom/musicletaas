package com.moyavolna.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.moyavolna.app.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyavolna.app.data.Track
import com.moyavolna.app.ui.theme.Accent
import com.moyavolna.app.ui.theme.TextMuted
import com.moyavolna.app.ui.theme.TextPrimary

@Composable
fun TrackCard(track: Track, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(138.dp)
            .clickable(onClick = onClick),
    ) {
        Artwork(track.artworkLarge, track.id, size = 138.dp, corner = 18.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            track.title,
            color = TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TrackListItem(
    track: Track,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    isDisliked: Boolean = false,
    onToggleDislike: (() -> Unit)? = null,
    isPlaying: Boolean = true,
) {
    // Плавный переход подсветки активного трека между строками: цвет названия мягко
    // перетекает, а индикатор появляется/исчезает кроссфейдом (а не «прыгает»).
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) Accent else TextPrimary,
        animationSpec = tween(300),
        label = "trackTitle",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(track.artworkSmall, track.id, size = 52.dp, corner = 12.dp)
            // Индикатор играющего трека: затемнение + ЖИВАЯ волна (плавно стартует и
            // затухает в такт воспроизведению), а не статичная иконка.
            PlayingIndicatorOverlay(visible = isCurrent, isPlaying = isPlaying)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        if (onToggleDislike != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleDislike),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.sol_dislike),
                    null,
                    tint = if (isDisliked) Accent else TextMuted,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isFavorite) R.drawable.sol_heart else R.drawable.sol_heart_out),
                null,
                tint = if (isFavorite) Accent else TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlayingIndicatorOverlay(visible: Boolean, isPlaying: Boolean) {
    // Вынесено в отдельную composable-функцию, чтобы AnimatedVisibility
    // не пытался разрешиться к RowScope-варианту через внешний Row.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            PlayingWave(isPlaying = isPlaying, color = Accent, height = 18.dp, bars = 4)
        }
    }
}

@Composable
fun SectionRow(
    title: String,
    tracks: List<Track>,
    subtitleFor: (Track) -> String,
    onTrackClick: (Track) -> Unit,
) {
    Column(Modifier.padding(top = 26.dp)) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(tracks.size) { i ->
                val t = tracks[i]
                TrackCard(t, subtitleFor(t)) { onTrackClick(t) }
            }
        }
    }
}
