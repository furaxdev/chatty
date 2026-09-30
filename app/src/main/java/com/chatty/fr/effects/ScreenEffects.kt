package com.chatty.fr.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Festive = listOf(
    Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF),
    Color(0xFFE040FB), Color(0xFFFF6E40), Color(0xFFFFFFFF),
)

/**
 * Superposition plein écran qui joue un effet puis appelle [onFinished].
 * [text] sert aux effets qui réutilisent le message (Écho).
 */
@Composable
fun ScreenEffectOverlay(effect: MessageEffect, text: String, onFinished: () -> Unit) {
    val progress = remember(effect) { Animatable(0f) }
    val duration = when (effect) {
        MessageEffect.FIREWORKS, MessageEffect.LASERS, MessageEffect.SNOW -> 4200
        else -> 3400
    }
    LaunchedEffect(effect) {
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
        onFinished()
    }
    val seed = remember(effect) { Random.nextInt() }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            val rnd = Random(seed)
            when (effect) {
                MessageEffect.CONFETTI -> confetti(t, rnd)
                MessageEffect.BALLOONS -> balloons(t, rnd)
                MessageEffect.LOVE -> love(t, rnd)
                MessageEffect.FIREWORKS -> fireworks(t, rnd)
                MessageEffect.LASERS -> lasers(t)
                MessageEffect.CELEBRATION -> celebration(t, rnd)
                MessageEffect.SPOTLIGHT -> spotlight(t)
                MessageEffect.ECHO -> echo(t, rnd)
                MessageEffect.SNOW -> snow(t, rnd)
                MessageEffect.EMOJI_RAIN -> emojiRain(t, rnd)
                MessageEffect.RAINBOW -> rainbow(t)
                MessageEffect.MONEY -> money(t, rnd)
                MessageEffect.SHOOTING_STARS -> shootingStars(t, rnd)
                else -> Unit
            }
        }
    }
}

/** Fondu d'entrée/sortie commun. */
private fun fade(t: Float, inEnd: Float = 0.1f, outStart: Float = 0.8f): Float = when {
    t < inEnd -> t / inEnd
    t > outStart -> ((1f - t) / (1f - outStart)).coerceIn(0f, 1f)
    else -> 1f
}

private fun DrawScope.confetti(t: Float, rnd: Random) {
    val w = size.width
    val h = size.height
    repeat(160) {
        val x0 = rnd.nextFloat() * w
        val delay = rnd.nextFloat() * 0.35f
        val speed = 0.8f + rnd.nextFloat() * 0.8f
        val sway = 20f + rnd.nextFloat() * 60f
        val color = Festive[rnd.nextInt(Festive.size)]
        val spin = rnd.nextFloat() * 720f
        val local = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (local <= 0f) return@repeat
        val y = -40f + local * speed * (h + 80f)
        val x = x0 + sin(local * 10f + x0) * sway
        rotate(spin * local, Offset(x, y)) {
            drawRect(color.copy(alpha = fade(t, 0.02f, 0.85f)), Offset(x - 8f, y - 4f), Size(16f, 8f))
        }
    }
}

private fun DrawScope.balloons(t: Float, rnd: Random) {
    val w = size.width
    val h = size.height
    repeat(14) {
        val x0 = rnd.nextFloat() * w
        val delay = rnd.nextFloat() * 0.3f
        val r = 45f + rnd.nextFloat() * 35f
        val color = Festive[rnd.nextInt(Festive.size - 1)]
        val local = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (local <= 0f) return@repeat
        val y = h + r * 2 - local * (h + r * 5)
        val x = x0 + sin(local * 6f + it) * 30f
        val string = Path().apply {
            moveTo(x, y + r * 1.15f)
            quadraticTo(x - 12f, y + r * 1.8f, x + 6f, y + r * 2.6f)
        }
        drawPath(string, Color.Gray, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
        drawOval(color, Offset(x - r, y - r * 1.15f), Size(r * 2, r * 2.3f))
        drawOval(Color.White.copy(alpha = 0.35f), Offset(x - r * 0.55f, y - r * 0.85f), Size(r * 0.5f, r * 0.7f))
        val knot = Path().apply {
            moveTo(x, y + r * 1.1f); lineTo(x - 7f, y + r * 1.3f); lineTo(x + 7f, y + r * 1.3f); close()
        }
        drawPath(knot, color)
    }
}

private fun DrawScope.love(t: Float, rnd: Random) {
    val center = Offset(size.width / 2, size.height / 2)
    val beat = 1f + 0.08f * sin(t * 2 * PI.toFloat() * 4)
    val scale = (t / 0.25f).coerceAtMost(1f) * beat
    val alpha = fade(t, 0.05f, 0.8f)
    drawHeart(center, size.minDimension * 0.28f * scale, Color(0xFFFF4D6D).copy(alpha = alpha))
    repeat(18) {
        val angle = rnd.nextFloat() * 2 * PI.toFloat()
        val dist = t * size.minDimension * (0.5f + rnd.nextFloat() * 0.4f)
        val p = center + Offset(cos(angle) * dist, sin(angle) * dist)
        drawCircle(Color(0xFFFFA0B5).copy(alpha = alpha), 5f, p)
    }
}

private fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * 0.35f)
        cubicTo(c.x - s * 1.1f, c.y - s * 0.3f, c.x - s * 0.5f, c.y - s * 1.0f, c.x, c.y - s * 0.45f)
        cubicTo(c.x + s * 0.5f, c.y - s * 1.0f, c.x + s * 1.1f, c.y - s * 0.3f, c.x, c.y + s * 0.35f)
        close()
    }
    translate(0f, s * 0.2f) { drawPath(path, color) }
}

