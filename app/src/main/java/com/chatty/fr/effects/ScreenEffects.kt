package com.chatty.fr.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val Festive = listOf(
    Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF),
    Color(0xFFE040FB), Color(0xFFFF6E40), Color(0xFFFFFFFF),
)

/*
 * Effets plein écran façon iMessage.
 *
 * Chaque effet reprend exactement la géométrie, les quantités, les trajectoires et le timing
 * de la version d'origine (dessinée sur Canvas). Seul le rendu change : plus de Canvas,
 * DrawScope, drawText, BlendMode ni CompositingStrategy.Offscreen, qui faisaient planter
 * l'appli sur certains téléphones. Chaque particule est un Box/Text placé et animé par
 * graphicsLayer (translation, rotation, échelle, transparence), lu à chaque image sans
 * recomposition.
 */

/**
 * Superposition plein écran qui joue un effet puis appelle [onFinished].
 * [text] sert aux effets qui réutilisent le message (Écho, Pluie d'emojis).
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
    val t: () -> Float = { progress.value }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f) return@BoxWithConstraints
        val px = Px(LocalDensity.current.density)
        when (effect) {
            MessageEffect.CONFETTI -> Confetti(t, w, h, seed, px)
            MessageEffect.BALLOONS -> Balloons(t, w, h, seed, px)
            MessageEffect.LOVE -> Love(t, w, h, seed, px)
            MessageEffect.FIREWORKS -> Fireworks(t, w, h, seed, px)
            MessageEffect.LASERS -> Lasers(t, w, h, px)
            MessageEffect.CELEBRATION -> Celebration(t, w, h, seed, px)
            MessageEffect.SPOTLIGHT -> Spotlight(progress.value, w, h)
            MessageEffect.ECHO -> Echo(t, w, h, seed, text)
            MessageEffect.SNOW -> Snow(t, w, h, seed, px)
            MessageEffect.EMOJI_RAIN -> EmojiRain(t, w, h, seed, text)
            MessageEffect.RAINBOW -> Rainbow(progress.value, w, h, px)
            MessageEffect.MONEY -> Money(t, w, h, seed)
            MessageEffect.SHOOTING_STARS -> ShootingStars(t, w, h, seed, px)
            else -> Unit
        }
    }
}

/** Conversion pixels → dp (toute la géométrie est calculée en pixels, comme sur le Canvas d'origine). */
private class Px(val density: Float) {
    fun dp(px: Float): Dp = (px / density).dp
}

/** Fondu d'entrée/sortie commun. */
private fun fade(t: Float, inEnd: Float = 0.1f, outStart: Float = 0.8f): Float = when {
    t < inEnd -> t / inEnd
    t > outStart -> ((1f - t) / (1f - outStart)).coerceIn(0f, 1f)
    else -> 1f
}

/** Voile plein écran dont l'opacité suit [alpha]. */
@Composable
private fun Scrim(color: Color, alpha: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .background(color)
    )
}

/** Particule circulaire de rayon [radius] px. */
@Composable
private fun Dot(radius: Float, color: Color, px: Px, layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit) {
    Box(
        Modifier
            .size(px.dp(radius * 2))
            .graphicsLayer(layer)
            .background(color, CircleShape)
    )
}

/** Segment fixe de (x1,y1) à (x2,y2), épaisseur en px (remplace drawLine/drawPath). */
@Composable
private fun Segment(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Color, px: Px) {
    val dx = x2 - x1
    val dy = y2 - y1
    val length = sqrt(dx * dx + dy * dy)
    val angle = (atan2(dy, dx) * 180f / PI).toFloat()
    Box(
        Modifier
            .size(px.dp(length), px.dp(thickness))
            .graphicsLayer {
                translationX = x1
                translationY = y1 - thickness / 2f
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationZ = angle
            }
            .background(color, RoundedCornerShape(50))
    )
}

// ------------------------------------------------------------------ Confettis

private class ConfettiPiece(val x0: Float, val delay: Float, val speed: Float, val sway: Float, val color: Color, val spin: Float)

