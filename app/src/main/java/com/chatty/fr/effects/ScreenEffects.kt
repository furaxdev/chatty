package com.chatty.fr.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val Festive = listOf(
    Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF),
    Color(0xFFE040FB), Color(0xFFFF6E40), Color(0xFFFFFFFF),
)

private const val TAU = (2 * PI).toFloat()

/*
 * Effets plein écran façon iMessage, rendus avec des composants Compose stables.
 *
 * Mêmes effets que la version d'origine (efeb453), avec plus de réalisme : profondeur
 * (particules proches plus grandes, rapides et nettes), physique (gravité, frottement de l'air,
 * vent), rotations 3D (rotationX/rotationY) pour les confettis et billets qui scintillent, et
 * halos lumineux en dégradés radiaux.
 *
 * Aucun Canvas, DrawScope, drawText, BlendMode ni CompositingStrategy.Offscreen (source des
 * plantages) : chaque élément est un Box/Text animé par graphicsLayer, lu à chaque image sans
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
        MessageEffect.FIREWORKS -> 4600
        MessageEffect.LASERS, MessageEffect.SNOW -> 4200
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
            MessageEffect.LASERS -> Lasers(t, w, h, seed, px)
            MessageEffect.CELEBRATION -> Celebration(t, w, h, seed, px)
            MessageEffect.SPOTLIGHT -> Spotlight(progress.value, w, h, seed, px)
            MessageEffect.ECHO -> Echo(t, w, h, seed, text)
            MessageEffect.SNOW -> Snow(t, w, h, seed, px)
            MessageEffect.EMOJI_RAIN -> EmojiRain(t, w, h, seed, text)
            MessageEffect.RAINBOW -> Rainbow(progress.value, w, h, px)
            MessageEffect.MONEY -> Money(t, w, h, seed, px)
            MessageEffect.SHOOTING_STARS -> ShootingStars(t, w, h, seed, px)
            else -> Unit
        }
    }
}

// ------------------------------------------------------------------ outils communs

/** Conversion pixels → dp (toute la géométrie est calculée en pixels). */
private class Px(val density: Float) {
    fun dp(px: Float): Dp = (px / density).dp
}

/** Fondu d'entrée/sortie commun. */
private fun fade(t: Float, inEnd: Float = 0.1f, outStart: Float = 0.8f): Float = when {
    t < inEnd -> t / inEnd
    t > outStart -> ((1f - t) / (1f - outStart)).coerceIn(0f, 1f)
    else -> 1f
}

/** Progression locale d'une particule qui démarre à [delay] et dure [span]. */
private fun local(t: Float, delay: Float, span: Float = 1f - delay): Float =
    ((t - delay) / span).coerceIn(0f, 1f)

/**
 * Chute réaliste : la particule accélère puis atteint sa vitesse limite (frottement de l'air).
 * Renvoie la distance parcourue normalisée (0 → 1 à la fin).
 */
private fun fallCurve(l: Float, drag: Float = 3f): Float {
    val terminal = l - (1f - exp(-drag * l)) / drag
    val norm = 1f - (1f - exp(-drag)) / drag
    return terminal / norm
}

/** Éclaircit ou assombrit une couleur. */
private fun Color.shade(amount: Float): Color =
    if (amount >= 0f) lerp(this, Color.White, amount) else lerp(this, Color.Black, -amount)

/** Voile plein écran (couleur unie ou dégradé) dont l'opacité suit [alpha]. */
@Composable
private fun Scrim(brush: Brush, alpha: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .background(brush)
    )
}

@Composable
private fun Scrim(color: Color, alpha: () -> Float) = Scrim(Brush.linearGradient(listOf(color, color)), alpha)

