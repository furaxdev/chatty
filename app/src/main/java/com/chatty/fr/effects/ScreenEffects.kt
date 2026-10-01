package com.chatty.fr.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Festive = listOf(
    Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE),
    Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFF6E40),
    Color.White,
)

/**
 * Full-screen message effect.
 *
 * Intentionally implemented without Canvas/drawScope/path/blend modes.
 * Every visual is a normal Compose node, animated with graphicsLayer.
 * This keeps the effect picker lightweight and avoids fragile text rasterization.
 */
@Composable
fun ScreenEffectOverlay(
    effect: MessageEffect,
    text: String,
    onFinished: () -> Unit,
) {
    val progress = remember(effect) { Animatable(0f) }
    val duration = when (effect) {
        MessageEffect.FIREWORKS, MessageEffect.LASERS, MessageEffect.SNOW -> 4200
        else -> 3400
    }

    LaunchedEffect(effect) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
        onFinished()
    }

    val seed = remember(effect) { Random(effect.hashCode()) }
    val particles = remember(effect) {
        List(80) {
            Particle(
                x = seed.nextFloat(),
                y = seed.nextFloat(),
                delay = seed.nextFloat() * 0.45f,
                size = 8f + seed.nextFloat() * 28f,
                speed = 0.45f + seed.nextFloat() * 0.8f,
                color = Festive[seed.nextInt(Festive.size)],
                phase = seed.nextFloat() * 6.28f,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (effect) {
            MessageEffect.CONFETTI -> ParticleRain(progress.value, particles)
            MessageEffect.BALLOONS -> Balloons(progress.value, particles)
            MessageEffect.LOVE -> Love(progress.value, particles)
            MessageEffect.FIREWORKS -> Fireworks(progress.value, particles)
            MessageEffect.LASERS -> Lasers(progress.value)
            MessageEffect.CELEBRATION -> Celebration(progress.value, particles)
            MessageEffect.SPOTLIGHT -> Spotlight(progress.value)
            MessageEffect.ECHO -> Echo(progress.value, particles)
            MessageEffect.SNOW -> Snow(progress.value, particles)
            MessageEffect.EMOJI_RAIN -> EmojiRain(progress.value, particles)
            MessageEffect.RAINBOW -> Rainbow(progress.value)
            MessageEffect.MONEY -> Money(progress.value, particles)
            MessageEffect.SHOOTING_STARS -> ShootingStars(progress.value, particles)
            else -> Unit
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val delay: Float,
    val size: Float,
    val speed: Float,
    val color: Color,
    val phase: Float,
)

private fun visibleProgress(t: Float, delay: Float): Float {
    return ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
}

private fun effectAlpha(t: Float): Float {
    return when {
        t < 0.08f -> t / 0.08f
        t > 0.82f -> ((1f - t) / 0.18f).coerceIn(0f, 1f)
        else -> 1f
    }
}

@Composable
private fun ParticleRain(t: Float, particles: List<Particle>) {
    particles.forEachIndexed { index, p ->
        val local = visibleProgress(t, p.delay)
        ParticleDot(
            p = p,
            x = p.x + sin(local * 10f + p.phase) * 0.06f,
            y = -0.08f + local * (1.05f + p.speed * 0.2f),
            rotation = local * 540f + index * 17f,
            alpha = effectAlpha(t),
        )
    }
}

@Composable
private fun Balloons(t: Float, particles: List<Particle>) {
    particles.take(16).forEachIndexed { index, p ->
        val local = visibleProgress(t, p.delay)
        val x = p.x + sin(local * 6f + p.phase) * 0.04f
        val y = 1.08f - local * 1.35f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = x * 900f - 450f
                    translationY = y * 1800f - 900f
                    scaleX = 0.8f + p.size / 80f
                    scaleY = 1.05f + p.size / 100f
                    alpha = effectAlpha(t)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (index % 2 == 0) "🎈" else "🎈",
                fontSize = 52.sp,
            )
        }
    }
}

@Composable
private fun Love(t: Float, particles: List<Particle>) {
    val scale = (t / 0.25f).coerceAtMost(1f)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = effectAlpha(t)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("❤", fontSize = 150.sp, color = Color(0xFFFF4D6D))
    }

    particles.take(18).forEach { p ->
        val local = visibleProgress(t, p.delay)
        ParticleDot(
            p = p,
            x = 0.5f + cos(p.phase) * local * 0.42f,
            y = 0.5f + sin(p.phase) * local * 0.42f,
            rotation = local * 180f,
            alpha = effectAlpha(t),
        )
    }
}

@Composable
private fun Fireworks(t: Float, particles: List<Particle>) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f * effectAlpha(t))))
    particles.take(24).forEachIndexed { index, p ->
        val cycle = ((t * 2.4f + index * 0.11f) % 1f)
        val radius = cycle * 0.28f
        ParticleDot(
            p = p,
            x = 0.5f + cos(p.phase) * radius,
            y = 0.35f + sin(p.phase) * radius,
            rotation = cycle * 360f,
            alpha = (1f - cycle) * effectAlpha(t),
        )
    }
}

