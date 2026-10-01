package com.chatty.fr.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val Festive = listOf(
    Color(0xFFFF5252),
    Color(0xFFFFD740),
    Color(0xFF69F0AE),
    Color(0xFF40C4FF),
    Color(0xFFE040FB),
    Color(0xFFFF6E40),
    Color.White,
)

/**
 * Full-screen message effect.
 *
 * Deliberately contains no Canvas, DrawScope, Path, BlendMode or manual text rasterization.
 * Visuals are regular Compose nodes animated with Compose animation APIs.
 */
@Composable
fun ScreenEffectOverlay(effect: MessageEffect, text: String, onFinished: () -> Unit) {
    val duration = when (effect) {
        MessageEffect.FIREWORKS, MessageEffect.LASERS, MessageEffect.SNOW -> 4200L
        else -> 3400L
    }

    var visible by remember(effect) { mutableStateOf(true) }

    LaunchedEffect(effect) {
        kotlinx.coroutines.delay(duration)
        visible = false
        kotlinx.coroutines.delay(350L)
        onFinished()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(300)),
        ) {
            when (effect) {
                MessageEffect.CONFETTI -> ConfettiEffect()
                MessageEffect.BALLOONS -> BalloonsEffect()
                MessageEffect.LOVE -> LoveEffect()
                MessageEffect.FIREWORKS -> FireworksEffect()
                MessageEffect.LASERS -> LasersEffect()
                MessageEffect.CELEBRATION -> CelebrationEffect()
                MessageEffect.SPOTLIGHT -> SpotlightEffect()
                MessageEffect.ECHO -> EchoEffect(text)
                MessageEffect.SNOW -> SnowEffect()
                MessageEffect.EMOJI_RAIN -> EmojiRainEffect()
                MessageEffect.RAINBOW -> RainbowEffect()
                MessageEffect.MONEY -> MoneyEffect()
                MessageEffect.SHOOTING_STARS -> ShootingStarsEffect()
                else -> Unit
            }
        }
    }
}

@Composable
private fun ConfettiEffect() {
    ParticleField(34, Festive, ParticleShape.Rect, ParticleMode.Fall, 6..12)
}

@Composable
private fun BalloonsEffect() {
    ParticleField(12, Festive.dropLast(1), ParticleShape.Balloon, ParticleMode.Rise, 42..64)
}

@Composable
private fun LoveEffect() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(tween(700)) + fadeIn(tween(500)),
            exit = scaleOut(tween(300)) + fadeOut(tween(300)),
        ) {
            Text("❤", fontSize = 150.sp, color = Color(0xFFFF4D6D))
        }
    }
}

@Composable
private fun FireworksEffect() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))) {
        ParticleField(26, Festive.dropLast(1), ParticleShape.Spark, ParticleMode.Burst, 5..10)
    }
}

@Composable
private fun LasersEffect() {
    val colors = listOf(Color(0xFF00E5FF), Color(0xFFFF1744), Color(0xFF76FF03), Color(0xFFE040FB))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
        repeat(10) { index ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = index * 36f }
                    .wrapContentSize(Alignment.Center),
            ) {
                Box(
                    Modifier
                        .size(width = 6.dp, height = 900.dp)
                        .background(colors[index % colors.size], RoundedCornerShape(50)),
                )
            }
        }
        Text("✦", fontSize = 90.sp, color = Color.White)
    }
}

@Composable
private fun CelebrationEffect() {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🎉", fontSize = 92.sp)
            Text("CÉLÉBRATION !", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("✨", fontSize = 52.sp)
        }
    }
}

@Composable
private fun SpotlightEffect() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.padding(32.dp).size(190.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
        Text("★", color = Color.White, fontSize = 82.sp, modifier = Modifier.padding(82.dp))
    }
}

@Composable
private fun EchoEffect(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(4) { index ->
                Text(
                    text = text.ifBlank { "..." },
                    color = Festive[index % Festive.size].copy(alpha = 1f - index * 0.2f),
                    fontSize = (34 - index * 5).sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1f - index * 0.06f
                        scaleY = 1f - index * 0.06f
                    },
                )
            }
        }
    }
}

@Composable
private fun SnowEffect() {
    ParticleField(36, listOf(Color.White), ParticleShape.Snow, ParticleMode.Fall, 5..14)
}

@Composable
private fun EmojiRainEffect() {
    ParticleField(22, listOf(Color.White), ParticleShape.Emoji, ParticleMode.Fall, 26..42)
}