/** Point lumineux : cœur brillant + halo (dégradé radial). [radius] = rayon du halo en px. */
@Composable
private fun Glow(radius: Float, color: Color, px: Px, core: Float = 0.35f, layer: GraphicsLayerScope.() -> Unit) {
    Box(
        Modifier
            .size(px.dp(radius * 2))
            .graphicsLayer(layer)
            .background(
                Brush.radialGradient(
                    0f to Color.White,
                    core * 0.5f to color,
                    core to color.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
                CircleShape,
            )
    )
}

/** Disque doux (bords flous) de rayon [radius] px. */
@Composable
private fun SoftDot(radius: Float, color: Color, px: Px, layer: GraphicsLayerScope.() -> Unit) {
    Box(
        Modifier
            .size(px.dp(radius * 2))
            .graphicsLayer(layer)
            .background(
                Brush.radialGradient(0f to color, 0.55f to color.copy(alpha = color.alpha * 0.8f), 1f to Color.Transparent),
                CircleShape,
            )
    )
}

/** Segment fixe de (x1,y1) à (x2,y2), épaisseur en px. */
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

private class ConfettiPiece(
    val x0: Float, val y0: Float, val delay: Float, val speed: Float, val sway: Float, val swayFreq: Float,
    val phase: Float, val drift: Float, val color: Color, val shape: Int, val spin: Float, val flip: Float, val depth: Float,
)

@Composable
private fun Confetti(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    val pieces = remember(seed, w, h) {
        val rnd = Random(seed)
        List(180) {
            val depth = rnd.nextFloat()
            ConfettiPiece(
                x0 = rnd.nextFloat() * w,
                y0 = -40f - rnd.nextFloat() * h * 0.35f,
                delay = rnd.nextFloat() * 0.3f,
                speed = 0.75f + depth * 0.7f,
                sway = 15f + rnd.nextFloat() * 55f,
                swayFreq = 5f + rnd.nextFloat() * 6f,
                phase = rnd.nextFloat() * TAU,
                drift = (rnd.nextFloat() - 0.5f) * 120f,
                color = Festive[rnd.nextInt(Festive.size)],
                shape = rnd.nextInt(3),
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                flip = 2f + rnd.nextFloat() * 5f,
                depth = depth,
            )
        }
    }
    pieces.forEach { p ->
        val scale = 0.6f + p.depth * 0.7f
        // 0 = rectangle, 1 = serpentin fin, 2 = pastille ronde
        val (pw, ph) = when (p.shape) { 0 -> 16f to 9f; 1 -> 24f to 5f; else -> 10f to 10f }
        Box(
            Modifier
                .size(px.dp(pw * scale), px.dp(ph * scale))
                .graphicsLayer {
                    val tt = t()
                    val l = local(tt, p.delay)
                    if (l <= 0f) { alpha = 0f; return@graphicsLayer }
                    val y = p.y0 + fallCurve(l, 2.5f) * p.speed * (h + 200f - p.y0)
                    val x = p.x0 + sin(l * p.swayFreq + p.phase) * p.sway + p.drift * l
                    translationX = x - pw * scale / 2
                    translationY = y - ph * scale / 2
                    rotationZ = p.spin * l
                    // Retournement 3D : le confetti « scintille » en captant la lumière.
                    val flipAngle = l * p.flip * 360f
                    rotationX = flipAngle
                    rotationY = sin(l * p.flip * 2f + p.phase) * 40f
                    cameraDistance = 10f * density
                    val shine = 0.55f + 0.45f * abs(cos(flipAngle * PI.toFloat() / 180f))
                    alpha = fade(tt, 0.02f, 0.88f) * shine * (0.6f + 0.4f * p.depth)
                }
                .background(p.color, if (p.shape == 2) CircleShape else RoundedCornerShape(1.dp))
        )
    }
}

// ------------------------------------------------------------------ Ballons

private class BalloonSpec(
    val x0: Float, val delay: Float, val r: Float, val color: Color, val index: Int,
    val speed: Float, val bob: Float, val tilt: Float,
)

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
                speed = 0.85f + rnd.nextFloat() * 0.3f,
                bob = 2.5f + rnd.nextFloat() * 2f,
                tilt = 4f + rnd.nextFloat() * 6f,
            )
        }
    }
    // Les plus petits (lointains) d'abord, pour que les grands passent devant.
    balloons.sortedBy { it.r }.forEach { b ->
        val r = b.r
        Box(
            Modifier
                .size(px.dp(r * 2), px.dp(r * 3.9f))
                .graphicsLayer {
                    val l = local(t(), b.delay)
                    if (l <= 0f) { alpha = 0f; return@graphicsLayer }
                    // Montée qui démarre doucement (poussée d'Archimède) puis se stabilise.
                    val rise = l * l * (3f - 2f * l) * 0.35f + l * 0.65f
                    val y = h + r * 2 - rise * b.speed * (h + r * 6)
                    val x = b.x0 + sin(l * 6f + b.index) * 30f
                    translationX = x - r
                    translationY = y - r * 1.15f - sin(l * TAU * b.bob) * 6f
                    transformOrigin = TransformOrigin(0.5f, 0.3f)
                    rotationZ = sin(l * TAU * 1.2f + b.index) * b.tilt
                }
        ) {
            // Ficelle qui ondule
            Segment(r, r * 2.3f, r - 8f, r * 2.95f, 2.5f, Color(0xFF9E9E9E), px)
            Segment(r - 8f, r * 2.95f, r + 6f, r * 3.75f, 2.5f, Color(0xFF9E9E9E), px)
            // Nœud
            Box(
                Modifier
                    .size(px.dp(12f))
                    .graphicsLayer {
                        translationX = r - 6f
                        translationY = r * 2.3f - 8f
                        rotationZ = 45f
                    }
                    .background(b.color.shade(-0.25f))
            )
            // Enveloppe : dégradé sphérique (lumière en haut à gauche, ombre en bas à droite)
            Box(
                Modifier
                    .size(px.dp(r * 2), px.dp(r * 2.3f))
                    .background(
                        Brush.radialGradient(
                            0f to b.color.shade(0.35f),
                            0.45f to b.color,
                            1f to b.color.shade(-0.35f),
                            center = Offset(r * 0.7f, r * 0.75f),
                            radius = r * 1.9f,
                        ),
                        RoundedCornerShape(percent = 50),
                    )
            )
            // Reflet brillant
            Box(
                Modifier
                    .size(px.dp(r * 0.5f), px.dp(r * 0.75f))
                    .graphicsLayer {
                        translationX = r * 0.45f
                        translationY = r * 0.3f
                        rotationZ = -20f
                    }
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f))),
                        RoundedCornerShape(percent = 50),
                    )
            )
        }
    }
}

