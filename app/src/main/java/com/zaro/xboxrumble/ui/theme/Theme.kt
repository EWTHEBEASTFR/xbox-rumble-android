package com.zaro.xboxrumble.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lilac = Color(0xFFB8A9E8)
val Amber = Color(0xFFF5A623)
val Teal  = Color(0xFF4ECDC4)
val Coral = Color(0xFFFF6B6B)
val Green = Color(0xFF4ADE80)
val Ink   = Color(0xFF1A1A1A)
val PageBg = Color(0xFFFAFAF8)
val CardBg = Color.White
val Hairline = Color(0xFFF0F0F0)
val MetaText = Color(0xFF9B9B9B)
val SecondaryText = Color(0xFF6B6B6B)

private val BrandLightScheme = lightColorScheme(
    primary = Lilac, onPrimary = Ink,
    secondary = Teal, onSecondary = Ink,
    tertiary = Amber,
    background = PageBg, onBackground = Ink,
    surface = CardBg, onSurface = Ink,
    error = Coral, onError = Color.White,
)

@Composable
fun XboxRumbleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrandLightScheme, content = content)
}