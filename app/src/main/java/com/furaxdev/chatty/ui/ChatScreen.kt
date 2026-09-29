package com.furaxdev.chatty.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furaxdev.chatty.ChattyViewModel
import com.furaxdev.chatty.data.Message
import com.furaxdev.chatty.data.MessageStatus
import com.furaxdev.chatty.data.ScheduledMessage
import com.furaxdev.chatty.effects.EffectPicker
import com.furaxdev.chatty.effects.EffectKind
import com.furaxdev.chatty.effects.InvisibleInk
import com.furaxdev.chatty.effects.MessageEffect
import com.furaxdev.chatty.effects.ScreenEffectOverlay
import com.furaxdev.chatty.effects.bubbleEffect
import com.furaxdev.chatty.ui.theme.BubblePalette
import kotlinx.coroutines.delay

val Reactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "👎", "🔥")

private sealed interface ChatRow {
    val key: String
    data class Day(val date: Long) : ChatRow { override val key = "day_$date" }
    data class Msg(val m: Message, val first: Boolean, val last: Boolean) : ChatRow { override val key = "m_${m.id}" }
    data class Planned(val s: ScheduledMessage) : ChatRow { override val key = "s_${s.id}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChattyViewModel,
    threadId: Long,
    address: String,
    initialText: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val store = vm.store
    val version by store.version.collectAsState()
    val messages by vm.messages.collectAsState()
    val scheduled by vm.scheduled.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val conv = conversations.firstOrNull { it.threadId == threadId }
    val contact = remember(address, conversations) { vm.contact(address) }

    DisposableEffect(threadId) {
        vm.openThread(threadId)
        onDispose { vm.closeThread() }
    }

    var text by rememberSaveable(threadId) { mutableStateOf(initialText ?: store.draft(threadId)) }
    val latestText by rememberUpdatedState(text)
    DisposableEffect(threadId) { onDispose { store.setDraft(threadId, latestText) } }

    val palette = BubblePalette.getOrNull(remember(version) { store.bubbleColor })
    val mineColor = palette ?: MaterialTheme.colorScheme.primary
    val onMineColor = if (palette != null) Color.White else MaterialTheme.colorScheme.onPrimary

    // --- Lecture des effets ---
    var screenEffect by remember { mutableStateOf<Pair<MessageEffect, String>?>(null) }
    val bubblePlays = remember { mutableStateMapOf<Long, Int>() }
    val revealed = remember { mutableStateMapOf<Long, Boolean>() }
    var knownIds by remember(threadId) { mutableStateOf<Set<Long>?>(null) }

    fun play(m: Message) {
        val effect = m.effect ?: return
        if (effect.kind == EffectKind.SCREEN) screenEffect = effect to m.body
        else bubblePlays[m.id] = (bubblePlays[m.id] ?: 0) + 1
    }

    LaunchedEffect(messages) {
        if (messages.isEmpty() || messages.first().threadId != threadId) return@LaunchedEffect
        val known = knownIds
        // À l'ouverture : on rejoue l'effet du dernier message non lu. Ensuite : chaque nouveau message.
        val fresh = if (known == null) messages.filter { !it.isMine && !it.read } else messages.filter { it.id !in known }
        knownIds = messages.map { it.id }.toSet()
        if (store.autoPlayEffects) fresh.lastOrNull { it.effect != null }?.let(::play)
    }