@Composable
private fun Confetti(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    val pieces = remember(seed, w, h) {
        val rnd = Random(seed)
        List(160) {
            ConfettiPiece(
                x0 = rnd.nextFloat() * w,
                delay = rnd.nextFloat() * 0.35f,
                speed = 0.8f + rnd.nextFloat() * 0.8f,
                sway = 20f + rnd.nextFloat() * 60f,
                color = Festive[rnd.nextInt(Festive.size)],
                spin = rnd.nextFloat() * 720f,
            )
        }
    }
    pieces.forEach { p ->
        Box(
            Modifier
                .size(px.dp(16f), px.dp(8f))
                .graphicsLayer {
                    val tt = t()
                    val local = ((tt - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (local <= 0f) { alpha = 0f; return@graphicsLayer }
                    val y = -40f + local * p.speed * (h + 80f)
                    val x = p.x0 + sin(local * 10f + p.x0) * p.sway
                    translationX = x - 8f
                    translationY = y - 4f
                    rotationZ = p.spin * local
                    alpha = fade(tt, 0.02f, 0.85f)
                }
                .background(p.color)
        )
    }
}

// ------------------------------------------------------------------ Ballons

private class BalloonSpec(val x0: Float, val delay: Float, val r: Float, val color: Color, val index: Int)

@Composable
private fun Balloons(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    val balloons = remember(seed, w, h) {
        val rnd = Random(seed)
        List(14) { i ->
            BalloonSpec(
                x0 = rnd.nextFloat() * w,
                delay = rnd.nextFloat() * 0.3f,
                r = 45f + rnd.nextFloat() * 35f,
                color = Festive[rnd.nextInt(Festive.size - 1)],
                index = i,
            )
        }
    }
    balloons.forEach { b ->
        val r = b.r
        // Groupe (ballon + reflet + nœud + ficelle) : de y-1.15r à y+2.6r autour du centre (x, y).
        Box(
            Modifier
                .size(px.dp(r * 2), px.dp(r * 3.75f))
                .graphicsLayer {
                    val local = ((t() - b.delay) / (1f - b.delay)).coerceIn(0f, 1f)
                    if (local <= 0f) { alpha = 0f; return@graphicsLayer }
                    val y = h + r * 2 - local * (h + r * 5)
                    val x = b.x0 + sin(local * 6f + b.index) * 30f
                    translationX = x - r
                    translationY = y - r * 1.15f
                }
        ) {
            // Ficelle : courbe de (r, 2.3r) vers (r+6, 3.75r) en passant à gauche, en deux segments.
            Segment(r, r * 2.3f, r - 8f, r * 2.95f, 2.5f, Color.Gray, px)
            Segment(r - 8f, r * 2.95f, r + 6f, r * 3.75f, 2.5f, Color.Gray, px)
            // Nœud
            Box(
                Modifier
                    .size(px.dp(12f))
                    .graphicsLayer {
                        translationX = r - 6f
                        translationY = r * 2.3f - 8f
                        rotationZ = 45f
                    }
                    .background(b.color)
            )
            // Ballon
            Box(Modifier.size(px.dp(r * 2), px.dp(r * 2.3f)).background(b.color, RoundedCornerShape(50)))
            // Reflet
            Box(
                Modifier
                    .size(px.dp(r * 0.5f), px.dp(r * 0.7f))
                    .graphicsLayer {
                        translationX = r * 0.45f
                        translationY = r * 0.3f
                    }
                    .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
            )
        }
    }
}

// ------------------------------------------------------------------ Amour

/** Cœur construit avec un carré pivoté et deux disques (taille [size] px). */
@Composable
private fun Heart(size: Float, color: Color, px: Px, modifier: Modifier = Modifier) {
    val side = size * 0.6f
    val lobe = size * 0.212f
    Box(modifier.size(px.dp(size))) {
        Box(
            Modifier
                .size(px.dp(side))
                .graphicsLayer {
                    translationX = size / 2 - side / 2
                    translationY = size * 0.58f - side / 2
                    rotationZ = 45f
                }
                .background(color)
        )
        listOf(-lobe, lobe).forEach { dx ->
            Box(
                Modifier
                    .size(px.dp(side))
                    .graphicsLayer {
                        translationX = size / 2 + dx - side / 2
                        translationY = size * 0.58f - lobe - side / 2
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

private class LoveSpark(val angle: Float, val distFactor: Float)

@Composable
private fun Love(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    // Un grand cœur qui grossit et bat au centre, entouré de petits cœurs.
    val cx = w / 2
    val cy = h / 2
    val minDim = minOf(w, h)
    val heartSize = minDim * 0.28f * 1.5f
    Heart(
        heartSize, Color(0xFFFF4D6D), px,
        Modifier.graphicsLayer {
            val tt = t()
            val beat = 1f + 0.08f * sin(tt * 2 * PI.toFloat() * 4)
            val s = (tt / 0.25f).coerceAtMost(1f) * beat
            translationX = cx - heartSize / 2
            translationY = cy - heartSize / 2 + minDim * 0.28f * 0.2f * s
            scaleX = s
            scaleY = s
            alpha = fade(tt, 0.05f, 0.8f)
        },
    )
    val sparks = remember(seed) {
        val rnd = Random(seed)
        List(18) { LoveSpark(rnd.nextFloat() * 2 * PI.toFloat(), 0.5f + rnd.nextFloat() * 0.4f) }
    }
    val box = 34.dp
    val half = box.value * px.density / 2
    sparks.forEach { s ->
        Box(
            Modifier
                .size(box)
                .graphicsLayer {
                    val tt = t()
                    val dist = tt * minDim * s.distFactor
                    translationX = cx + cos(s.angle) * dist - half
                    translationY = cy + sin(s.angle) * dist - half
                    alpha = fade(tt, 0.05f, 0.8f)
                },
            contentAlignment = Alignment.Center,
        ) { Text("💕", fontSize = 22.sp) }
    }
}

// ------------------------------------------------------------------ Feux d'artifice

private class Firework(val start: Float, val cx: Float, val cy: Float, val color: Color)

@Composable
private fun Fireworks(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    Scrim(Color.Black) { 0.75f * fade(t(), 0.08f, 0.85f) }
    val fireworks = remember(seed, w, h) {
        val rnd = Random(seed)
        List(6) { i ->
            Firework(
                start = i * 0.12f,
                cx = w * (0.15f + rnd.nextFloat() * 0.7f),
                cy = h * (0.15f + rnd.nextFloat() * 0.45f),
                color = Festive[rnd.nextInt(Festive.size - 1)],
            )
        }
    }
    val minDim = minOf(w, h)
    val rays = 28
    fireworks.forEach { f ->
        // Fusée qui monte
        Dot(4f, Color(0xFFFFF59D), px) {
            val local = (t() - f.start) / 0.35f
            if (local !in 0f..1f || local >= 0.3f) { alpha = 0f; return@Dot }
            val k = local / 0.3f
            alpha = 1f
            translationX = f.cx - 4f
            translationY = h - (h - f.cy) * k - 4f
        }
        // Gerbe : 28 rayons qui s'écartent et retombent
        repeat(rays) { r ->
            val a = r * 2 * PI.toFloat() / rays
            Box(
                Modifier
                    .size(px.dp(10f))
                    .graphicsLayer {
                        val local = (t() - f.start) / 0.35f
                        if (local !in 0f..1f || local < 0.3f) { alpha = 0f; return@graphicsLayer }
                        val k = (local - 0.3f) / 0.7f
                        val d = k * minDim * 0.28f
                        translationX = f.cx + cos(a) * d - 5f
                        translationY = f.cy + sin(a) * d + k * k * 60f - 5f
                        val s = 1f - k * 0.5f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - k
                    }
                    .background(f.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(px.dp(4f)).background(Color.White.copy(alpha = 0.6f), CircleShape))
            }
        }
    }
}

// ------------------------------------------------------------------ Lasers

@Composable
private fun Lasers(t: () -> Float, w: Float, h: Float, px: Px) {
    Scrim(Color.Black) { 0.8f * fade(t(), 0.08f, 0.85f) }
    val colors = listOf(Color(0xFF00E5FF), Color(0xFFFF1744), Color(0xFF76FF03), Color(0xFFE040FB))
    val ox = w / 2
    val oy = h * 0.45f
    val len = maxOf(w, h)
    repeat(12) { i ->
        val c = colors[i % colors.size]
        // Halo (18 px) puis faisceau (4 px), qui tournent autour du centre.
        listOf(18f to 0.35f, 4f to 1f).forEach { (thickness, strength) ->
            Box(
                Modifier
                    .size(px.dp(len), px.dp(thickness))
                    .graphicsLayer {
                        val tt = t()
                        val a = (tt * 6f + i * (2 * PI.toFloat() / 12)) % (2 * PI.toFloat())
                        translationX = ox
                        translationY = oy - thickness / 2
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationZ = a * 180f / PI.toFloat()
                        alpha = strength * fade(tt, 0.08f, 0.85f)
                    }
                    .background(c, RoundedCornerShape(50))
            )
        }
    }
    // Source lumineuse au centre
    Box(
        Modifier
            .size(px.dp(180f))
            .graphicsLayer {
                translationX = ox - 90f
                translationY = oy - 90f
                alpha = fade(t(), 0.08f, 0.85f)
            }
            .background(Brush.radialGradient(listOf(Color.White, Color.Transparent)), CircleShape)
    )
}

// ------------------------------------------------------------------ Célébration

private class Sparkle(val delay: Float, val angle: Float, val speed: Float, val r: Float, val index: Int)

@Composable
private fun Celebration(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    // Pluie d'étincelles dorées depuis le coin supérieur droit.
    Scrim(Color.Black) { 0.35f * fade(t(), 0.05f, 0.8f) }
    val maxDim = maxOf(w, h)
    val sparkles = remember(seed, w, h) {
        val rnd = Random(seed)
        List(140) { i ->
            Sparkle(
                delay = rnd.nextFloat() * 0.5f,
                angle = (PI / 2 + rnd.nextFloat() * PI / 2).toFloat(),
                speed = maxDim * (0.4f + rnd.nextFloat() * 0.8f),
                r = 6f + rnd.nextFloat() * 6f,
                index = i,
            )
        }
    }
    sparkles.forEach { s ->
        val boxPx = s.r * 2.6f
        Box(
            Modifier
                .size(px.dp(boxPx))
                .graphicsLayer {
                    val local = ((t() - s.delay) / 0.5f).coerceIn(0f, 1f)
                    if (local <= 0f || local >= 1f) { alpha = 0f; return@graphicsLayer }
                    val twinkle = 0.5f + 0.5f * sin(local * 40f + s.index)
                    translationX = w + cos(s.angle) * s.speed * local - boxPx / 2
                    translationY = sin(s.angle) * s.speed * local + local * local * 300f - boxPx / 2
                    alpha = ((1f - local) * twinkle).coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", color = Color(0xFFFFD54F), fontSize = (s.r * 2.2f / px.density).sp, textAlign = TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------ Projecteur

@Composable
private fun Spotlight(t: Float, w: Float, h: Float) {
    val alpha = fade(t, 0.1f, 0.8f)
    // Le faisceau balaie puis se pose sur la dernière bulle (bas droite).
    val settle = (t / 0.4f).coerceAtMost(1f)
    val target = Offset(w * 0.72f, h * 0.82f)
    val sweep = Offset(w * (0.2f + 0.5f * sin(t * 8f)), h * 0.4f)
    val center = sweep + (target - sweep) * settle
    val dark = Color.Black.copy(alpha = 0.85f * alpha)
    // Voile sombre percé d'un trou lumineux au bord adouci : le dégradé radial est transparent
    // au centre puis opaque au-delà du rayon (même rendu que l'ancien BlendMode.DstOut).
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to dark,
                    center = center,
                    radius = 260f,
                )
            )
    )
}

// ------------------------------------------------------------------ Écho

private class EchoCopy(val delay: Float, val y: Float, val fromLeft: Boolean, val color: Color, val sizeSp: Int)

@Composable
private fun Echo(t: () -> Float, w: Float, h: Float, seed: Int, text: String) {
    val label = text.take(40).ifBlank { "👋" }
    val copies = remember(seed, w, h) {
        val rnd = Random(seed)
        List(26) {
            EchoCopy(
                delay = rnd.nextFloat() * 0.6f,
                y = h * rnd.nextFloat(),
                fromLeft = rnd.nextBoolean(),
                color = Festive[rnd.nextInt(Festive.size - 1)],
                sizeSp = 14 + rnd.nextInt(14),
            )
        }
    }
    copies.forEach { c ->
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    val local = ((t() - c.delay) / 0.45f).coerceIn(0f, 1f)
                    if (local <= 0f || local >= 1f) { alpha = 0f; return@graphicsLayer }
                    alpha = 1f
                    translationX = if (c.fromLeft) -300f + local * (w + 400f) else w + 100f - local * (w + 400f)
                    translationY = c.y
                }
        ) {
            Text(label, color = c.color.copy(alpha = 0.9f), fontSize = c.sizeSp.sp, maxLines = 1, softWrap = false)
        }
    }
}

// ------------------------------------------------------------------ Neige

private class Flake(val x0: Float, val speed: Float, val r: Float, val offset: Float, val index: Int)

@Composable
private fun Snow(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    Scrim(Color(0xFF0D1B2A)) { 0.35f * fade(t(), 0.1f, 0.85f) }
    val flakes = remember(seed, w, h) {
        val rnd = Random(seed)
        List(150) { i ->
            Flake(
                x0 = rnd.nextFloat() * w,
                speed = 0.4f + rnd.nextFloat() * 0.8f,
                r = 2f + rnd.nextFloat() * 5f,
                offset = rnd.nextFloat(),
                index = i,
            )
        }
    }
    flakes.forEach { f ->
        Dot(f.r, Color.White, px) {
            val tt = t()
            val y = ((f.offset + tt * f.speed) % 1f) * h
            val x = f.x0 + sin(tt * 8f + f.index) * 18f
            translationX = x - f.r
            translationY = y - f.r
            alpha = fade(tt, 0.1f, 0.85f) * 0.9f
        }
    }
}

// ------------------------------------------------------------------ Pluie d'emojis

/** Emojis présents dans le texte (ou 🎉 par défaut). */
private fun emojisOf(text: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (Character.getType(cp) == Character.OTHER_SYMBOL.toInt()) out += String(Character.toChars(cp))
        i += Character.charCount(cp)
    }
    return out.distinct().ifEmpty { listOf("🎉", "😄", "✨") }
}

private class FallingGlyph(val x: Float, val delay: Float, val speed: Float, val glyph: String, val sizeSp: Int, val index: Int)

@Composable
private fun EmojiRain(t: () -> Float, w: Float, h: Float, seed: Int, text: String) {
    val drops = remember(seed, w, h, text) {
        val emojis = emojisOf(text)
        val rnd = Random(seed)
        List(60) { i ->
            FallingGlyph(
                x = rnd.nextFloat() * w,
                delay = rnd.nextFloat() * 0.4f,
                speed = 0.7f + rnd.nextFloat() * 0.7f,
                glyph = emojis[rnd.nextInt(emojis.size)],
                sizeSp = 22 + rnd.nextInt(20),
                index = i,
            )
        }
    }
    drops.forEach { d ->
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    val tt = t()
                    val local = ((tt - d.delay) / (1f - d.delay)).coerceIn(0f, 1f)
                    if (local <= 0f) { alpha = 0f; return@graphicsLayer }
                    translationX = d.x
                    translationY = -80f + local * d.speed * (h + 160f)
                    rotationZ = sin(local * 6f + d.index) * 25f
                    alpha = fade(tt, 0.02f, 0.85f)
                }
        ) { Text(d.glyph, fontSize = d.sizeSp.sp) }
    }
}

// ------------------------------------------------------------------ Arc-en-ciel

@Composable
private fun Rainbow(t: Float, w: Float, h: Float, px: Px) {
    val alpha = fade(t, 0.12f, 0.75f)
    val colors = listOf(
        Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676),
        Color(0xFF2979FF), Color(0xFF651FFF), Color(0xFFD500F9),
    )
    val cx = w / 2
    val cy = h * 0.75f
    val grow = (t / 0.4f).coerceAtMost(1f)
    val minDim = minOf(w, h)
    val band = minDim * 0.05f
    colors.forEachIndexed { i, c ->
        val r = (minDim * 0.75f - i * band) * grow
        if (r <= 0f) return@forEachIndexed
        // Demi-anneau : cercle avec bordure, dont on ne garde que la moitié haute.
        val outer = r + band / 2
        Box(
            Modifier
                .size(px.dp(outer * 2), px.dp(outer))
                .graphicsLayer {
                    translationX = cx - outer
                    translationY = cy - outer
                    this.alpha = 0.85f * alpha
                }
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(px.dp(outer * 2))
                    .border(px.dp(band), c, CircleShape)
            )
        }
    }
    // Petits nuages aux extrémités
    listOf(cx - minDim * 0.62f, cx + minDim * 0.62f).forEach { x ->
        listOf(Triple(0f, 0f, 40f), Triple(-38f, 10f, 30f), Triple(38f, 10f, 30f)).forEach { (dx, dy, base) ->
            val r = base * grow
            if (r > 0f) Dot(r, Color.White, px) {
                translationX = x + dx - r
                translationY = cy + dy - r
                this.alpha = alpha
            }
        }
    }
}

// ------------------------------------------------------------------ Pluie de billets

private class Bill(val x0: Float, val delay: Float, val speed: Float, val symbol: String, val index: Int)

@Composable
private fun Money(t: () -> Float, w: Float, h: Float, seed: Int) {
    val bills = remember(seed, w, h) {
        val rnd = Random(seed)
        List(45) { i ->
            Bill(
                x0 = rnd.nextFloat() * w,
                delay = rnd.nextFloat() * 0.45f,
                speed = 0.6f + rnd.nextFloat() * 0.5f,
                symbol = if (rnd.nextInt(4) == 0) "🪙" else "💵",
                index = i,
            )
        }
    }
    bills.forEach { b ->
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    val tt = t()
                    val local = ((tt - b.delay) / (1f - b.delay)).coerceIn(0f, 1f)
                    if (local <= 0f) { alpha = 0f; return@graphicsLayer }
                    translationX = b.x0 + sin(local * 9f + b.index) * 50f
                    translationY = -60f + local * (h + 120f) * b.speed
                    rotationZ = sin(local * 8f + b.index) * 40f
                    alpha = fade(tt, 0.02f, 0.85f)
                }
        ) { Text(b.symbol, fontSize = 30.sp) }
    }
}