@Composable
private fun Lasers(t: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f * effectAlpha(t))))
    repeat(8) { index ->
        val angle = t * 7f + index * 0.78f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = angle * 57.2958f
                    alpha = effectAlpha(t)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 900.dp)
                    .background(Festive[index % Festive.size]),
            )
        }
    }
}

@Composable
private fun Celebration(t: Float, particles: List<Particle>) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f * effectAlpha(t))))
    particles.take(50).forEachIndexed { index, p ->
        val local = visibleProgress(t, p.delay)
        ParticleDot(
            p = p,
            x = 1f - local * (0.5f + p.speed * 0.4f) + sin(local * 9f + p.phase) * 0.08f,
            y = local * local * 1.1f,
            rotation = local * 720f + index * 11f,
            alpha = (1f - local) * effectAlpha(t),
        )
    }
}

@Composable
private fun Spotlight(t: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f * effectAlpha(t)))
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = sin(t * 7f) * 500f
                translationY = cos(t * 5f) * 260f
                alpha = 0.7f * effectAlpha(t)
                scaleX = 2.2f
                scaleY = 2.2f
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(280.dp)
                .background(Color.White.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun Echo(t: Float, particles: List<Particle>) {
    particles.take(26).forEachIndexed { index, p ->
        val local = ((t - p.delay) / 0.45f).coerceIn(0f, 1f)
        if (local > 0f && local < 1f) {
            ParticleDot(
                p = p,
                x = if (index % 2 == 0) -0.08f + local * 1.16f else 1.08f - local * 1.16f,
                y = p.y,
                rotation = local * 180f,
                alpha = effectAlpha(t),
            )
        }
    }
}

@Composable
private fun Snow(t: Float, particles: List<Particle>) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0D1B2A).copy(alpha = 0.3f * effectAlpha(t))))
    particles.forEach { p ->
        val local = (p.y + t * p.speed) % 1f
        ParticleDot(
            p = p,
            x = p.x + sin(t * 8f + p.phase) * 0.03f,
            y = local,
            rotation = 0f,
            alpha = 0.9f * effectAlpha(t),
        )
    }
}

@Composable
private fun EmojiRain(t: Float, particles: List<Particle>) {
    val emojis = listOf("✨", "🎉", "⭐", "💫", "🌟")
    particles.take(35).forEachIndexed { index, p ->
        val local = visibleProgress(t, p.delay)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (p.x + sin(local * 8f + p.phase) * 0.05f) * 900f - 450f
                    translationY = (-0.1f + local * 1.2f) * 1800f - 900f
                    rotationZ = local * 240f
                    alpha = effectAlpha(t)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(emojis[index % emojis.size], fontSize = 30.sp)
        }
    }
}

@Composable
private fun Rainbow(t: Float) {
    val alpha = effectAlpha(t)
    val colors = listOf(
        Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00),
        Color(0xFF00E676), Color(0xFF2979FF), Color(0xFF651FFF),
        Color(0xFFD500F9),
    )
    colors.forEachIndexed { index, color ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = 450f - index * 42f
                    scaleX = 1.25f - index * 0.04f
                    scaleY = 0.16f
                    alpha = alpha * 0.9f
                }
                .background(color),
        )
    }
}

@Composable
private fun Money(t: Float, particles: List<Particle>) {
    particles.take(35).forEachIndexed { index, p ->
        val local = visibleProgress(t, p.delay)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (p.x + sin(local * 9f + p.phase) * 0.08f) * 900f - 450f
                    translationY = (-0.08f + local * 1.15f) * 1800f - 900f
                    rotationZ = sin(local * 8f + index) * 40f
                    alpha = effectAlpha(t)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("💵", fontSize = 34.sp)
        }
    }
}

@Composable
private fun ShootingStars(t: Float, particles: List<Particle>) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1026).copy(alpha = 0.86f * effectAlpha(t))))
    particles.take(18).forEachIndexed { index, p ->
        val local = ((t - p.delay) / 0.3f).coerceIn(0f, 1f)
        if (local > 0f && local < 1f) {
            ParticleDot(
                p = p,
                x = 0.9f - local * 1.0f,
                y = p.y * 0.45f + local * 0.5f,
                rotation = -30f,
                alpha = (1f - local) * effectAlpha(t),
            )
        }
    }
}

@Composable
private fun ParticleDot(
    p: Particle,
    x: Float,
    y: Float,
    rotation: Float,
    alpha: Float,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = x * 900f - 450f
                translationY = y * 1800f - 900f
                rotationZ = rotation
                alpha = alpha
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(p.size.dp)
                .background(p.color),
        )
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
