package com.moyavolna.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyavolna.app.R
import com.moyavolna.app.ui.theme.TextPrimary

enum class Tab(
    val label: String,
    @DrawableRes val icon: Int,
) {
    Home("Главная", R.drawable.sol_nav_home),
    Search("Поиск", R.drawable.sol_nav_search),
    Collection("Коллекция", R.drawable.sol_nav_library),
}

@Composable
fun NavBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
            )
            // Перехватываем ВСЕ касания в зоне навбара, чтобы тапы по «пустым» местам
            // между пунктами не проваливались на треки/камешки под навбаром
            // (раньше при попытке нажать пункт иногда тыкался трек снизу).
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == current
            // Плавный перелив цвета и лёгкое «подрастание» активной иконки вместо
            // мгновенного переключения — смена таба ощущается мягче и живее.
            val color by animateColorAsState(
                targetValue = if (on) TextPrimary else Color(0xFF8A85A0),
                animationSpec = tween(260),
                label = "navColor",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (on) 1.12f else 1f,
                animationSpec = tween(260),
                label = "navScale",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onSelect(tab)
                    },
            ) {
                Icon(
                    painterResource(tab.icon),
                    null,
                    tint = color,
                    modifier = Modifier.size(25.dp).scale(iconScale),
                )
                Spacer(Modifier.height(5.dp))
                Text(tab.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
