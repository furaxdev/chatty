package com.chatty.fr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001258),
    secondaryContainer = Color(0xFFE2E3F5),
    surface = Color(0xFFF8F9FF),
    surfaceContainer = Color(0xFFECEEF8),
    surfaceContainerHigh = Color(0xFFE5E7F2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF00218C),
    primaryContainer = Color(0xFF2B45D6),
    onPrimaryContainer = Color(0xFFDDE1FF),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272A31),
)

/** Couleurs de bulle au choix (0 = couleur du thème). */
val BubblePalette = listOf(
    null,
    Color(0xFF1E88E5), // Bleu iMessage
    Color(0xFF34C759), // Vert SMS
    Color(0xFF8E24AA), // Violet
    Color(0xFFE91E63), // Rose
    Color(0xFFFF6D00), // Orange
    Color(0xFF263238), // Ardoise
)

/** 0 = suivre le système, 1 = clair, 2 = sombre, 3 = noir AMOLED. */
const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2
const val THEME_BLACK = 3

@Composable
fun ChattyTheme(dynamicColor: Boolean, themeMode: Int = THEME_SYSTEM, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK, THEME_BLACK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    var scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    if (themeMode == THEME_BLACK) {
        // Noir pur : économise la batterie sur les écrans OLED.
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
        )
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