// ------------------------------------------------------------------ Amour

/** Cœur construit avec un carré pivoté et deux disques (taille [size] px). */
@Composable
private fun Heart(size: Float, color: Color, px: Px, modifier: Modifier = Modifier, gloss: Boolean = true) {
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
                .background(Brush.linearGradient(listOf(color, color.shade(-0.3f))))
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
        if (gloss) {
            // Reflet sur le lobe gauche
            Box(
                Modifier
                    .size(px.dp(side * 0.38f), px.dp(side * 0.24f))
                    .graphicsLayer {
                        translationX = size / 2 - lobe - side * 0.22f
                        translationY = size * 0.58f - lobe - side * 0.3f
                        rotationZ = -35f
                    }
                    .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(percent = 50))
            )
        }
    }
}

/** Battement « lub-dub » d'un cœur. */
private fun heartbeat(t: Float): Float {
    val phase = (t * 4f) % 1f
    fun pulse(x: Float) = exp(-(x * x) / 0.0025f)
    return 1f + 0.12f * pulse(phase - 0.1f) + 0.07f * pulse(phase - 0.28f)
}

private class FloatingHeart(
    val x0: Float, val delay: Float, val size: Float, val sway: Float, val phase: Float,
    val color: Color, val emoji: Boolean, val speed: Float,
)

@Composable
private fun Love(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    val cx = w / 2
    val cy = h / 2
    val minDim = minOf(w, h)
    val heartSize = minDim * 0.28f * 1.5f
    // Halo rose qui pulse derrière le cœur
    Box(
        Modifier
            .size(px.dp(heartSize * 2.4f))
            .graphicsLayer {
                val tt = t()
                val s = (tt / 0.25f).coerceAtMost(1f) * heartbeat(tt)
                translationX = cx - heartSize * 1.2f
                translationY = cy - heartSize * 1.2f
                scaleX = s
                scaleY = s
                alpha = 0.55f * fade(tt, 0.05f, 0.8f)
            }
            .background(Brush.radialGradient(listOf(Color(0xFFFF4D6D).copy(alpha = 0.6f), Color.Transparent)), CircleShape)
    )
    // Petits cœurs qui s'élèvent en ondulant autour du grand
    val floating = remember(seed, w, h) {
        val rnd = Random(seed)
        List(26) {
            FloatingHeart(
                x0 = w * (0.1f + rnd.nextFloat() * 0.8f),
                delay = rnd.nextFloat() * 0.55f,
                size = 22f + rnd.nextFloat() * 34f,
                sway = 20f + rnd.nextFloat() * 40f,
                phase = rnd.nextFloat() * TAU,
                color = listOf(Color(0xFFFF4D6D), Color(0xFFFF80AB), Color(0xFFE91E63), Color(0xFFFFB3C6))[rnd.nextInt(4)],
                emoji = rnd.nextInt(3) == 0,
                speed = 0.6f + rnd.nextFloat() * 0.5f,
            )
        }
    }
    floating.forEach { f ->
        val layer: GraphicsLayerScope.() -> Unit = {
            val tt = t()
            val l = local(tt, f.delay, 0.45f)
            if (l <= 0f || l >= 1f) {
                alpha = 0f
            } else {
                translationX = f.x0 + sin(l * 6f + f.phase) * f.sway - f.size / 2
                translationY = h * (0.95f - l * f.speed) - f.size / 2
                val pop = (l / 0.15f).coerceAtMost(1f)
                scaleX = pop
                scaleY = pop
                rotationZ = sin(l * 5f + f.phase) * 15f
                alpha = (1f - l).coerceIn(0f, 1f) * fade(tt, 0.05f, 0.85f)
            }
        }
        if (f.emoji) {
            Box(Modifier.size(px.dp(f.size)).graphicsLayer(layer), contentAlignment = Alignment.Center) {
                Text("💕", fontSize = (f.size * 0.7f / px.density).sp)
            }
        } else {
            Heart(f.size, f.color, px, Modifier.graphicsLayer(layer), gloss = false)
        }
    }
    // Grand cœur qui apparaît en rebondissant puis bat
    Heart(
        heartSize, Color(0xFFFF4D6D), px,
        Modifier.graphicsLayer {
            val tt = t()
            val appear = (tt / 0.25f).coerceAtMost(1f)
            val overshoot = 1f + 0.18f * sin(appear * PI.toFloat()) * (1f - appear)
            val s = appear * overshoot * heartbeat(tt)
            translationX = cx - heartSize / 2
            translationY = cy - heartSize / 2 + minDim * 0.28f * 0.2f * s
            scaleX = s
            scaleY = s
            alpha = fade(tt, 0.05f, 0.8f)
        },
    )
    // Les 💕 d'origine qui jaillissent du centre
    val sparks = remember(seed) {
        val rnd = Random(seed + 1)
        List(18) { rnd.nextFloat() * TAU to (0.5f + rnd.nextFloat() * 0.4f) }
    }
    val box = 34.dp
    val half = box.value * px.density / 2
    sparks.forEach { (angle, distFactor) ->
        Box(
            Modifier
                .size(box)
                .graphicsLayer {
                    val tt = t()
                    val ease = 1f - (1f - tt).pow(2)
                    val dist = ease * minDim * distFactor
                    translationX = cx + cos(angle) * dist - half
                    translationY = cy + sin(angle) * dist - half - tt * tt * 60f
                    rotationZ = cos(angle) * 20f
                    alpha = fade(tt, 0.05f, 0.8f)
                },
            contentAlignment = Alignment.Center,
        ) { Text("💕", fontSize = 22.sp) }
    }
}