private fun DrawScope.fireworks(t: Float, rnd: Random) {
    drawRect(Color.Black.copy(alpha = 0.75f * fade(t, 0.08f, 0.85f)))
    repeat(6) { i ->
        val start = i * 0.12f
        val local = ((t - start) / 0.35f)
        val cx = size.width * (0.15f + rnd.nextFloat() * 0.7f)
        val cy = size.height * (0.15f + rnd.nextFloat() * 0.45f)
        val color = Festive[rnd.nextInt(Festive.size - 1)]
        if (local !in 0f..1f) return@repeat
        if (local < 0.3f) {
            // Fusée qui monte
            val k = local / 0.3f
            val y = size.height - (size.height - cy) * k
            drawCircle(Color(0xFFFFF59D), 4f, Offset(cx, y))
        } else {
            val k = (local - 0.3f) / 0.7f
            val rays = 28
            repeat(rays) { r ->
                val a = r * 2 * PI.toFloat() / rays
                val d = k * size.minDimension * 0.28f
                val p = Offset(cx + cos(a) * d, cy + sin(a) * d + k * k * 60f)
                drawCircle(color.copy(alpha = 1f - k), 5f * (1f - k * 0.5f), p)
                drawCircle(Color.White.copy(alpha = (1f - k) * 0.6f), 2f, p)
            }
        }
    }
}

private fun DrawScope.lasers(t: Float) {
    val alpha = fade(t, 0.08f, 0.85f)
    drawRect(Color.Black.copy(alpha = 0.8f * alpha))
    val colors = listOf(Color(0xFF00E5FF), Color(0xFFFF1744), Color(0xFF76FF03), Color(0xFFE040FB))
    val origin = Offset(size.width / 2, size.height * 0.45f)
    repeat(12) { i ->
        val a = (t * 6f + i * (2 * PI.toFloat() / 12)) % (2 * PI.toFloat())
        val len = size.maxDimension
        val end = origin + Offset(cos(a) * len, sin(a) * len)
        val c = colors[i % colors.size]
        drawLine(c.copy(alpha = 0.35f * alpha), origin, end, strokeWidth = 18f, cap = StrokeCap.Round)
        drawLine(c.copy(alpha = alpha), origin, end, strokeWidth = 4f, cap = StrokeCap.Round)
    }
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), origin, 90f),
        90f, origin,
    )
}

