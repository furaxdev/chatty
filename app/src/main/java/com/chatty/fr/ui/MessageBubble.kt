package com.chatty.fr.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatty.fr.ChattyViewModel
import com.chatty.fr.data.Message
import com.chatty.fr.data.MessageFormat
import com.chatty.fr.data.MessageStatus
import com.chatty.fr.data.ScheduledMessage
import com.chatty.fr.effects.EffectKind
import com.chatty.fr.effects.InvisibleInk
import com.chatty.fr.effects.MessageEffect
import com.chatty.fr.effects.bubbleEffect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val Reactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "👎", "🔥")

@Composable
fun DayHeader(date: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            formatDayHeader(date),
            fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun UnreadDivider(count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Text(
            if (count > 1) "$count nouveaux messages" else "Nouveau message",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    }
}

private val UrlRegex = Regex("""(https?://\S+|www\.\S+)""")
private val PhoneRegex = Regex("""(?<![\w/])(\+?\d[\d .-]{7,}\d)""")
private val EmailRegex = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")

/** Rend cliquables les liens, e-mails et numéros de téléphone. */
fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    data class Hit(val range: IntRange, val url: String)
    val hits = (UrlRegex.findAll(text).map { Hit(it.range, if (it.value.startsWith("www.")) "https://${it.value}" else it.value) } +
        EmailRegex.findAll(text).map { Hit(it.range, "mailto:${it.value}") } +
        PhoneRegex.findAll(text).map { Hit(it.range, "tel:${it.value.filter { c -> c.isDigit() || c == '+' }}") })
        .sortedBy { it.range.first }
    var cursor = 0
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    for (hit in hits) {
        if (hit.range.first < cursor) continue
        append(text.substring(cursor, hit.range.first))
        withLink(LinkAnnotation.Url(hit.url, style)) { append(text.substring(hit.range)) }
        cursor = hit.range.last + 1
    }
    append(text.substring(cursor))
}

fun isEmojiOnly(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > 16) return false
    var count = 0
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        val type = Character.getType(cp)
        val isEmojiish = type == Character.OTHER_SYMBOL.toInt() || type == Character.SURROGATE.toInt() ||
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0x1F1E6..0x1F1FF
        if (!isEmojiish) return false
        if (type == Character.OTHER_SYMBOL.toInt()) count++
        i += Character.charCount(cp)
    }
    return count in 1..3
}

