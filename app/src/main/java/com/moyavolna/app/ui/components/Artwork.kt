package com.moyavolna.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.moyavolna.app.ui.theme.Accent
import com.moyavolna.app.ui.theme.BlueAccent
import com.moyavolna.app.ui.theme.OrangeAccent
import com.moyavolna.app.ui.theme.PinkAccent
import com.moyavolna.app.ui.theme.TealAccent
import androidx.compose.ui.res.painterResource
import com.moyavolna.app.R

private val gradients = listOf(
    listOf(Accent, BlueAccent),
    listOf(PinkAccent, Accent),
    listOf(TealAccent, BlueAccent),
    listOf(OrangeAccent, PinkAccent),
    listOf(Accent, Color(0xFF2A2350)),
)

fun gradientFor(seed: String): Brush {
    val idx = (seed.hashCode().let { if (it < 0) -it else it }) % gradients.size
    return Brush.linearGradient(gradients[idx])
}

@Composable
fun Artwork(
    url: String?,
    seed: String,
    size: Dp,
    corner: Dp = 16.dp,
    modifier: Modifier = Modifier,
    cacheKey: String? = null,
    crossfade: Boolean = true,
) {
    val shape = RoundedCornerShape(corner)
    // Серый полупрозрачный плейсхолдер пока обложка грузится — без иконки ноты,
    // просто однотонный серый прямоугольник.
    val placeholderBg = Color.White.copy(alpha = 0.08f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(placeholderBg),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    // Плавное появление обложки после подгрузки (n7538): 100мс по умолчанию
                    // выглядели «резко». 350мс fade-in делает появление мягким. Для картинок
                    // из memory-cache Coil всё равно пропускает кроссфейд → мгновенно, без мигания.
                    .crossfade(if (crossfade) 350 else 0)
                    .apply { cacheKey?.let { memoryCacheKey(it) } }
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