// ------------------------------------------------------------------ Feux d'artifice

private class Firework(val start: Float, val cx: Float, val cy: Float, val color: Color, val accent: Color, val size: Float)
private class Spark(val angle: Float, val speed: Float, val twinkle: Float, val accent: Boolean)

@Composable
private fun Fireworks(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    // Ciel nocturne en dégradé
    Scrim(Brush.verticalGradient(listOf(Color(0xFF000814), Color(0xFF14002E), Color(0xFF000000)))) {
        0.82f * fade(t(), 0.06f, 0.88f)
    }
    val fireworks = remember(seed, w, h) {
        val rnd = Random(seed)
        List(7) { i ->
            Firework(
                start = i * 0.1f + rnd.nextFloat() * 0.04f,
                cx = w * (0.15f + rnd.nextFloat() * 0.7f),
                cy = h * (0.12f + rnd.nextFloat() * 0.4f),
                color = Festive[rnd.nextInt(Festive.size - 1)],
                accent = Festive[rnd.nextInt(Festive.size)],
                size = minOf(w, h) * (0.24f + rnd.nextFloat() * 0.12f),
            )
        }
    }
    val sparks = remember(seed) {
        val rnd = Random(seed + 7)
        List(fireworks.size) {
            List(40) { s ->
                Spark(
                    angle = s * TAU / 40 + (rnd.nextFloat() - 0.5f) * 0.12f,
                    speed = 0.75f + rnd.nextFloat() * 0.3f,
                    twinkle = rnd.nextFloat() * TAU,
                    accent = rnd.nextInt(4) == 0,
                )
            }
        }
    }
    val rocketSpan = 0.12f
    val burstSpan = 0.32f
    fireworks.forEachIndexed { fi, f ->
        // Éclair du ciel au moment de l'explosion
        Box(
            Modifier
                .size(px.dp(f.size * 3f))
                .graphicsLayer {
                    val k = (t() - f.start - rocketSpan) / burstSpan
                    if (k !in 0f..0.35f) { alpha = 0f; return@graphicsLayer }
                    translationX = f.cx - f.size * 1.5f
                    translationY = f.cy - f.size * 1.5f
                    alpha = 0.45f * (1f - k / 0.35f)
                }
                .background(Brush.radialGradient(listOf(f.color.copy(alpha = 0.7f), Color.Transparent)), CircleShape)
        )
        // Fusée qui monte (ralentit en haut) avec sa traînée d'étincelles
        repeat(6) { trail ->
            Glow(if (trail == 0) 9f else 6f - trail * 0.6f, Color(0xFFFFE082), px) {
                val k = (t() - f.start) / rocketSpan - trail * 0.05f
                if (k !in 0f..1f || (t() - f.start) / rocketSpan > 1f) { alpha = 0f; return@Glow }
                val ease = 1f - (1f - k) * (1f - k)
                val r = if (trail == 0) 9f else 6f - trail * 0.6f
                translationX = f.cx + sin(k * 9f + fi) * 3f - r
                translationY = h - (h - f.cy) * ease - r
                alpha = 1f - trail * 0.15f
            }
        }
        // Gerbe : vitesse initiale, freinage de l'air, gravité, scintillement final
        sparks[fi].forEach { s ->
            val color = if (s.accent) f.accent else f.color
            Glow(9f, color, px, core = 0.4f) {
                val k = (t() - f.start - rocketSpan) / burstSpan
                if (k !in 0f..1f) { alpha = 0f; return@Glow }
                val travel = (1f - exp(-4f * k)) / (1f - exp(-4f)) * f.size * s.speed
                translationX = f.cx + cos(s.angle) * travel - 9f
                translationY = f.cy + sin(s.angle) * travel + k * k * 110f - 9f
                val sparkle = if (k > 0.55f) 0.55f + 0.45f * sin(k * 60f + s.twinkle) else 1f
                alpha = ((1f - k).pow(1.3f) * sparkle).coerceIn(0f, 1f)
                val s2 = 1f - k * 0.45f
                scaleX = s2
                scaleY = s2
            }
        }
    }
}

