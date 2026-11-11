package com.moyavolna.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ===== Palette (matches the HTML concept) =====
val Bg = Color(0xFF000000)
val BgElev = Color(0xFF181818)
val BgElev2 = Color(0xFF101010)
val TextPrimary = Color(0xFFF4F4F5)
val TextMuted = Color(0xFF8A8A92)
val Accent = Color(0xFFB084F5)
val AccentDeep = Color(0xFF6E54C9)
val BlueAccent = Color(0xFF5B8DEF)
val PinkAccent = Color(0xFFE879C9)
val TealAccent = Color(0xFF3DD6C4)
val OrangeAccent = Color(0xFFF5A35A)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF15101F),
    background = Bg,
    onBackground = TextPrimary,
    surface = BgElev,
    onSurface = TextPrimary,
    surfaceVariant = BgElev,
    onSurfaceVariant = TextMuted,
)

private val AppTypography = Typography().run {
    val f = FontFamily.SansSerif
    Typography(
        displayLarge = displayLarge.copy(fontFamily = f),
        headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.ExtraBold),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 18.sp),
        bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f),
        labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun MoyaVolnaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content,
    )
}