@Composable
private fun RainbowEffect() {
    val colors = listOf(
        Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00),
        Color(0xFF00E676), Color(0xFF2979FF), Color(0xFF651FFF), Color(0xFFD500F9),
    )
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        colors.forEach { color ->
            Box(
                Modifier.fillMaxWidth(0.78f).padding(vertical = 3.dp).size(16.dp)
                    .background(color, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun MoneyEffect() {
    ParticleField(28, listOf(Color(0xFF8BC34A), Color(0xFFFFD54F)), ParticleShape.Coin, ParticleMode.Fall, 28..42)
}

@Composable
private fun ShootingStarsEffect() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1026).copy(alpha = 0.9f))) {
        ParticleField(18, listOf(Color.White), ParticleShape.Star, ParticleMode.Diagonal, 5..10)
    }
}

private enum class ParticleShape { Rect, Balloon, Spark, Snow, Emoji, Coin, Star }
private enum class ParticleMode { Fall, Rise, Burst, Diagonal }

private data class Particle(
    val index: Int,
    val x: Float,
    val y: Float,
    val delay: Int,
    val size: Int,
    val colorIndex: Int,
)

@Composable
private fun ParticleField(
    count: Int,
    colors: List<Color>,
    shape: ParticleShape,
    mode: ParticleMode,
    sizeRange: IntRange,
) {
    val seed = remember(shape, mode, count) { Random.nextInt() }
    val particles = remember(seed) {
        List(count) { index ->
            Particle(
                index = index,
                x = Random(seed + index * 31).nextFloat(),
                y = Random(seed + index * 67).nextFloat(),
                delay = Random(seed + index * 97).nextInt(0, 900),
                size = Random(seed + index * 131).nextInt(sizeRange.first, sizeRange.last + 1),
                colorIndex = Random(seed + index * 173).nextInt(colors.size),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            AnimatedParticle(particle, colors, shape, mode)
        }
    }
}

@Composable
private fun AnimatedParticle(
    particle: Particle,
    colors: List<Color>,
    shape: ParticleShape,
    mode: ParticleMode,
) {
    val progress = remember(particle) { Animatable(0f) }

    LaunchedEffect(particle) {
        if (particle.delay > 0) kotlinx.coroutines.delay(particle.delay.toLong())
        progress.animateTo(
            1f,
            tween(2800 + (particle.index % 5) * 220, easing = LinearEasing),
        )
    }

    val p = progress.value
    val x = when (mode) {
        ParticleMode.Diagonal -> particle.x - p * 0.45f
        else -> particle.x + sin(p * 6f + particle.index) * 0.035f
    }
    val y = when (mode) {
        ParticleMode.Fall -> particle.y + p * 1.15f
        ParticleMode.Rise -> particle.y + (1f - p) * 1.25f
        ParticleMode.Burst -> particle.y + sin(p * 3.14f + particle.index) * 0.18f
        ParticleMode.Diagonal -> particle.y + p * 0.65f
    }
    val alpha = if (p > 0.88f) ((1f - p) / 0.12f).coerceIn(0f, 1f) else 1f
    val rotation = p * (particle.index % 2 * 2 - 1) * 180f

    ParticleView(
        shape = shape,
        color = colors[particle.colorIndex],
        size = particle.size,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
            .offset {
                IntOffset((x * 900f).roundToInt(), (y * 1800f).roundToInt())
            }
            .alpha(alpha)
            .graphicsLayer { rotationZ = rotation },
    )
}

@Composable
private fun ParticleView(shape: ParticleShape, color: Color, size: Int, modifier: Modifier) {
    val unit = size.dp
    Box(modifier.size(unit)) {
        when (shape) {
            ParticleShape.Rect -> Box(
                Modifier.size(unit, unit / 2).background(color, RoundedCornerShape(2.dp)),
            )
            ParticleShape.Balloon -> Box(Modifier.size(unit).background(color, CircleShape))
            ParticleShape.Spark -> Text("✦", fontSize = unit.value.sp, color = color)
            ParticleShape.Snow -> Text("•", fontSize = unit.value.sp, color = color)
            ParticleShape.Emoji -> Text(listOf("🎉", "✨", "🎈", "⭐", "💫")[size % 5], fontSize = unit.value.sp)
            ParticleShape.Coin -> Box(
                Modifier.size(unit).background(color, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("€", fontSize = (size * 0.48f).sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            ParticleShape.Star -> Text("★", fontSize = unit.value.sp, color = color)
        }
    }
}

@Composable
fun EffectBadge(effect: MessageEffect) {
    Text(
        text = "${effect.emoji} Envoyé avec « ${effect.label} »",
        fontSize = 11.sp,
        color = Color.Gray,
        fontWeight = FontWeight.Normal,
    )
}
