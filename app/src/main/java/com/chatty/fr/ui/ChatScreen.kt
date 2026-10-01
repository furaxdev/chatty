package com.chatty.fr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.chatty.fr.ChattyViewModel
import com.chatty.fr.data.Message
import com.chatty.fr.data.MessageFormat
import com.chatty.fr.data.MessageStatus
import com.chatty.fr.data.ScheduledMessage
import com.chatty.fr.data.SimCards
import com.chatty.fr.effects.EffectKind
import com.chatty.fr.effects.EffectPicker
import com.chatty.fr.effects.MessageEffect
import com.chatty.fr.effects.ScreenEffectOverlay
import com.chatty.fr.ui.theme.BubblePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface ChatRow {
    val key: String
    data class Day(val date: Long) : ChatRow { override val key = "day_$date" }
    data class Unread(val count: Int) : ChatRow { override val key = "unread" }
    data class Msg(val m: Message, val first: Boolean, val last: Boolean) : ChatRow { override val key = "m_${m.id}" }
    data class Planned(val s: ScheduledMessage) : ChatRow { override val key = "s_${s.id}" }
    data class Pending(val p: ChattyViewModel.PendingSend) : ChatRow { override val key = "p_${p.id}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChattyViewModel,
    threadId: Long,
    address: String,
    initialText: String?,
    onBack: () -> Unit,
    onDetails: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = vm.store
    val version by store.version.collectAsState()
    val messages by vm.messages.collectAsState()
    val scheduled by vm.scheduled.collectAsState()
    val allPending by vm.pending.collectAsState()
    val pending = allPending.filter { it.threadId == threadId }
    val conversations by vm.conversations.collectAsState()
    val conv = conversations.firstOrNull { it.threadId == threadId }
    val contact = remember(address, conversations) { vm.contact(address) }
    val sims = remember { SimCards.active(context) }

    DisposableEffect(threadId) {
        vm.openThread(threadId)
        onDispose { vm.closeThread() }
    }

    var text by rememberSaveable(threadId) { mutableStateOf(initialText ?: store.draft(threadId)) }
    val latestText by rememberUpdatedState(text)
    DisposableEffect(threadId) { onDispose { store.setDraft(threadId, latestText) } }

    val palette = BubblePalette.getOrNull(remember(version) { store.colorFor(threadId) })
    val mineColor = palette ?: MaterialTheme.colorScheme.primary
    val onMineColor = if (palette != null) Color.White else MaterialTheme.colorScheme.onPrimary

    // --- Lecture des effets ---
    var screenEffect by remember { mutableStateOf<Pair<MessageEffect, String>?>(null) }
    val bubblePlays = remember { mutableStateMapOf<Long, Int>() }
    val revealed = remember { mutableStateMapOf<Long, Boolean>() }
    val details = remember { mutableStateMapOf<Long, Boolean>() }
    var knownIds by remember(threadId) { mutableStateOf<Set<Long>?>(null) }
    // Premier message non lu à l'ouverture (pour le séparateur « Nouveaux messages »).
    var unreadAnchor by remember(threadId) { mutableStateOf<Pair<Long, Int>?>(null) }

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
        if (known == null) {
            val unread = messages.filter { !it.isMine && !it.read }
            unread.firstOrNull()?.let { unreadAnchor = it.id to unread.size }
        }
        knownIds = messages.map { it.id }.toSet()
        if (store.autoPlayEffects) fresh.lastOrNull { it.effect != null }?.let(::play)
    }

    var showPicker by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Message?>(null) }
    var detailsOf by remember { mutableStateOf<Message?>(null) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var plannedAction by remember { mutableStateOf<ScheduledMessage?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var simPicker by remember { mutableStateOf(false) }
    var remindOf by remember { mutableStateOf<Message?>(null) }
    var menu by remember { mutableStateOf(false) }

    val isGroup = address.contains(',')
    val attachments = remember(threadId) { androidx.compose.runtime.mutableStateListOf<String>() }
    var viewing by remember { mutableStateOf<com.chatty.fr.data.Attachment?>(null) }
    val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris -> uris.forEach { if (attachments.size < 5) attachments += it.toString() } }

    fun insertLocation() {
        val loc = LocationShare.lastKnown(context)
        if (loc == null) {
            Toast.makeText(context, "Position indisponible : activez la localisation puis réessayez", Toast.LENGTH_LONG).show()
        } else {
            val msg = LocationShare.message(loc)
            text = if (text.isBlank()) msg else "${text.trimEnd()}\n$msg"
        }
    }
    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) insertLocation() }

    fun send(effect: MessageEffect?, overrideText: String? = null) {
        val body = (overrideText ?: text).trim()
        if (overrideText == null && attachments.isNotEmpty()) {
            vm.sendMedia(address, body, attachments.toList(), effect, threadId, replyTo)
            attachments.clear()
            replyTo = null
            text = ""
            store.setDraft(threadId, "")
            showPicker = false
            return
        }
        if (body.isEmpty()) return
        vm.send(address, body, effect, threadId, replyTo)
        replyTo = null
        if (overrideText == null) {
            text = ""
            store.setDraft(threadId, "")
        }
        showPicker = false
    }

    val rows = remember(messages, scheduled, pending, unreadAnchor) { buildRows(messages, scheduled, pending, unreadAnchor) }
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) { if (rows.isNotEmpty() && listState.firstVisibleItemIndex < 4) listState.animateScrollToItem(0) }
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    val lastIncoming = messages.lastOrNull()?.takeIf { !it.isMine }
    val suggestions = remember(lastIncoming?.id) { lastIncoming?.let { MessageFormat.suggestions(it.body) }.orEmpty() }

    fun scrollToQuote(quote: String) {
        val idx = rows.indexOfFirst { it is ChatRow.Msg && MessageFormat.matches(quote, it.m.body) }
        if (idx >= 0) scope.launch {
            listState.animateScrollToItem(idx)
            val m = (rows[idx] as ChatRow.Msg).m
            bubblePlays[m.id] = (bubblePlays[m.id] ?: 0) + 1
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    },
                    title = {
                        Row(
                            Modifier.clickable(onClick = onDetails),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(contact, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(store.nickname(address) ?: contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                                if (contact.name != null) {
                                    Text(address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$address".toUri()))
                        }) { Icon(Icons.Default.Call, "Appeler") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Plus") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                val pinned = conv?.pinned == true
                                val muted = conv?.muted == true
                                val archived = conv?.archived == true
                                DropdownMenuItem(
                                    text = { Text("Détails") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = { menu = false; onDetails() },
                                )
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
                                if (sims.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text("Choisir la SIM") },
                                        leadingIcon = { Icon(Icons.Default.SimCard, null) },
                                        onClick = { simPicker = true; menu = false },
                                    )
                                }
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
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is ChatRow.Day -> DayHeader(row.date)
                                is ChatRow.Unread -> UnreadDivider(row.count)
                                is ChatRow.Planned -> PlannedBubble(row.s) { plannedAction = row.s }
                                is ChatRow.Pending -> PendingBubble(
                                    row.p, mineColor, onMineColor,
                                    onUndo = { vm.cancelPending(row.p.id)?.let { restored -> if (text.isBlank()) text = restored } },
                                    onSendNow = { vm.sendPendingNow(row.p.id) },
                                )
                                is ChatRow.Msg -> MessageBubble(
                                    m = row.m,
                                    first = row.first,
                                    last = row.last,
                                    mineColor = mineColor,
                                    onMineColor = onMineColor,
                                    playKey = bubblePlays[row.m.id],
                                    revealed = revealed[row.m.id] == true,
                                    showDetails = details[row.m.id] == true,
                                    otherName = contact.displayName,
                                    onTap = {
                                        when {
                                            row.m.effect == MessageEffect.INVISIBLE_INK -> revealed[row.m.id] = revealed[row.m.id] != true
                                            row.m.status == MessageStatus.FAILED -> vm.retry(row.m.id)
                                            else -> details[row.m.id] = details[row.m.id] != true
                                        }
                                    },
                                    onLongPress = {
                                        if (store.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selected = row.m
                                    },
                                    onReply = { replyTo = row.m },
                                    onQuoteClick = ::scrollToQuote,
                                    onCopyCode = { code ->
                                        copy(context, code)
                                        Toast.makeText(context, "Code $code copié", Toast.LENGTH_SHORT).show()
                                    },
                                    senderName = if (isGroup && !row.m.isMine && row.m.address.isNotBlank()) vm.contact(row.m.address).displayName else null,
                                    onReplayEffect = { play(row.m) },
                                    onOpenAttachment = { a ->
                                        if (a.isImage) viewing = a
                                        else runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(shareableUri(context, a), a.contentType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            })
                                        }
                                    },
                                )
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        showJump,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) {
                        SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Aller en bas")
                        }
                    }
                }
                val simLabel = if (sims.size > 1) {
                    val chosen = store.simFor(threadId).also { version }
                    sims.firstOrNull { it.subId == chosen }?.let { "SIM ${it.slot + 1}" } ?: "SIM"
                } else null
                Composer(
                    text = text,
                    onTextChange = { text = it },
                    sendColor = mineColor,
                    onSendColor = onMineColor,
                    hapticsEnabled = store.haptics,
                    onSend = { send(null) },
                    onLongPressSend = { showPicker = true },
                    replyTo = replyTo?.body,
                    replyAuthor = replyTo?.let { if (it.isMine) "vous" else contact.displayName },
                    onCancelReply = { replyTo = null },
                    suggestions = suggestions,
                    onSuggestion = { send(null, it) },
                    recentEmojis = remember(version) { store.recentEmojis() },
                    onEmojiUsed = store::pushRecentEmoji,
                    simLabel = simLabel,
                    onSimClick = { simPicker = true },
                    attachments = attachments,
                    onAddAttachment = {
                        pickMedia.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onRemoveAttachment = { attachments.remove(it) },
                    onShareLocation = {
                        val has = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (has) insertLocation()
                        else locationPermission.launch(
                            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    mms = isGroup,
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

        viewing?.let { a -> ImageViewer(a) { viewing = null } }
    }

    if (showSchedule) {
        ScheduleDialog(
            onPick = { at ->
                val body = replyTo?.let { MessageFormat.encodeReply(it.body, text.trim()) } ?: text.trim()
                vm.schedule(address, threadId, body, null, at)
                text = ""
                replyTo = null
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
            onReact = { emoji -> vm.react(m, emoji, address); selected = null },
            onReply = { replyTo = m; selected = null },
            onCopy = { copy(context, m.body); selected = null },
            onShare = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.body)
                }, "Partager"))
                selected = null
            },
            onStar = { store.setStarred(m.id, !m.starred); selected = null },
            onReplay = { play(m); selected = null },
            onRetry = { vm.retry(m.id); selected = null },
            onDetails = { detailsOf = m; selected = null },
            onRemind = { remindOf = m; selected = null },
            onDelete = { vm.deleteMessage(m.id); selected = null },
            onDismiss = { selected = null },
        )
    }

    remindOf?.let { m ->
        ScheduleDialog(
            title = "Me le rappeler",
            onPick = { at ->
                vm.remind(m, address, at)
                remindOf = null
                Toast.makeText(context, "Rappel ${formatScheduled(at)}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { remindOf = null },
        )
    }

    detailsOf?.let { m ->
        val fmt = SimpleDateFormat("EEEE d MMMM yyyy 'à' HH:mm:ss", Locale.FRANCE)
        AlertDialog(
            onDismissRequest = { detailsOf = null },
            title = { Text("Détails du message") },
            text = {
                val segs = runCatching { com.chatty.fr.sms.SmsSender.segments(m.body) }.getOrNull()
                Text(buildString {
                    appendLine("Type : SMS")
                    appendLine(if (m.isMine) "À : $address" else "De : $address")
                    appendLine((if (m.isMine) "Envoyé le " else "Reçu le ") + fmt.format(Date(m.date)))
                    val status = when (m.status) {
                        MessageStatus.SENDING -> "En cours d'envoi"
                        MessageStatus.SENT -> "Envoyé"
                        MessageStatus.DELIVERED -> "Distribué"
                        MessageStatus.FAILED -> "Échec"
                        MessageStatus.RECEIVED -> "Reçu"
                    }
                    appendLine("Statut : $status")
                    m.effect?.let { appendLine("Effet : ${it.emoji} ${it.label}") }
                    if (segs != null) append("Taille : ${m.body.length} caractères · ${segs[0]} SMS")
                })
            },
            confirmButton = { TextButton(onClick = { detailsOf = null }) { Text("OK") } },
        )
    }

    if (simPicker) {
        val chosen = store.simFor(threadId)
        AlertDialog(
            onDismissRequest = { simPicker = false },
            title = { Text("Envoyer avec") },
            text = {
                Column {
                    (listOf(-1 to "SIM par défaut du téléphone") + sims.map { it.subId to "SIM ${it.slot + 1} · ${it.label}" })
                        .forEach { (id, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosen == id, onClick = { store.setSim(threadId, id); simPicker = false })
                                Text(label)
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = { simPicker = false }) { Text("Fermer") } },
        )
    }

    plannedAction?.let { s ->
        AlertDialog(
            onDismissRequest = { plannedAction = null },
            title = { Text("Message programmé") },
            text = { Text("« ${MessageFormat.decodeReply(s.body).second} »\n\nEnvoi prévu ${formatScheduled(s.sendAt)}.") },
            confirmButton = {
                TextButton(onClick = { vm.sendScheduledNow(s.id); plannedAction = null }) { Text("Envoyer maintenant") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.cancelScheduled(s.id)
                    if (text.isBlank()) text = MessageFormat.decodeReply(s.body).second
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

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}

private fun buildRows(
    messages: List<Message>,
    scheduled: List<ScheduledMessage>,
    pending: List<ChattyViewModel.PendingSend>,
    unreadAnchor: Pair<Long, Int>?,
): List<ChatRow> {
    val out = ArrayList<ChatRow>()
    messages.forEachIndexed { i, m ->
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        if (prev == null || !isSameDay(prev.date, m.date)) out += ChatRow.Day(m.date)
        if (unreadAnchor != null && m.id == unreadAnchor.first) out += ChatRow.Unread(unreadAnchor.second)
        val first = prev == null || prev.isMine != m.isMine || m.date - prev.date > 5 * 60_000 || !isSameDay(prev.date, m.date)
        val last = next == null || next.isMine != m.isMine || next.date - m.date > 5 * 60_000 || !isSameDay(next.date, m.date)
        out += ChatRow.Msg(m, first, last)
    }
    pending.forEach { out += ChatRow.Pending(it) }
    scheduled.forEach { out += ChatRow.Planned(it) }
    return out.asReversed()
}