private fun DrawScope.celebration(t: Float, rnd: Random) {
    // Pluie d'étincelles dorées depuis le coin supérieur droit.
    val alpha = fade(t, 0.05f, 0.8f)
    drawRect(Color.Black.copy(alpha = 0.35f * alpha))
    val origin = Offset(size.width, 0f)
    repeat(140) {
        val delay = rnd.nextFloat() * 0.5f
        val local = ((t - delay) / 0.5f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@repeat
        val a = (PI / 2 + rnd.nextFloat() * PI / 2).toFloat()
        val speed = size.maxDimension * (0.4f + rnd.nextFloat() * 0.8f)
        val p = origin + Offset(cos(a) * speed * local, sin(a) * speed * local + local * local * 300f)
        val twinkle = 0.5f + 0.5f * sin(local * 40f + it)
        drawStar(p, 6f + rnd.nextFloat() * 6f, Color(0xFFFFD54F).copy(alpha = (1f - local) * twinkle))
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + r * 0.25f, c.y - r * 0.25f)
        lineTo(c.x + r, c.y)
        lineTo(c.x + r * 0.25f, c.y + r * 0.25f)
        lineTo(c.x, c.y + r)
        lineTo(c.x - r * 0.25f, c.y + r * 0.25f)
        lineTo(c.x - r, c.y)
        lineTo(c.x - r * 0.25f, c.y - r * 0.25f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.spotlight(t: Float) {
    val alpha = fade(t, 0.1f, 0.8f)
    // Le faisceau balaie puis se pose sur la dernière bulle (bas droite).
    val settle = (t / 0.4f).coerceAtMost(1f)
    val target = Offset(size.width * 0.72f, size.height * 0.82f)
    val sweep = Offset(size.width * (0.2f + 0.5f * sin(t * 8f)), size.height * 0.4f)
    val center = sweep + (target - sweep) * settle
    drawRect(Color.Black.copy(alpha = 0.85f * alpha))
    drawCircle(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
            center,
            260f,
        ),
        260f,
        center,
    )
}

private fun DrawScope.echo(t: Float, rnd: Random) {
    repeat(26) {
        val delay = rnd.nextFloat() * 0.6f
        val local = ((t - delay) / 0.45f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@repeat
        val y = size.height * rnd.nextFloat()
        val fromLeft = rnd.nextBoolean()
        val x = if (fromLeft) -80f + local * (size.width + 160f) else size.width + 80f - local * (size.width + 160f)
        val radius = 12f + rnd.nextFloat() * 24f
        val color = Festive[rnd.nextInt(Festive.size - 1)]
        drawCircle(color.copy(alpha = 0.85f * fade(t)), radius, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.25f * fade(t)), radius * 0.35f, Offset(x - radius * 0.3f, y - radius * 0.3f))
    }
}

private fun DrawScope.snow(t: Float, rnd: Random) {
    val alpha = fade(t, 0.1f, 0.85f)
    drawRect(Color(0xFF0D1B2A).copy(alpha = 0.35f * alpha))
    repeat(150) {
        val x0 = rnd.nextFloat() * size.width
        val speed = 0.4f + rnd.nextFloat() * 0.8f
        val r = 2f + rnd.nextFloat() * 5f
        val offset = rnd.nextFloat()
        val y = ((offset + t * speed) % 1f) * size.height
        val x = x0 + sin(t * 8f + it) * 18f
        drawCircle(Color.White.copy(alpha = alpha * 0.9f), r, Offset(x, y))
    }
}

private fun DrawScope.rainbow(t: Float) {
    val alpha = fade(t, 0.12f, 0.75f)
    val colors = listOf(
        Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676),
        Color(0xFF2979FF), Color(0xFF651FFF), Color(0xFFD500F9),
    )
    val center = Offset(size.width / 2, size.height * 0.75f)
    val grow = (t / 0.4f).coerceAtMost(1f)
    val band = size.minDimension * 0.05f
    colors.forEachIndexed { i, c ->
        val r = (size.minDimension * 0.75f - i * band) * grow
        if (r <= 0f) return@forEachIndexed
        drawArc(
            c.copy(alpha = 0.85f * alpha), 180f, 180f, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(band),
        )
    }
    // Petits nuages aux extrémités
    val cloud = Color.White.copy(alpha = alpha)
    listOf(center.x - size.minDimension * 0.62f, center.x + size.minDimension * 0.62f).forEach { cx ->
        drawCircle(cloud, 40f * grow, Offset(cx, center.y))
        drawCircle(cloud, 30f * grow, Offset(cx - 38f, center.y + 10f))
        drawCircle(cloud, 30f * grow, Offset(cx + 38f, center.y + 10f))
    }
}

private fun DrawScope.money(t: Float, rnd: Random) {
    val alpha = fade(t, 0.02f, 0.85f)
    repeat(45) {
        val x0 = rnd.nextFloat() * size.width
        val delay = rnd.nextFloat() * 0.45f
        val local = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (local <= 0f) return@repeat
        val y = -60f + local * (size.height + 120f) * (0.6f + rnd.nextFloat() * 0.5f)
        val x = x0 + sin(local * 9f + it) * 50f
        val w = 32f + rnd.nextFloat() * 12f
        rotate(sin(local * 8f + it) * 40f, Offset(x, y)) {
            drawRoundRect(Color(0xFF8BC34A).copy(alpha = alpha), Offset(x - w, y - w * 0.55f), Size(w * 2f, w * 1.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
            drawCircle(Color(0xFFFFD54F).copy(alpha = alpha), w * 0.28f, Offset(x, y))
        }
    }
}

private fun DrawScope.shootingStars(t: Float, rnd: Random) {
    val alpha = fade(t, 0.08f, 0.85f)
    drawRect(Color(0xFF0B1026).copy(alpha = 0.8f * alpha))
    // Ciel étoilé
    repeat(120) {
        val p = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        val tw = 0.4f + 0.6f * sin(t * 20f + it)
        drawCircle(Color.White.copy(alpha = alpha * tw.coerceIn(0f, 1f)), 1.5f + rnd.nextFloat() * 1.5f, p)
    }
    // Étoiles filantes
    repeat(7) {
        val start = rnd.nextFloat() * 0.6f
        val local = ((t - start) / 0.3f)
        if (local !in 0f..1f) return@repeat
        val from = Offset(size.width * (0.3f + rnd.nextFloat() * 0.9f), size.height * rnd.nextFloat() * 0.4f)
        val dir = Offset(-1f, 0.55f)
        val len = size.maxDimension * 0.6f
        val head = from + dir * (len * local)
        val tail = head - dir * 220f
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = alpha)), tail, head),
            tail, head, strokeWidth = 4f, cap = StrokeCap.Round,
        )
        drawCircle(Color.White.copy(alpha = alpha), 5f, head)
    }
}

@Composable
fun EffectBadge(effect: MessageEffect) {
    Text("${effect.emoji} Envoyé avec « ${effect.label} »", fontSize = 11.sp, color = Color.Gray)
}