// ------------------------------------------------------------------ Lasers

@Composable
private fun Lasers(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    Scrim(Color.Black) { 0.85f * fade(t(), 0.08f, 0.85f) }
    val colors = listOf(Color(0xFF00E5FF), Color(0xFFFF1744), Color(0xFF76FF03), Color(0xFFE040FB))
    val ox = w / 2
    val oy = h * 0.45f
    val len = maxOf(w, h) * 1.2f
    // Brume colorée qui pulse avec la musique
    colors.forEachIndexed { i, c ->
        Box(
            Modifier
                .size(px.dp(maxOf(w, h)))
                .graphicsLayer {
                    val tt = t()
                    val a = tt * 3f + i * TAU / 4
                    translationX = ox - maxOf(w, h) / 2 + cos(a) * w * 0.25f
                    translationY = oy - maxOf(w, h) / 2 + sin(a) * h * 0.2f
                    alpha = (0.18f + 0.12f * sin(tt * 30f + i)) * fade(tt, 0.08f, 0.85f)
                }
                .background(Brush.radialGradient(listOf(c.copy(alpha = 0.6f), Color.Transparent)), CircleShape)
        )
    }
    val phases = remember(seed) { val rnd = Random(seed); List(12) { rnd.nextFloat() * TAU } }
    val beamThickness = 30f
    repeat(12) { i ->
        val c = colors[i % colors.size]
        // Faisceau : cœur blanc, couleur saturée, halo qui s'estompe sur les bords
        Box(
            Modifier
                .size(px.dp(len), px.dp(beamThickness))
                .graphicsLayer {
                    val tt = t()
                    // Rotation d'origine + balayage de va-et-vient, comme une lyre de boîte de nuit
                    val a = (tt * 6f + i * (TAU / 12)) % TAU + sin(tt * 9f + phases[i]) * 0.25f
                    translationX = ox
                    translationY = oy - beamThickness / 2
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = a * 180f / PI.toFloat()
                    // Stroboscope
                    val strobe = 0.7f + 0.3f * sin(tt * 80f + phases[i])
                    alpha = strobe * fade(tt, 0.08f, 0.85f)
                }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to c.copy(alpha = 0.35f),
                        0.45f to c,
                        0.5f to Color.White,
                        0.55f to c,
                        0.7f to c.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    )
                )
        )
    }
    // Source lumineuse au centre
    Glow(110f, Color(0xFFB388FF), px, core = 0.25f) {
        val tt = t()
        translationX = ox - 110f
        translationY = oy - 110f
        val pulse = 1f + 0.1f * sin(tt * 40f)
        scaleX = pulse
        scaleY = pulse
        alpha = fade(tt, 0.08f, 0.85f)
    }
}

// ------------------------------------------------------------------ Célébration

private class Sparkle(val delay: Float, val angle: Float, val speed: Float, val r: Float, val index: Int, val star: Boolean)

@Composable
private fun Celebration(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    // Pluie d'étincelles dorées depuis le coin supérieur droit.
    Scrim(Color.Black) { 0.4f * fade(t(), 0.05f, 0.8f) }
    // Lueur dorée dans le coin
    Box(
        Modifier
            .size(px.dp(maxOf(w, h)))
            .graphicsLayer {
                translationX = w - maxOf(w, h) / 2
                translationY = -maxOf(w, h) / 2
                alpha = 0.6f * fade(t(), 0.05f, 0.8f)
            }
            .background(Brush.radialGradient(listOf(Color(0xFFFFD54F).copy(alpha = 0.55f), Color.Transparent)), CircleShape)
    )
    val maxDim = maxOf(w, h)
    val sparkles = remember(seed, w, h) {
        val rnd = Random(seed)
        List(150) { i ->
            Sparkle(
                delay = rnd.nextFloat() * 0.5f,
                angle = (PI / 2 + rnd.nextFloat() * PI / 2).toFloat(),
                speed = maxDim * (0.4f + rnd.nextFloat() * 0.8f),
                r = 6f + rnd.nextFloat() * 6f,
                index = i,
                star = rnd.nextInt(3) != 0,
            )
        }
    }
    val gold = listOf(Color(0xFFFFD54F), Color(0xFFFFF59D), Color(0xFFFFB300))
    sparkles.forEach { s ->
        val layer: GraphicsLayerScope.() -> Unit = {
            val l = local(t(), s.delay, 0.5f)
            if (l <= 0f || l >= 1f) {
                alpha = 0f
            } else {
                // Freinage de l'air puis chute
                val travel = (1f - exp(-3f * l)) / (1f - exp(-3f)) * s.speed
                val twinkle = 0.45f + 0.55f * abs(sin(l * 40f + s.index))
                val boxPx = s.r * 3f
                translationX = w + cos(s.angle) * travel - boxPx / 2
                translationY = sin(s.angle) * travel + l * l * 320f - boxPx / 2
                rotationZ = l * 180f * (if (s.index % 2 == 0) 1f else -1f)
                alpha = ((1f - l) * twinkle).coerceIn(0f, 1f)
            }
        }
        if (s.star) {
            Box(Modifier.size(px.dp(s.r * 3f)).graphicsLayer(layer), contentAlignment = Alignment.Center) {
                // Halo + étoile
                Box(
                    Modifier
                        .size(px.dp(s.r * 3f))
                        .background(Brush.radialGradient(listOf(Color(0xFFFFE082).copy(alpha = 0.7f), Color.Transparent)), CircleShape)
                )
                Text("✦", color = gold[s.index % gold.size], fontSize = (s.r * 2.2f / px.density).sp, textAlign = TextAlign.Center)
            }
        } else {
            Glow(s.r * 1.5f, gold[s.index % gold.size], px, core = 0.4f, layer = layer)
        }
    }
}

