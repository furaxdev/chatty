package com.chatty.fr.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatty.fr.ChattyViewModel
import com.chatty.fr.data.Message
import com.chatty.fr.data.MessageFormat
import com.chatty.fr.data.ScheduledMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = { Text(title) },
            )
        },
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}

@Composable
private fun Empty(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier) {
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Tous les messages mis en favoris (⭐). */
@Composable
fun StarredScreen(vm: ChattyViewModel, onOpen: (Message) -> Unit, onBack: () -> Unit) {
    val version by vm.store.version.collectAsState()
    var items by remember { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(version) { items = vm.starredMessages() }
    SimpleScaffold("Messages favoris", onBack) { modifier ->
        val list = items
        if (list != null && list.isEmpty()) {
            Empty(Icons.Default.Star, "Aucun favori.\nRestez appuyé sur un message puis « Ajouter aux favoris ».", modifier)
        } else {
            LazyColumn(modifier) {
                items(list.orEmpty(), key = { it.id }) { m ->
                    val contact = remember(m.address) { vm.contact(m.address) }
                    ListItem(
                        headlineContent = { Text(if (m.isMine) "Vous → ${contact.displayName}" else contact.displayName) },
                        supportingContent = { Text(m.body, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(contact, 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { vm.store.setStarred(m.id, false) }) {
                                Icon(Icons.Default.Star, "Retirer", tint = androidx.compose.ui.graphics.Color(0xFFFFB300))
                            }
                        },
                        overlineContent = { Text(formatListDate(m.date)) },
                        modifier = Modifier.clickable { onOpen(m) },
                    )
                }
            }
        }
    }
}

/** Tous les messages programmés, toutes conversations confondues. */
@Composable
fun ScheduledScreen(vm: ChattyViewModel, onOpen: (ScheduledMessage) -> Unit, onBack: () -> Unit) {
    val version by vm.store.version.collectAsState()
    val items = remember(version) { vm.store.scheduled().sortedBy { it.sendAt } }
    SimpleScaffold("Messages programmés", onBack) { modifier ->
        if (items.isEmpty()) {
            Empty(Icons.Default.Schedule, "Aucun message programmé.\nRestez appuyé sur Envoyer puis « Programmer ».", modifier)
        } else {
            LazyColumn(modifier) {
                items(items, key = { it.id }) { s ->
                    val contact = remember(s.address) { vm.contact(s.address) }
                    ListItem(
                        overlineContent = { Text(formatScheduled(s.sendAt)) },
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = { Text(MessageFormat.decodeReply(s.body).second, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(contact, 40.dp) },
                        trailingContent = {
                            androidx.compose.foundation.layout.Row {
                                IconButton(onClick = { vm.sendScheduledNow(s.id) }) { Icon(Icons.AutoMirrored.Filled.Send, "Envoyer maintenant") }
                                IconButton(onClick = { vm.cancelScheduled(s.id) }) { Icon(Icons.Default.Close, "Annuler") }
                            }
                        },
                        modifier = Modifier.clickable { onOpen(s) },
                    )
                }
            }
        }
    }
}

/** Numéros bloqués (liste système partagée avec l'appli Téléphone). */
@Composable
fun BlockedScreen(vm: ChattyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var numbers by remember { mutableStateOf(vm.blockedNumbers()) }
    SimpleScaffold("Numéros bloqués", onBack) { modifier ->
        if (numbers.isEmpty()) {
            Empty(Icons.Default.Block, "Aucun numéro bloqué.\nBloquez un numéro depuis les détails d'une conversation.", modifier)
        } else {
            LazyColumn(modifier) {
                items(numbers, key = { it }) { n ->
                    val contact = remember(n) { vm.contact(n) }
                    ListItem(
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = { if (contact.name != null) Text(n) },
                        leadingContent = { Avatar(contact, 40.dp) },
                        trailingContent = {
                            TextButton(onClick = {
                                vm.unblock(n)
                                numbers = vm.blockedNumbers()
                                Toast.makeText(context, "Débloqué", Toast.LENGTH_SHORT).show()
                            }) { Text("Débloquer") }
                        },
                    )
                }
            }
        }
    }
}