    var showPicker by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Message?>(null) }
    var plannedAction by remember { mutableStateOf<ScheduledMessage?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun send(effect: MessageEffect?) {
        val body = text.trim()
        if (body.isEmpty()) return
        vm.send(address, body, effect, threadId)
        text = ""
        store.setDraft(threadId, "")
        showPicker = false
    }

    val rows = remember(messages, scheduled) { buildRows(messages, scheduled) }
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(contact, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                                if (contact.name != null) {
                                    Text(address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address")))
                        }) { Icon(Icons.Default.Call, "Appeler") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Plus") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                val pinned = conv?.pinned == true
                                val muted = conv?.muted == true
                                val archived = conv?.archived == true
                                DropdownMenuItem(
                                    text = { Text(if (pinned) "Désépingler" else "Épingler") },
                                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                                    onClick = { store.setPinned(threadId, !pinned); menu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (muted) "Réactiver les notifications" else "Mettre en sourdine") },
                                    leadingIcon = { Icon(if (muted) Icons.Default.Notifications else Icons.Default.NotificationsOff, null) },
                                    onClick = { store.setMuted(threadId, !muted); menu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (archived) "Désarchiver" else "Archiver") },
                                    leadingIcon = { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                                    onClick = {
                                        store.setArchived(threadId, !archived); menu = false
                                        if (!archived) onBack()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer la conversation") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { confirmDelete = true; menu = false },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Day -> DayHeader(row.date)
                            is ChatRow.Planned -> PlannedBubble(row.s) { plannedAction = row.s }
                            is ChatRow.Msg -> MessageBubble(
                                row = row,
                                mineColor = mineColor,
                                onMineColor = onMineColor,
                                playKey = bubblePlays[row.m.id],
                                revealed = revealed[row.m.id] == true,
                                onTap = {
                                    when {
                                        row.m.effect == MessageEffect.INVISIBLE_INK -> revealed[row.m.id] = revealed[row.m.id] != true
                                        row.m.status == MessageStatus.FAILED -> vm.retry(row.m.id)
                                    }
                                },
                                onLongPress = {
                                    if (store.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selected = row.m
                                },
                            )
                        }
                    }
                }
                Composer(
                    text = text,
                    onTextChange = { text = it },
                    sendColor = mineColor,
                    onSendColor = onMineColor,
                    hapticsEnabled = store.haptics,
                    onSend = { send(null) },
                    onLongPressSend = { showPicker = true },
                )
            }
        }

        // L'encre invisible se recache toute seule après quelques secondes.
        LaunchedEffect(revealed.toMap()) {
            if (revealed.values.any { it }) {
                delay(6000)
                revealed.keys.toList().forEach { revealed[it] = false }
            }
        }

        if (showPicker) {
            EffectPicker(
                text = text,
                bubbleColor = mineColor,
                onBubbleColor = onMineColor,
                onSend = { send(it) },
                onSchedule = { showPicker = false; showSchedule = true },
                onDismiss = { showPicker = false },
            )
        }

