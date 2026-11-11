package com.moyavolna.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.moyavolna.app.R
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.moyavolna.app.data.Track
import com.moyavolna.app.ui.SearchState
import com.moyavolna.app.ui.components.ElasticOverscroll
import com.moyavolna.app.ui.components.TrackListItem
import com.moyavolna.app.ui.theme.Accent
import com.moyavolna.app.ui.theme.BgElev
import com.moyavolna.app.ui.theme.TextMuted
import com.moyavolna.app.ui.theme.TextPrimary

@Composable
fun SearchScreen(
    searchFlow: kotlinx.coroutines.flow.StateFlow<SearchState>,
    currentId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    isDisliked: (String) -> Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    bottomOverlay: @Composable () -> Unit = {},
) {
    // Состояние поиска собираем ЛОКАЛЬНО (не в корне) → ввод не перерисовывает весь экран.
    val state by searchFlow.collectAsState()
    // Текст поля — ЛОКАЛЬНЫЙ источник правды: ввод мгновенный, каретка не прыгает в
    // начало (раньше текст гонялся через StateFlow и подлагивал/сбрасывал курсор).
    var query by rememberSaveable { mutableStateOf(searchFlow.value.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Gallery-style: the results list fills the WHOLE screen and scrolls up behind the
    // floating header (бесконечная прокрутка до самого верха). The top scrim is a thin
    // Black→Transparent fade just like the NavBar's bottom fade — совсем слегка сверху,
    // so tracks stay VISIBLE scrolling up behind the «Поиск» title (no solid block).
    val headerH = 110.dp      // resting top inset (first row sits just below the field)
    val topScrimH = 92.dp     // thin top fade height (status bar + field)
    Box(Modifier.fillMaxSize()) {
        // ---- scrolling content (sits UNDER the header) ----
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            state.searched && state.results.isEmpty() -> EmptyHint("Ничего не нашлось — попробуйте другой запрос", headerH)
            !state.searched -> EmptyHint("Найдите любой трек или артиста — играет сразу, целиком", headerH)
            else -> ElasticOverscroll(maxVertical = 56.dp, maxHorizontal = 0.dp) {
                LazyColumn(contentPadding = PaddingValues(top = headerH, bottom = 200.dp)) {
                    items(state.results, key = { it.id }) { t ->
                        TrackListItem(
                            track = t,
                            isCurrent = t.id == currentId,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite(t.id),
                            onClick = { onTrackClick(t, state.results) },
                            onToggleFavorite = { onToggleFavorite(t) },
                            isDisliked = isDisliked(t.id),
                            onToggleDislike = { onDislike(t) },
                        )
                    }
                }
            }
        }

        // ---- thin top fade (mirror of the NavBar): black at the very top edge → transparent,
        // so rows fade out only right at the top and stay visible scrolling behind the title ----
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topScrimH)
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent))),
        )

        // ---- floating header (только поле поиска, без заголовка) ----
        Column(Modifier.fillMaxWidth().padding(top = 56.dp)) {
            // search field
            Row(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .height(52.dp) // fixed height — пилюля не меняет размер при вводе
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgElev)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.sol_nav_search), null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Трек, артист, жанр…", color = TextMuted, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; onQueryChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSubmit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        painterResource(R.drawable.sol_close), null, tint = TextMuted,
                        modifier = Modifier.size(20.dp).clickable { query = ""; onQueryChange("") },
                    )
                }
            }
        }

        // ---- bottom overlay (mini player) — часть экрана, а не корневого Box.
        // Так плашка живёт ВНУТРИ экрана поиска и переключается вместе с ним при
        // смене таба, не «болтаясь» в корне приложения.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)) {
            bottomOverlay()
        }
    }
}

@Composable
private fun EmptyHint(text: String, topInset: androidx.compose.ui.unit.Dp) {
    // Sit BELOW the floating header (раньше подпись наезжала на заголовок «Поиск»).
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = topInset + 24.dp, start = 40.dp, end = 40.dp, bottom = 120.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(text, color = TextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
