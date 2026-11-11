package com.moyavolna.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.moyavolna.app.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyavolna.app.data.Track
import com.moyavolna.app.ui.components.ElasticOverscroll
import com.moyavolna.app.ui.components.TrackListItem
import com.moyavolna.app.ui.theme.Accent
import com.moyavolna.app.ui.theme.AccentDeep
import com.moyavolna.app.ui.theme.TextMuted
import com.moyavolna.app.ui.theme.TextPrimary

@Composable
fun CollectionScreen(
    favorites: List<Track>,
    currentId: String?,
    isPlaying: Boolean = false,
    onTrackClick: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayAll: () -> Unit,
    bottomOverlay: @Composable () -> Unit = {},
) {
    if (favorites.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
            Text(
                "Коллекция",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 22.dp, bottom = 16.dp),
            )
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Здесь будут ваши любимые треки.\nНажмите ♥ на любом треке.",
                    color = TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        return
    }

    // Gallery-style: the favourites list fills the whole screen and scrolls up behind the
    // floating header (заголовок + карточка «Мне нравится»). Top scrim is a thin Black→Transparent
    // fade like the NavBar — tracks stay visible scrolling up behind the «Коллекция» title.
    val headerH = 196.dp      // resting top inset (first row sits just below the card)
    val topScrimH = 132.dp    // thin top fade height (status bar + title)
    Box(Modifier.fillMaxSize()) {
        // ---- scrolling list (sits UNDER the header) ----
        ElasticOverscroll(maxVertical = 56.dp, maxHorizontal = 0.dp) {
            LazyColumn(contentPadding = PaddingValues(top = headerH, bottom = 200.dp)) {
                items(favorites, key = { it.id }) { t ->
                    TrackListItem(
                        track = t,
                        isCurrent = t.id == currentId,
                        isPlaying = isPlaying,
                        isFavorite = true,
                        onClick = { onTrackClick(t, favorites) },
                        onToggleFavorite = { onToggleFavorite(t) },
                    )
                }
            }
        }

        // ---- thin top fade (mirror of the NavBar): black at the very top → transparent ----
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topScrimH)
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent))),
        )

        // ---- floating header (title + "Мне нравится" card) on top ----
        Column(Modifier.fillMaxWidth().padding(top = 56.dp)) {
            Text(
                "Коллекция",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 22.dp, bottom = 16.dp),
            )
            Row(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(AccentDeep, Color(0xFF2A2350))))
                    .clickable(onClick = onPlayAll)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.sol_heart), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Мне нравится", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${favorites.size} треков", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.sol_play), null, tint = Color(0xFF141022), modifier = Modifier.size(22.dp))
                }
            }
        }

        // ---- bottom overlay (mini player) — часть экрана коллекции, не корневого Box.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)) {
            bottomOverlay()
        }
    }
}
