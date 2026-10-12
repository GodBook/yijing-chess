package com.yijing.chess.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BoardWoodLight = Color(0xFFE8D3A7)
val BoardWoodDark = Color(0xFFB58863)
val BoardWoodBg = Color(0xFFD2B48C)
val XiangqiRedColor = Color(0xFFB82828)
val XiangqiBlackColor = Color(0xFF1E2124)
val BoardLineColor = Color(0xFF4A3728)

val PrimaryGold = Color(0xFFE5B869)
val SecondaryGold = Color(0xFFC79848)
val DarkBackground = Color(0xFF141210)
val DarkSurface = Color(0xFF1F1C18)
val DarkSurfaceVariant = Color(0xFF2C2722)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGold,
    secondary = SecondaryGold,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF0EBE1),
    onSurface = Color(0xFFF0EBE1)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B5A2B),
    secondary = Color(0xFFA0522D),
    background = Color(0xFFF9F6F0),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1E1A16),
    onSurface = Color(0xFF1E1A16)
)

@Composable
fun YiJingTheme(
    darkTheme: Boolean = true, // 默认沉浸式典雅暗黑古风
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
