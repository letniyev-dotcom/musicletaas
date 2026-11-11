package com.moyavolna.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyavolna.app.player.AudioLevel

/**
 * Живой эквалайзер «играющего трека»: столбики плавно «дышат», пока идёт
 * воспроизведение, и — ГЛАВНОЕ — двигаются ПОД ТРЕК: их размах задаётся реальной
 * громкостью звука (RMS из Visualizer), которую публикует [AudioLevel]. Громче
 * музыка → выше прыгают столбики, тише → спокойнее.
 *
 * Если сигнал недоступен (RECORD_AUDIO не выдан / тишина) — остаётся мягкое
 * генеративное «дыхание», чтобы индикатор всё равно выглядел живым.
 *
 * Амплитуда движения плавно НАРАСТАЕТ при старте и плавно ЗАТУХАЕТ при паузе/
 * остановке (а не включается/выключается резко).
 *
 * Используется и в списках (индикатор текущего трека), и в мини-плашке на главной —
 * единый «вейв» во всём приложении.
 */
@Composable
fun PlayingWave(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 4,
    height: Dp = 15.dp,
    barWidth: Dp = 3.dp,
    gap: Dp = 2.5.dp,
) {
    // Реальная громкость воспроизведения (0f..1f), общая на весь процесс.
    val rawLevel by AudioLevel.level.collectAsState()
    // Сглаживаем громкость МЯГКОЙ критически-задемпфированной пружиной: столбики
    // плавно следуют за музыкой, без резких скачков и дрожи (raw RMS из Visualizer
    // прилетает часто и «колюче» — здесь оно превращается в плавную огибающую).
    val level by animateFloatAsState(
        targetValue = if (isPlaying) rawLevel else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 130f),
        label = "waveLevel",
    )

    val t = rememberInfiniteTransition(label = "wave")
    // Каждый столбик колеблется в своём ритме — волна выглядит живой, а не синхронной.
    // Длинные плавные tween'ы (с FastOutSlowIn) → мягкое «дыхание», а не дёрганье.
    val phases = (0 until bars).map { idx ->
        t.animateFloat(
            initialValue = 0.42f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(720 + idx * 160, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wb$idx",
        )
    }
    // Плавный запуск/затухание движения: на старте 0→1, на паузе 1→0.
    val amp by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isPlaying) 520 else 380,
            easing = FastOutSlowInEasing,
        ),
        label = "waveAmp",
    )
    // «Энергия» звука: пол (0.30) оставляет лёгкое плавное движение на тихих
    // участках, а реальная громкость (level) мягко добавляет размах. Множитель
    // уменьшен → амплитуда нарастает плавно, без резких выбросов.
    val energy = 0.30f + level * 0.95f
    val rest = 0.30f // высота «спокойных» столбиков, когда волна не играет
    Row(
        modifier = modifier.height(height),
        verticalAlignment = Alignment.Bottom,
    ) {
        phases.forEachIndexed { i, h ->
            // Индивидуальный ритм столбика, умноженный на энергию звука → высота прыжка.
            val dynamic = h.value * energy
            // Плавно интерполируем между «покоем» и реальным движением через amp.
            val factor = (rest + (dynamic - rest) * amp).coerceIn(0.14f, 1f)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(end = if (i < bars - 1) gap else 0.dp)
                    .width(barWidth)
                    .height(height * factor)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