// ------------------------------------------------------------------ Projecteur

@Composable
private fun Spotlight(t: Float, w: Float, h: Float, seed: Int, px: Px) {
    val alpha = fade(t, 0.1f, 0.8f)
    // Le faisceau balaie puis se pose sur la dernière bulle (bas droite).
    val settle = (t / 0.4f).coerceAtMost(1f)
    val eased = settle * settle * (3f - 2f * settle)
    val target = Offset(w * 0.72f, h * 0.82f)
    val sweep = Offset(w * (0.2f + 0.5f * sin(t * 8f)), h * 0.4f)
    val center = sweep + (target - sweep) * eased
    val radius = 280f
    val dark = Color.Black.copy(alpha = 0.88f * alpha)
    // Voile sombre percé d'un trou lumineux au bord adouci (dégradé radial, sans BlendMode).
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    0.8f to dark.copy(alpha = dark.alpha * 0.85f),
                    1f to dark,
                    center = center,
                    radius = radius,
                )
            )
    )
    // Lumière chaude dans le cercle
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0f to Color(0xFFFFF8E1).copy(alpha = 0.16f * alpha),
                    0.6f to Color(0xFFFFF8E1).copy(alpha = 0.06f * alpha),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                )
            )
    )
    // Poussières qui flottent dans le faisceau
    val dust = remember(seed) { val rnd = Random(seed); List(18) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
    dust.forEachIndexed { i, (a, d, s) ->
        val angle = a * TAU + t * (0.6f + s)
        val dist = d * radius * 0.45f
        val r = 1.5f + s * 2f
        SoftDot(r, Color.White, px) {
            translationX = center.x + cos(angle) * dist - r
            translationY = center.y + sin(angle) * dist - t * 30f * (i % 3) - r
            this.alpha = alpha * (0.3f + 0.5f * s)
        }
    }
}

// ------------------------------------------------------------------ Écho

