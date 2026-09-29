package com.furaxdev.chatty.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun ChattyTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
