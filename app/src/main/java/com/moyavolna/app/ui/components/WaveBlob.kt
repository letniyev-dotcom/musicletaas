package com.moyavolna.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.moyavolna.app.player.AudioLevel
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * «Моё лето» — a glowing organic blob centred on black that breathes in time.
 * Built from a slow rotating colour sweep, drifting colour clouds, and a beating
 * white-purple core, all softened by a large blur into a single living glow.
 *
 * The three [colors] are derived from the playing track's cover art and crossfade
 * smoothly when the song changes; the core pulses to the real audio loudness
 * published by the playback Visualizer (falling back to a gentle auto-breath when
 * the rhythm signal isn't available).
 */
@Composable
fun WaveBlob(
    isPlaying: Boolean,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val t = rememberInfiniteTransition(label = "blob")

    val rot by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "rot",
    )
    // PERF: ОДНА infinite-transition с фиксированной длительностью «дыхания» 1800мс.
    // Раньше при каждой смене isPlaying пересоздавалась анимация (tween(if isPlaying
    // 1300 else 2600)) → infinite-transition сбрасывалась и запускалась заново, что
    // вызывало рывок и лишнюю работу для GPU. Теперь длительность стабильна, а
    // "громкость" дыхания регулируется через множитель `beatScale` ниже.
    val breath by t.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breath",
    )
    // Когда воспроизведение остановлено — амплитуда дыхания мягко уменьшается до
    // 50% (плавный animateFloatAsState), без перезапуска самой анимации.
    val breathAmp by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.5f,
        animationSpec = tween(800),
        label = "breathAmp",
    )
    val effectiveBreath = 1f + (breath - 1f) * breathAmp
    val d1 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(9000), RepeatMode.Reverse),
        label = "d1",
    )
    val d2 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(11000), RepeatMode.Reverse),
        label = "d2",
    )

    // Real-time loudness → snappy but smoothed pulse on the core. Чуть «жёстче»
    // пружина и больший множитель → блоб заметнее бьётся ПОД ТРЕК в такт музыке.
    val rawLevel by AudioLevel.level.collectAsState()
    val level by animateFloatAsState(
        targetValue = if (isPlaying) rawLevel else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "level",
    )
    val beat = effectiveBreath * (1f + level * 0.72f)

    // Smoothly crossfade the palette as the track changes.
    val c0 = colors.getOrElse(0) { Color(0xFFE879C9) }
    val c1c = colors.getOrElse(1) { Color(0xFFB084F5) }
    val c2c = colors.getOrElse(2) { Color(0xFF7B97FF) }
    val pink by animateColorAsState(c0, tween(1400), label = "pink")
    val accent by animateColorAsState(c1c, tween(1400), label = "accent")
    val blue by animateColorAsState(c2c, tween(1400), label = "blue")
    val violet = lerp(accent, blue, 0.5f)
    val tr = Color(0x00000000)

    // PERF: the glow is a full-screen blur that re-renders every frame (rotation +
    // breathing never stop). A 46dp blur over the whole screen at 60fps saturates the
    // GPU on mid-range phones and makes EVERYTHING on the home screen stutter — including
    // прокрутку камешков. So we render the blob into a HALF-resolution layer and upscale
    // it 2× : the blur then works on ¼ of the pixels (~4× cheaper) and looks identical
    // once blurred. fillMaxSize(0.5f) + centered parent + scale 2 → fills the screen exactly.
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
      // blur() Modifier работает только на Android 12+ (API 31). На более старых
      // версиях он no-op, но компилятор и Compose рантайм его принимают; чтобы
      // на старых девайсах не было «жёстких» углов многоугольников звезды,
      // условно убираем blur для API < 31 — там без блюра звезда смотрится как
      // острая фигура, и лучше вообще не применять modifier (Coil + GPU работа
      // на старых девайсах и так дорогая).
      val canvasMod = Modifier
          .fillMaxSize(0.5f)
          .graphicsLayer { scaleX = 2f; scaleY = 2f }
          .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(23.dp) else it }
      Canvas(canvasMod) {
        val c = center
        val r = size.minDimension / 2f

        // drifting colour clouds — the soft aura behind the star
        drawCircle(
            Brush.radialGradient(listOf(pink, tr), center = c, radius = r * 0.55f),
            radius = r * 0.55f,
            center = c + Offset((d1 - 0.5f) * r * 0.4f, (0.5f - d1) * r * 0.3f),
        )
        drawCircle(
            Brush.radialGradient(listOf(blue, tr), center = c, radius = r * 0.55f),
            radius = r * 0.55f,
            center = c + Offset((0.5f - d2) * r * 0.42f, (d2 - 0.5f) * r * 0.34f),
        )
        drawCircle(
            Brush.radialGradient(listOf(accent, tr), center = c, radius = r * 0.5f),
            radius = r * 0.5f,
            center = c + Offset((d2 - 0.5f) * r * 0.2f, (d1 - 0.5f) * r * 0.3f),
        )

        // ===== star burst with elongated points (как в концепте, скрин 2) =====
        // A multi-point star whose tips reach far past the body, so once blurred
        // the «волна» gets the drawn-out, almost-star edges of the concept.
        rotate(rot, pivot = c) {
            val starOuter = r * (0.72f + level * 0.10f) * beat
            val starInner = r * 0.24f
            drawPath(
                starPath(c, points = 6, outer = starOuter, inner = starInner),
                brush = Brush.radialGradient(
                    0f to lerp(accent, Color.White, 0.35f),
                    0.45f to accent.copy(alpha = 0.85f),
                    0.8f to violet.copy(alpha = 0.45f),
                    1f to tr,
                    center = c,
                    radius = starOuter,
                ),
            )
        }
        // second, offset star layer for richer, less symmetric rays
        rotate(-rot * 0.6f + 30f, pivot = c) {
            val o2 = r * (0.6f + level * 0.08f) * beat
            drawPath(
                starPath(c, points = 5, outer = o2, inner = r * 0.20f),
                brush = Brush.radialGradient(
                    0f to pink.copy(alpha = 0.7f),
                    0.6f to blue.copy(alpha = 0.4f),
                    1f to tr,
                    center = c,
                    radius = o2,
                ),
            )
        }

        // bright beating core — a small white star, scales with loudness
        rotate(rot * 0.5f, pivot = c) {
            val coreOuter = r * 0.5f * beat
            drawPath(
                starPath(c, points = 6, outer = coreOuter, inner = r * 0.20f),
                brush = Brush.radialGradient(
                    0f to Color.White,
                    0.5f to lerp(accent, Color.White, 0.6f),
                    0.85f to accent.copy(alpha = 0.35f),
                    1f to tr,
                    center = c,
                    radius = coreOuter,
                ),
            )
        }
      }
    }
}

/** Build a closed star polygon centred at [c] with [points] tips. */
private fun starPath(c: Offset, points: Int, outer: Float, inner: Float): Path {
    val path = Path()
    val total = points * 2
    val step = (PI / points).toFloat()
    for (i in 0 until total) {
        val rad = if (i % 2 == 0) outer else inner
        // start pointing up
        val ang = -PI.toFloat() / 2f + i * step
        val x = c.x + rad * cos(ang)
        val y = c.y + rad * sin(ang)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