private class EchoCopy(val delay: Float, val y: Float, val fromLeft: Boolean, val color: Color, val depth: Float, val drift: Float)

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
                depth = rnd.nextFloat(),
                drift = (rnd.nextFloat() - 0.5f) * 80f,
            )
        }
    }
    // Les copies lointaines d'abord : elles passent derrière les proches.
    copies.sortedBy { it.depth }.forEach { c ->
        val span = 0.55f - c.depth * 0.2f // les proches vont plus vite
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    val l = local(t(), c.delay, span)
                    if (l <= 0f || l >= 1f) { alpha = 0f; return@graphicsLayer }
                    translationX = if (c.fromLeft) -300f + l * (w + 400f) else w + 100f - l * (w + 400f)
                    translationY = c.y + c.drift * l
                    val pop = (l / 0.12f).coerceAtMost(1f)
                    scaleX = pop
                    scaleY = pop
                    alpha = (0.35f + 0.6f * c.depth) * (1f - ((l - 0.85f) / 0.15f).coerceIn(0f, 1f))
                }
        ) {
            Text(
                label,
                color = c.color.copy(alpha = 0.95f),
                fontSize = (13 + c.depth * 18).sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ------------------------------------------------------------------ Neige

private class Flake(val x0: Float, val speed: Float, val r: Float, val offset: Float, val index: Int, val depth: Float, val sway: Float)

@Composable
private fun Snow(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    Scrim(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B263B).copy(alpha = 0.6f), Color.Transparent))) {
        0.5f * fade(t(), 0.1f, 0.85f)
    }
    val flakes = remember(seed, w, h) {
        val rnd = Random(seed)
        List(170) { i ->
            val depth = rnd.nextFloat()
            Flake(
                x0 = rnd.nextFloat() * w,
                speed = 0.25f + depth * 0.75f,
                r = 1.5f + depth * 5.5f,
                offset = rnd.nextFloat(),
                index = i,
                depth = depth,
                sway = 8f + depth * 22f,
            )
        }
    }
    // Lointains (petits, lents, pâles) puis proches (gros, rapides, nets)
    flakes.sortedBy { it.depth }.forEach { f ->
        SoftDot(f.r * 1.4f, Color.White, px) {
            val tt = t()
            val y = ((f.offset + tt * f.speed) % 1f) * (h + 40f) - 20f
            // Rafales de vent communes + oscillation propre à chaque flocon
            val gust = sin(tt * 3f) * 40f * f.depth
            val x = f.x0 + sin(tt * 8f + f.index) * f.sway + gust
            translationX = x - f.r * 1.4f
            translationY = y - f.r * 1.4f
            alpha = fade(tt, 0.1f, 0.85f) * (0.45f + 0.5f * f.depth)
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

private class FallingGlyph(
    val x: Float, val delay: Float, val speed: Float, val glyph: String, val sizeSp: Int, val index: Int, val tumble: Float,
)

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
                tumble = (rnd.nextFloat() - 0.5f) * 2f,
            )
        }
    }
    // Les petits (lointains) d'abord
    drops.sortedBy { it.sizeSp }.forEach { d ->
        val depth = (d.sizeSp - 22) / 20f
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    val tt = t()
                    val l = local(tt, d.delay)
                    if (l <= 0f) { alpha = 0f; return@graphicsLayer }
                    translationX = d.x + sin(l * 4f + d.index) * 25f
                    translationY = -80f + fallCurve(l, 2f) * d.speed * (h + 160f)
                    rotationZ = sin(l * 6f + d.index) * 25f + d.tumble * l * 90f
                    rotationY = sin(l * 5f + d.index) * 35f
                    cameraDistance = 12f * density
                    alpha = fade(tt, 0.02f, 0.85f) * (0.7f + 0.3f * depth)
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
    val grow = run { val g = (t / 0.4f).coerceAtMost(1f); 1f - (1f - g) * (1f - g) }
    val minDim = minOf(w, h)
    val band = minDim * 0.05f
    val outer = (minDim * 0.75f + band / 2) * grow
    if (outer > band) {
        // Un seul dégradé radial pour les 7 bandes : transitions douces comme un vrai arc-en-ciel.
        val inner = outer - colors.size * band - band / 2
        val stops = ArrayList<Pair<Float, Color>>()
        stops += 0f to Color.Transparent
        stops += (inner / outer).coerceIn(0f, 1f) to Color.Transparent
        colors.asReversed().forEachIndexed { i, c ->
            val center = (inner + band * (i + 1)) / outer
            stops += center.coerceIn(0f, 1f) to c.copy(alpha = 0.8f)
        }
        stops += 1f to Color.Transparent
        val shimmer = 0.85f + 0.15f * sin(t * 12f)
        Box(
            Modifier
                .size(px.dp(outer * 2), px.dp(outer))
                .graphicsLayer {
                    translationX = cx - outer
                    translationY = cy - outer
                    this.alpha = alpha * shimmer
                }
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(px.dp(outer * 2))
                    .background(Brush.radialGradient(*stops.toTypedArray()), CircleShape)
            )
        }
    }
    // Nuages en volume aux extrémités, qui dérivent doucement
    listOf(cx - minDim * 0.62f, cx + minDim * 0.62f).forEachIndexed { side, x ->
        listOf(
            Triple(0f, 0f, 40f), Triple(-38f, 10f, 30f), Triple(38f, 10f, 30f),
            Triple(-18f, -18f, 28f), Triple(20f, -14f, 26f),
        ).forEach { (dx, dy, base) ->
            val r = base * grow
            if (r > 0f) {
                Box(
                    Modifier
                        .size(px.dp(r * 2))
                        .graphicsLayer {
                            translationX = x + dx - r + sin(t * 3f + side) * 8f
                            translationY = cy + dy - r
                            this.alpha = alpha
                        }
                        .background(
                            Brush.radialGradient(
                                0f to Color.White,
                                0.7f to Color(0xFFF2F4F8),
                                1f to Color(0xFFD5DAE3),
                                center = Offset(r * 0.8f, r * 0.7f),
                                radius = r * 1.3f,
                            ),
                            CircleShape,
                        )
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Pluie de billets

private class Bill(val x0: Float, val delay: Float, val speed: Float, val coin: Boolean, val index: Int, val flutter: Float)

@Composable
private fun Money(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    val bills = remember(seed, w, h) {
        val rnd = Random(seed)
        List(45) { i ->
            Bill(
                x0 = rnd.nextFloat() * w,
                delay = rnd.nextFloat() * 0.45f,
                speed = 0.6f + rnd.nextFloat() * 0.5f,
                coin = rnd.nextInt(4) == 0,
                index = i,
                flutter = 2f + rnd.nextFloat() * 3f,
            )
        }
    }
    bills.forEach { b ->
        Box(
            Modifier
                .size(px.dp(110f))
                .graphicsLayer {
                    val tt = t()
                    val l = local(tt, b.delay)
                    if (l <= 0f) { alpha = 0f; return@graphicsLayer }
                    cameraDistance = 12f * density
                    if (b.coin) {
                        // Pièce : chute rapide et rotation sur elle-même
                        translationX = b.x0 + sin(l * 3f + b.index) * 15f - 55f
                        translationY = -60f + fallCurve(l, 1.2f) * (h + 120f) * b.speed * 1.15f - 55f
                        rotationY = l * 1080f
                    } else {
                        // Billet : plane comme une feuille, se balance et se retourne
                        translationX = b.x0 + sin(l * 9f + b.index) * 50f - 55f
                        translationY = -60f + fallCurve(l, 4f) * (h + 120f) * b.speed - 55f
                        rotationZ = sin(l * 8f + b.index) * 40f
                        rotationX = sin(l * b.flutter * TAU) * 55f
                    }
                    alpha = fade(tt, 0.02f, 0.85f)
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (b.coin) "🪙" else "💵", fontSize = 30.sp) }
    }
}

// ------------------------------------------------------------------ Étoiles filantes

private class SkyStar(val x: Float, val y: Float, val r: Float, val index: Int, val color: Color, val speed: Float)
private class Comet(val start: Float, val fromX: Float, val fromY: Float, val span: Float)

@Composable
private fun ShootingStars(t: () -> Float, w: Float, h: Float, seed: Int, px: Px) {
    Scrim(Brush.verticalGradient(listOf(Color(0xFF050816), Color(0xFF0B1026), Color(0xFF1A1040)))) {
        0.85f * fade(t(), 0.08f, 0.85f)
    }
    val rnd = remember(seed) { Random(seed) }
    val starColors = listOf(Color.White, Color(0xFFCFE8FF), Color(0xFFFFF4D6))
    val stars = remember(seed, w, h) {
        List(120) { i ->
            SkyStar(
                x = rnd.nextFloat() * w,
                y = rnd.nextFloat() * h,
                r = 1.5f + rnd.nextFloat() * 1.5f,
                index = i,
                color = starColors[rnd.nextInt(starColors.size)],
                speed = 8f + rnd.nextFloat() * 20f,
            )
        }
    }
    stars.forEach { s ->
        Glow(s.r * 2.5f, s.color, px, core = 0.4f) {
            val tt = t()
            val tw = (0.4f + 0.6f * sin(tt * s.speed + s.index)).coerceIn(0f, 1f)
            translationX = s.x - s.r * 2.5f
            translationY = s.y - s.r * 2.5f
            alpha = fade(tt, 0.08f, 0.85f) * tw
        }
    }
    val comets = remember(seed, w, h) {
        List(8) { Comet(rnd.nextFloat() * 0.62f, w * (0.3f + rnd.nextFloat() * 0.9f), h * rnd.nextFloat() * 0.4f, 0.25f + rnd.nextFloat() * 0.1f) }
    }
    val dirX = -1f
    val dirY = 0.55f
    val tailAngle = (atan2(dirY, dirX) * 180f / PI).toFloat()
    val len = maxOf(w, h) * 0.6f
    val tail = 260f
    comets.forEach { c ->
        // Traînée : s'allonge au départ puis se raccourcit en disparaissant
        Box(
            Modifier
                .size(px.dp(tail), px.dp(5f))
                .graphicsLayer {
                    val tt = t()
                    val l = (tt - c.start) / c.span
                    if (l !in 0f..1f) { alpha = 0f; return@graphicsLayer }
                    val ease = 1f - (1f - l) * (1f - l)
                    val headX = c.fromX + dirX * len * ease
                    val headY = c.fromY + dirY * len * ease
                    translationX = headX - tail
                    translationY = headY - 2.5f
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    rotationZ = tailAngle
                    scaleX = (sin(l * PI.toFloat()) * 1.2f).coerceIn(0.15f, 1f)
                    alpha = fade(tt, 0.08f, 0.85f) * (1f - l * 0.6f)
                }
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFCFE8FF).copy(alpha = 0.6f), Color.White)),
                    RoundedCornerShape(50),
                )
        )
        // Tête lumineuse
        Glow(14f, Color(0xFFCFE8FF), px, core = 0.35f) {
            val tt = t()
            val l = (tt - c.start) / c.span
            if (l !in 0f..1f) { alpha = 0f; return@Glow }
            val ease = 1f - (1f - l) * (1f - l)
            translationX = c.fromX + dirX * len * ease - 14f
            translationY = c.fromY + dirY * len * ease - 14f
            alpha = fade(tt, 0.08f, 0.85f) * (1f - l * 0.5f)
        }
    }
}

@Composable
fun EffectBadge(effect: MessageEffect) {
    Text("${effect.emoji} Envoyé avec « ${effect.label} »", fontSize = 11.sp, color = Color.Gray)
}