        screenEffect?.let { (effect, body) ->
            ScreenEffectOverlay(effect, body) { screenEffect = null }
        }
    }

    if (showSchedule) {
        ScheduleDialog(
            onPick = { at ->
                vm.schedule(address, threadId, text.trim(), null, at)
                text = ""
                store.setDraft(threadId, "")
                showSchedule = false
                Toast.makeText(context, "Programmé pour ${formatScheduled(at)}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSchedule = false },
        )
    }

    selected?.let { m ->
        MessageSheet(
            message = m,
            onReact = { emoji ->
                store.setReaction(m.id, if (m.reaction == emoji) null else emoji)
                selected = null
            },
            onCopy = { copy(context, m.body); selected = null },
            onStar = { store.setStarred(m.id, !m.starred); selected = null },
            onReplay = { play(m); selected = null },
            onRetry = { vm.retry(m.id); selected = null },
            onDelete = { vm.deleteMessage(m.id); selected = null },
            onDismiss = { selected = null },
        )
    }

    plannedAction?.let { s ->
        AlertDialog(
            onDismissRequest = { plannedAction = null },
            title = { Text("Message programmé") },
            text = { Text("« ${s.body} »\n\nEnvoi prévu ${formatScheduled(s.sendAt)}.") },
            confirmButton = {
                TextButton(onClick = { vm.sendScheduledNow(s.id); plannedAction = null }) { Text("Envoyer maintenant") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.cancelScheduled(s.id)
                    if (text.isBlank()) text = s.body
                    plannedAction = null
                }) { Text("Annuler l'envoi") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette conversation ?") },
            text = { Text("Tous les messages avec ${contact.displayName} seront supprimés de ce téléphone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteThread(threadId); confirmDelete = false; onBack() }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}

private fun buildRows(messages: List<Message>, scheduled: List<ScheduledMessage>): List<ChatRow> {
    val out = ArrayList<ChatRow>()
    messages.forEachIndexed { i, m ->
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        if (prev == null || !isSameDay(prev.date, m.date)) out += ChatRow.Day(m.date)
        val first = prev == null || prev.isMine != m.isMine || m.date - prev.date > 5 * 60_000 || !isSameDay(prev.date, m.date)
        val last = next == null || next.isMine != m.isMine || next.date - m.date > 5 * 60_000 || !isSameDay(next.date, m.date)
        out += ChatRow.Msg(m, first, last)
    }
    scheduled.forEach { out += ChatRow.Planned(it) }
    return out.asReversed()
}

@Composable
private fun DayHeader(date: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            formatDayHeader(date),
            fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val UrlRegex = Regex("""(https?://\S+|www\.\S+)""")

private fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    UrlRegex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val url = match.value.let { if (it.startsWith("www.")) "https://$it" else it }
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    row: ChatRow.Msg,
    mineColor: Color,
    onMineColor: Color,
    playKey: Int?,
    revealed: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val m = row.m
    val big = 20.dp
    val small = 4.dp
    val shape: Shape = if (m.isMine) {
        RoundedCornerShape(big, if (row.first) big else small, if (row.last) small else small, big)
    } else {
        RoundedCornerShape(if (row.first) big else small, big, big, small)
    }
    val bg = if (m.isMine) mineColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (m.isMine) onMineColor else MaterialTheme.colorScheme.onSurface
    val failed = m.status == MessageStatus.FAILED
    // Les messages composés uniquement d'emojis (1 à 3) s'affichent en grand, sans bulle.
    val emojiOnly = remember(m.body) { isEmojiOnly(m.body) }

    Column(
        Modifier.fillMaxWidth().padding(top = if (row.first) 8.dp else 2.dp),
        horizontalAlignment = if (m.isMine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .bubbleEffect(m.effect?.takeIf { it.kind == EffectKind.BUBBLE }, playKey, m.isMine)
        ) {
            val content = @Composable {
                Box(
                    Modifier
                        .clip(shape)
                        .then(if (emojiOnly) Modifier else Modifier.background(if (failed) bg.copy(alpha = 0.55f) else bg))
                        .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                        .padding(horizontal = if (emojiOnly) 2.dp else 14.dp, vertical = if (emojiOnly) 0.dp else 9.dp)
                ) {
                    if (emojiOnly) {
                        Text(m.body, fontSize = 44.sp)
                    } else {
                        val linkColor = if (m.isMine) onMineColor else MaterialTheme.colorScheme.primary
                        Text(linkify(m.body, linkColor), color = fg, fontSize = 16.sp, lineHeight = 21.sp)
                    }
                }
            }
            if (m.effect == MessageEffect.INVISIBLE_INK) InvisibleInk(revealed, shape, fg) { content() } else content()

            if (m.reaction != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .align(if (m.isMine) Alignment.BottomStart else Alignment.BottomEnd)
                        .offset(x = if (m.isMine) (-10).dp else 10.dp, y = 14.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                ) { Text(m.reaction, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
        if (m.reaction != null) Spacer(Modifier.height(12.dp))
        if (row.last || failed) {
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
                val effectLabel = m.effect?.let { " · ${it.emoji} ${it.label}" }.orEmpty()
                Text(
                    formatTime(m.date) + (status?.let { " · $it" } ?: "") + effectLabel,
                    fontSize = 11.sp,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (m.starred) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp).padding(horizontal = 6.dp))
        }
    }
}

private fun isEmojiOnly(text: String): Boolean {
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
        if (type == Character.OTHER_SYMBOL.toInt() && cp != 0x200D) count++
        i += Character.charCount(cp)
    }
    return count in 1..3
}

@Composable
private fun PlannedBubble(s: ScheduledMessage, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) { Text(s.body, fontSize = 16.sp) }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("Programmé · ${formatScheduled(s.sendAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSheet(
    message: Message,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Reactions.forEach { emoji ->
                val active = message.reaction == emoji
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
        ListItem(
            headlineContent = { Text("Copier le texte") },
            leadingContent = { Icon(Icons.Default.ContentCopy, null) },
            modifier = Modifier.clickable(onClick = onCopy),
        )
        ListItem(
            headlineContent = { Text(if (message.starred) "Retirer des favoris" else "Ajouter aux favoris") },
            leadingContent = { Icon(if (message.starred) Icons.Default.Star else Icons.Default.StarBorder, null) },
            modifier = Modifier.clickable(onClick = onStar),
        )
        message.effect?.let { effect ->
            ListItem(
                headlineContent = { Text("Rejouer l'effet « ${effect.label} »") },
                leadingContent = { Icon(Icons.Default.Replay, null) },
                modifier = Modifier.clickable(onClick = onReplay),
            )
        }
        if (message.status == MessageStatus.FAILED) {
            ListItem(
                headlineContent = { Text("Réessayer l'envoi") },
                leadingContent = { Icon(Icons.Default.Refresh, null) },
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
        ListItem(
            headlineContent = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable(onClick = onDelete),
        )
        Spacer(Modifier.height(24.dp))
    }
}