/**
 * Bulle de message : glisser vers la droite pour répondre, toucher pour afficher l'heure,
 * appui long pour le menu (réactions, copier…).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    m: Message,
    first: Boolean,
    last: Boolean,
    mineColor: Color,
    onMineColor: Color,
    playKey: Int?,
    revealed: Boolean,
    showDetails: Boolean,
    otherName: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    senderName: String? = null,
    onOpenAttachment: (com.chatty.fr.data.Attachment) -> Unit = {},
    onReplayEffect: () -> Unit = {},
) {
    val big = 20.dp
    val small = 4.dp
    val shape: Shape = if (m.isMine) {
        RoundedCornerShape(big, if (first) big else small, small, big)
    } else {
        RoundedCornerShape(if (first) big else small, big, big, small)
    }
    val bg = if (m.isMine) mineColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (m.isMine) onMineColor else MaterialTheme.colorScheme.onSurface
    val failed = m.status == MessageStatus.FAILED
    // Les messages composés uniquement d'emojis (1 à 3) s'affichent en grand, sans bulle.
    val emojiOnly = remember(m.body) { m.quote == null && isEmojiOnly(m.body) }
    val otp = remember(m.body) { if (m.isMine) null else MessageFormat.findOtp(m.body) }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var armed by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(top = if (first) 8.dp else 2.dp),
        horizontalAlignment = if (m.isMine) Alignment.End else Alignment.Start,
    ) {
        if (senderName != null && first && !m.isMine) {
            Text(
                senderName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = avatarColor(m.address),
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
        m.attachments.forEach { a ->
            Box(Modifier.padding(bottom = 3.dp)) {
                AttachmentView(a, onOpen = { onOpenAttachment(a) }, onLongPress = onLongPress)
            }
        }
        val hasText = m.body.isNotBlank() || m.quote != null
        if (hasText) Box(Modifier.fillMaxWidth(), contentAlignment = if (m.isMine) Alignment.CenterEnd else Alignment.CenterStart) {
            // Icône « répondre » révélée par le glissement.
            Icon(
                Icons.AutoMirrored.Filled.Reply, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .alpha((dragX.value / threshold).coerceIn(0f, 1f))
                    .size(22.dp),
            )
            Box(
                Modifier
                    .offset { IntOffset(dragX.value.roundToInt(), 0) }
                    .pointerInput(m.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (armed) onReply()
                                armed = false
                                scope.launch { dragX.animateTo(0f, spring()) }
                            },
                            onDragCancel = { armed = false; scope.launch { dragX.animateTo(0f, spring()) } },
                        ) { change, amount ->
                            val next = (dragX.value + amount * 0.6f).coerceIn(0f, threshold * 1.4f)
                            if (next != dragX.value) change.consume()
                            scope.launch { dragX.snapTo(next) }
                            if (!armed && next >= threshold) {
                                armed = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (armed && next < threshold) armed = false
                        }
                    }
            ) {
                Box(
                    Modifier
                        .widthIn(max = 300.dp)
                        .bubbleEffect(m.effect?.takeIf { it.kind == EffectKind.BUBBLE }, playKey, m.isMine)
                ) {
                    val content = @Composable {
                        Column(
                            Modifier
                                .clip(shape)
                                .then(if (emojiOnly) Modifier else Modifier.background(if (failed) bg.copy(alpha = 0.55f) else bg))
                                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                                .padding(horizontal = if (emojiOnly) 2.dp else 14.dp, vertical = if (emojiOnly) 0.dp else 9.dp)
                        ) {
                            m.quote?.let { quote ->
                                Row(
                                    Modifier
                                        .padding(bottom = 6.dp)
                                        .height(IntrinsicSize.Min)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(fg.copy(alpha = 0.10f))
                                        .clickable { onQuoteClick(quote) }
                                ) {
                                    Box(Modifier.width(3.dp).fillMaxHeight().background(fg.copy(alpha = 0.6f)))
                                    Text(
                                        quote, color = fg.copy(alpha = 0.85f), fontSize = 13.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            if (emojiOnly) {
                                Text(m.body, fontSize = 44.sp)
                            } else {
                                val linkColor = if (m.isMine) onMineColor else MaterialTheme.colorScheme.primary
                                Text(linkify(m.body, linkColor), color = fg, fontSize = 16.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                    if (m.effect == MessageEffect.INVISIBLE_INK) InvisibleInk(revealed, shape, fg) { content() } else content()

                    if (m.reactions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .align(if (m.isMine) Alignment.BottomStart else Alignment.BottomEnd)
                                .offset(x = if (m.isMine) (-10).dp else 10.dp, y = 14.dp)
                                .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                        ) {
                            val grouped = m.reactions.groupBy { it.emoji }
                            Text(
                                grouped.entries.joinToString(" ") { (e, list) -> if (list.size > 1) "$e${list.size}" else e },
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!hasText && m.reactions.isNotEmpty()) {
            Text(m.reactions.joinToString(" ") { it.emoji }, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (m.reactions.isNotEmpty() && hasText) Spacer(Modifier.height(12.dp))
        if (otp != null) {
            AssistChip(
                onClick = { onCopyCode(otp) },
                label = { Text("Copier le code $otp") },
                leadingIcon = { Icon(Icons.Default.Key, null, Modifier.size(AssistChipDefaults.IconSize)) },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (last || failed || showDetails) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m.starred) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                val status = when (m.status) {
                    MessageStatus.SENDING -> "Envoi…"
                    MessageStatus.SENT -> "Envoyé"
                    MessageStatus.DELIVERED -> "Distribué"
                    MessageStatus.FAILED -> "Échec · Touchez pour réessayer"
                    MessageStatus.RECEIVED -> null
                }
                val effectLabel = m.effect?.takeUnless { m.effectFromKeyword }?.let { " · ${it.emoji} ${it.label}" }.orEmpty()
                Text(
                    formatTime(m.date) + (status?.let { " · $it" } ?: ""),
                    fontSize = 11.sp,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (effectLabel.isNotEmpty()) {
                    // Toucher l'étiquette rejoue l'effet.
                    Text(
                        "$effectLabel ↻",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReplayEffect),
                    )
                }
            }
        } else if (m.starred) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp).padding(horizontal = 6.dp))
        }
    }
}

@Composable
fun PlannedBubble(s: ScheduledMessage, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) { Text(MessageFormat.decodeReply(s.body).second, fontSize = 16.sp) }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("Programmé · ${formatScheduled(s.sendAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Message en attente pendant le délai d'annulation. */
@Composable
fun PendingBubble(p: ChattyViewModel.PendingSend, color: Color, onColor: Color, onUndo: () -> Unit, onSendNow: () -> Unit) {
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis(), p.id) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(250) }
    }
    val remaining = ((p.sendAt - now) / 1000L + 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .alpha(0.6f)
                .clip(RoundedCornerShape(20.dp))
                .background(color)
                .clickable(onClick = onSendNow)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) { Text(MessageFormat.decodeReply(p.text).second, color = onColor, fontSize = 16.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Envoi dans $remaining s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onUndo) { Text("Annuler") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSheet(
    message: Message,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onStar: () -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
    onDetails: () -> Unit,
    onRemind: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Reactions.forEach { emoji ->
                val active = message.myReaction == emoji
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 24.sp) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SheetItem("Répondre", Icons.AutoMirrored.Filled.Reply, onReply)
        SheetItem("Copier le texte", Icons.Default.ContentCopy, onCopy)
        SheetItem("Partager", Icons.Default.Share, onShare)
        SheetItem(
            if (message.starred) "Retirer des favoris" else "Ajouter aux favoris",
            if (message.starred) Icons.Default.Star else Icons.Default.StarBorder, onStar,
        )
        message.effect?.let { effect -> SheetItem("Rejouer l'effet « ${effect.label} »", Icons.Default.Replay, onReplay) }
        if (message.status == MessageStatus.FAILED) SheetItem("Réessayer l'envoi", Icons.Default.Refresh, onRetry)
        SheetItem("Me le rappeler", Icons.Default.Alarm, onRemind)
        SheetItem("Détails", Icons.Default.Info, onDetails)
        ListItem(
            headlineContent = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable(onClick = onDelete),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