// ------------------------------------------------------------------ Étoiles filantes

private class SkyStar(val x: Float, val y: Float, val r: Float, val index: Int)
private class Comet(val start: Float, val fromX: Float, val fromY: Float)

@Composable
private fun ShootingStars(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    Scrim(Color(0xFF0B1026)) { 0.8f * fade(t(), 0.08f, 0.85f) }
    val rnd = remember(seed) { Random(seed) }
    // Ciel étoilé qui scintille
    val stars = remember(seed, w, h) {
        List(120) { i -> SkyStar(rnd.nextFloat() * w, rnd.nextFloat() * h, 1.5f + rnd.nextFloat() * 1.5f, i) }
    }
    stars.forEach { s ->
        Dot(s.r, Color.White, px) {
            val tt = t()
            val tw = (0.4f + 0.6f * sin(tt * 20f + s.index)).coerceIn(0f, 1f)
            translationX = s.x - s.r
            translationY = s.y - s.r
            alpha = fade(tt, 0.08f, 0.85f) * tw
        }
    }
    // Étoiles filantes : tête lumineuse + traînée en dégradé
    val comets = remember(seed, w, h) {
        List(7) { Comet(rnd.nextFloat() * 0.6f, w * (0.3f + rnd.nextFloat() * 0.9f), h * rnd.nextFloat() * 0.4f) }
    }
    val dirX = -1f
    val dirY = 0.55f
    val tailAngle = (atan2(dirY, dirX) * 180f / PI).toFloat()
    val len = maxOf(w, h) * 0.6f
    val tail = 220f
    comets.forEach { c ->
        Box(
            Modifier
                .size(px.dp(tail), px.dp(4f))
                .graphicsLayer {
                    val tt = t()
                    val local = (tt - c.start) / 0.3f
                    if (local !in 0f..1f) { alpha = 0f; return@graphicsLayer }
                    val headX = c.fromX + dirX * len * local
                    val headY = c.fromY + dirY * len * local
                    // Le bord droit du segment est la tête ; en tournant autour de ce point,
                    // la traînée part à l'opposé du mouvement (vers le haut à droite).
                    translationX = headX - tail
                    translationY = headY - 2f
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    rotationZ = tailAngle
                    alpha = fade(tt, 0.08f, 0.85f)
                }
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White)), RoundedCornerShape(50))
        )
        Dot(5f, Color.White, px) {
            val tt = t()
            val local = (tt - c.start) / 0.3f
            if (local !in 0f..1f) { alpha = 0f; return@Dot }
            translationX = c.fromX + dirX * len * local - 5f
            translationY = c.fromY + dirY * len * local - 5f
            alpha = fade(tt, 0.08f, 0.85f)
        }
    }
}

@Composable
fun EffectBadge(effect: MessageEffect) {
    Text("${effect.emoji} Envoyé avec « ${effect.label} »", fontSize = 11.sp, color = Color.Gray)
}
