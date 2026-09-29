package com.chatty.fr.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatty.fr.ChattyViewModel
import com.chatty.fr.data.Conversation
import com.chatty.fr.data.Message
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    vm: ChattyViewModel,
    archivedMode: Boolean,
    onOpen: (Conversation) -> Unit,
    onOpenMessage: (Message) -> Unit,
    onNew: () -> Unit,
    onArchived: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onStarred: () -> Unit = {},
    onScheduled: () -> Unit = {},
) {
    val all by vm.conversations.collectAsState()
    val loaded by vm.loaded.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var messageHits by remember { mutableStateOf<List<Message>>(emptyList()) }
    val selection = remember { mutableStateListOf<Long>() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    LaunchedEffect(query) {
        delay(250)
        messageHits = if (query.length >= 2) vm.searchMessages(query) else emptyList()
    }

    val visible = remember(all, query, archivedMode) {
        all.filter { it.archived == archivedMode }
            .filter {
                query.isBlank() || it.contact.displayName.contains(query, true) ||
                    it.address.contains(query) || it.snippet.contains(query, true)
            }
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.date })
    }
    val archivedCount = remember(all) { all.count { it.archived } }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                val selected = all.filter { it.threadId in selection }
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { selection.clear() }) { Icon(Icons.Default.Close, "Annuler") } },
                    title = { Text("${selection.size}") },
                    actions = {
                        val allPinned = selected.all { it.pinned }
                        IconButton(onClick = { selected.forEach { vm.store.setPinned(it.threadId, !allPinned) }; selection.clear() }) {
                            Icon(Icons.Default.PushPin, if (allPinned) "Désépingler" else "Épingler")
                        }
                        IconButton(onClick = { selected.forEach { vm.store.setArchived(it.threadId, !archivedMode) }; selection.clear() }) {
                            Icon(if (archivedMode) Icons.Default.Unarchive else Icons.Default.Archive, "Archiver")
                        }
                        val anyUnread = selected.any { it.unreadCount > 0 }
                        IconButton(onClick = {
                            selected.forEach { if (anyUnread) vm.markRead(it.threadId) else vm.markUnread(it.threadId) }
                            selection.clear()
                        }) {
                            Icon(if (anyUnread) Icons.Default.MarkChatRead else Icons.Default.MarkChatUnread, "Lu / non lu")
                        }
                        IconButton(onClick = {
                            val allMuted = selected.all { it.muted }
                            selected.forEach { vm.store.setMuted(it.threadId, !allMuted) }; selection.clear()
                        }) { Icon(Icons.Default.NotificationsOff, "Sourdine") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
                    },
                )
            } else if (archivedMode) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                    title = { Text("Archives") },
                )
            } else {
                SearchHeader(
                    query = query,
                    onQuery = { query = it },
                    menu = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Archives" + if (archivedCount > 0) " ($archivedCount)" else "") },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                                    onClick = { menu = false; onArchived() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Messages favoris") },
                                    leadingIcon = { Icon(Icons.Default.Star, null) },
                                    onClick = { menu = false; onStarred() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Messages programmés") },
                                    leadingIcon = { Icon(Icons.Default.Schedule, null) },
                                    onClick = { menu = false; onScheduled() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Paramètres") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { menu = false; onSettings() },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!archivedMode && selection.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    expanded = expandedFab,
                    icon = { Icon(Icons.AutoMirrored.Outlined.Chat, null) },
                    text = { Text("Démarrer une discussion") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    ) { padding ->
        if (loaded && visible.isEmpty() && messageHits.isEmpty()) {
            EmptyState(
                Modifier.padding(padding),
                when {
                    query.isNotBlank() -> "Aucun résultat pour « $query »"
                    archivedMode -> "Aucune conversation archivée"
                    else -> "Aucune conversation pour l'instant.\nDémarrez-en une !"
                },
            )
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(visible, key = { it.threadId }) { conv ->
                val selected = conv.threadId in selection
                SwipeRow(
                    archivedMode = archivedMode,
                    onSwiped = { vm.store.setArchived(conv.threadId, !archivedMode) },
                ) {
                    ConversationRow(
                        conv = conv,
                        selected = selected,
                        onClick = {
                            if (selection.isNotEmpty()) {
                                if (selected) selection.remove(conv.threadId) else selection.add(conv.threadId)
                            } else onOpen(conv)
                        },
                        onLongClick = { if (!selected) selection.add(conv.threadId) },
                    )
                }
            }
            if (messageHits.isNotEmpty()) {
                item {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(messageHits, key = { "hit_${it.id}" }) { m ->
                    SearchHitRow(vm, m, query) { onOpenMessage(m) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ${selection.size} conversation(s) ?") },
            text = { Text("Les messages seront définitivement supprimés de ce téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    selection.forEach { vm.deleteThread(it) }
                    selection.clear(); confirmDelete = false
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, menu: @Composable () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Rechercher dans Chatty", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        }
                        inner()
                    },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Effacer") }
            }
            menu()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(archivedMode: Boolean, onSwiped: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) { onSwiped(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (archivedMode) Icons.Default.Unarchive else Icons.Default.Archive, null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(conv: Conversation, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val unread = conv.unreadCount > 0
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(conv.contact, 52.dp)
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.contact.displayName,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conv.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.PushPin, "Épinglée", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (conv.muted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.NotificationsOff, "Sourdine", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val prefix = if (conv.lastIsMine) "Vous : " else ""
            val effect = conv.snippetEffect?.let { "${it.emoji} " }.orEmpty()
            Text(
                prefix + effect + conv.snippet.replace('\n', ' '),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatListDate(conv.date), fontSize = 12.sp,
                color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
            )
            if (unread) {
                Spacer(Modifier.height(4.dp))
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("${conv.unreadCount}") }
            }
        }
    }
}

@Composable
private fun SearchHitRow(vm: ChattyViewModel, m: Message, query: String, onClick: () -> Unit) {
    val contact = remember(m.address) { vm.contact(m.address) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                highlight(m.body, query, MaterialTheme.colorScheme.primary),
                maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatListDate(m.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color) =
    androidx.compose.ui.text.buildAnnotatedString {
        val i = text.indexOf(query, ignoreCase = true)
        if (i < 0 || query.isBlank()) { append(text); return@buildAnnotatedString }
        val start = (i - 30).coerceAtLeast(0)
        if (start > 0) append("…")
        append(text.substring(start, i))
        pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.Bold))
        append(text.substring(i, i + query.length))
        pop()
        append(text.substring(i + query.length))
    }

@Composable
private fun EmptyState(modifier: Modifier, message: String) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
