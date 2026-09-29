package com.furaxdev.chatty.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Joue l'animation d'un effet de bulle. [playKey] change à chaque fois qu'on veut
 * (re)jouer l'effet ; `null` = bulle affichée au repos.
 */
@Composable
fun Modifier.bubbleEffect(effect: MessageEffect?, playKey: Any?, isMine: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val shiftX = remember { Animatable(0f) }
    val shiftY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(effect, playKey) {
        if (effect == null || playKey == null) return@LaunchedEffect
        when (effect) {
            MessageEffect.SLAM -> {
                // Arrive énorme et s'écrase sur l'écran
                scale.snapTo(3.2f); alpha.snapTo(0f); shiftY.snapTo(-160f)
                alpha.animateTo(1f, tween(120))
                shiftY.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
            }
            MessageEffect.LOUD -> {
                scale.animateTo(1f, keyframes {
                    durationMillis = 1300
                    1f at 0; 1.9f at 250; 1.75f at 400; 1.9f at 550; 1.75f at 700; 1.9f at 850; 1f at 1300
                })
            }
            MessageEffect.GENTLE -> {
                scale.snapTo(0.35f); alpha.snapTo(0.2f)
                alpha.animateTo(1f, tween(700))
                scale.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            }
            MessageEffect.SHAKE -> {
                shiftX.animateTo(0f, keyframes {
                    durationMillis = 900
                    0f at 0; -28f at 75; 28f at 150; -24f at 225; 24f at 300; -18f at 375
                    18f at 450; -10f at 525; 10f at 600; -4f at 700; 0f at 900
                })
            }
            MessageEffect.JELLY -> {
                // Rebondit comme de la gelée
                scale.snapTo(0.2f)
                scale.animateTo(1f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow))
            }
            else -> Unit
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = shiftX.value
        translationY = shiftY.value
        this.alpha = alpha.value
        transformOrigin = TransformOrigin(if (isMine) 1f else 0f, 1f)
    }
}

/**
 * Encre invisible : le contenu est flouté et recouvert de particules scintillantes
 * tant que [revealed] est faux.
 */
@Composable
fun InvisibleInk(revealed: Boolean, shape: Shape, tint: Color, content: @Composable () -> Unit) {
    Box {
        Box(
            Modifier
                .then(if (revealed) Modifier else Modifier.blur(14.dp))
                .alpha(if (revealed) 1f else 0.15f)
        ) { content() }
        if (!revealed) {
            val transition = rememberInfiniteTransition(label = "ink")
            val phase by transition.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                label = "inkPhase",
            )
            val seed = remember { Random.nextInt() }
            Canvas(Modifier.matchParentSize().clip(shape)) {
                val rnd = Random(seed)
                repeat(220) {
                    val x0 = rnd.nextFloat() * size.width
                    val y0 = rnd.nextFloat() * size.height
                    val off = rnd.nextFloat()
                    val a = 0.5f + 0.5f * sin((phase + off) * 6.283f * 2)
                    val dx = sin((phase + off) * 6.283f) * 6f
                    drawCircle(tint.copy(alpha = a * 0.9f), 1.6f + rnd.nextFloat() * 1.4f, Offset(x0 + dx, y0))
                }
            }
        }
    }
}
