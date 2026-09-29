package com.chatty.fr.effects

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Écran « Envoyer avec effet » (appui long sur le bouton Envoyer), inspiré d'iMessage.
 * Onglet Bulle : l'effet anime la bulle. Onglet Écran : l'effet remplit tout l'écran.
 */
@Composable
fun EffectPicker(
    text: String,
    bubbleColor: Color,
    onBubbleColor: Color,
    onSend: (MessageEffect?) -> Unit,
    onSchedule: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val haptics = LocalHapticFeedback.current
    var tab by remember { mutableIntStateOf(0) }
    var bubbleChoice by remember { mutableStateOf<MessageEffect?>(null) }
    var playKey by remember { mutableIntStateOf(0) }
    val pager = rememberPagerState { MessageEffect.screen.size }
    var loop by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0101114))
            // Absorbe les touches pour ne pas atteindre la conversation derrière.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        // Effet plein écran joué en boucle en arrière-plan (onglet Écran).
        if (tab == 1) {
            val effect = MessageEffect.screen[pager.currentPage]
            androidx.compose.runtime.key(effect, loop) {
                ScreenEffectOverlay(effect, text) { loop++ }
            }
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer", tint = Color.White) }
                Text(
                    "Envoyer avec effet",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSchedule) {
                    Icon(Icons.Default.Schedule, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Programmer", color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                listOf("Bulle", "Écran").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = tab == i,
                        onClick = { tab = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            if (tab == 0) {
                Spacer(Modifier.weight(1f))
                PreviewBubble(text, bubbleChoice, playKey, bubbleColor, onBubbleColor)
                Spacer(Modifier.height(28.dp))
                MessageEffect.bubble.forEach { effect ->
                    val selected = bubbleChoice == effect
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                bubbleChoice = effect
                                playKey++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(effect.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            effect.label, color = Color.White, fontSize = 17.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            SendButton(bubbleColor, onBubbleColor) { onSend(effect) }
                        }
                        if (!selected) {
                            Box(Modifier.size(28.dp).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape))
                        }
                    }
                }
            } else {
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.fillMaxSize())
                }
                PreviewBubble(text, null, null, bubbleColor, onBubbleColor)
                Spacer(Modifier.height(24.dp))
                val current = MessageEffect.screen[pager.currentPage]
                LaunchedEffect(pager.currentPage) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                Text(
                    "${current.emoji}  ${current.label}",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(MessageEffect.screen.size) { i ->
                        val a by animateFloatAsState(if (i == pager.currentPage) 1f else 0.35f, label = "dot")
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = a)))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Balayez pour changer d'effet",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SendButton(bubbleColor, onBubbleColor) { onSend(current) }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { onSend(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Envoyer sans effet", color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SendButton(color: Color, onColor: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = onColor),
        modifier = Modifier.size(44.dp),
    ) { Icon(Icons.Default.ArrowUpward, "Envoyer") }
}

@Composable
private fun PreviewBubble(text: String, effect: MessageEffect?, playKey: Any?, color: Color, onColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        val shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        Box(Modifier.widthIn(max = 280.dp).bubbleEffect(effect, playKey, isMine = true)) {
            val body = @Composable {
                Text(
                    text.ifBlank { "👋" },
                    color = onColor, fontSize = 16.sp,
                    modifier = Modifier.clip(shape).background(color).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            if (effect == MessageEffect.INVISIBLE_INK) InvisibleInk(false, shape, onColor) { body() } else body()
        }
    }
}
