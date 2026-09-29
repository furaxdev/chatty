package com.chatty.fr.ui

import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.chatty.fr.ChattyViewModel
import com.chatty.fr.ui.theme.BubblePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Détails d'une conversation : surnom, couleur, notifications, blocage, export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailsScreen(vm: ChattyViewModel, threadId: Long, address: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = vm.store
    val version by store.version.collectAsState()
    val contact = remember(version) { vm.contact(address) }
    var editNick by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(vm.isBlocked(address)) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = exportText(vm, threadId, contact.displayName)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
            Toast.makeText(context, "Conversation exportée", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = { Text("Détails") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(contact, 96.dp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.displayName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { editNick = true }) { Icon(Icons.Default.Edit, "Surnom", Modifier.size(18.dp)) }
                }
                if (contact.name != null) Text(address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Action(Icons.Default.Call, "Appeler") {
                        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$address".toUri()))
                    }
                    Action(Icons.Default.PersonAdd, if (contact.name != null) "Contact" else "Ajouter") {
                        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, address)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                    Action(Icons.Default.Share, "Partager") {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${contact.displayName} : $address")
                        }, "Partager le contact"))
                    }
                }
            }
            HorizontalDivider()

            Text(
                "Couleur des bulles dans cette conversation",
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val custom = store.hasCustomColor(threadId)
                val current = store.colorFor(threadId)
                ColorDot(null, !custom, label = "Auto") { store.setColorFor(threadId, null) }
                BubblePalette.forEachIndexed { i, c ->
                    ColorDot(c ?: MaterialTheme.colorScheme.primary, custom && current == i) { store.setColorFor(threadId, i) }
                }
            }

            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Épingler la conversation") },
                trailingContent = { Switch(store.isPinned(threadId), { store.setPinned(threadId, it) }) },
            )
            ListItem(
                headlineContent = { Text("Notifications") },
                supportingContent = { Text(if (store.isMuted(threadId)) "En sourdine" else "Activées") },
                trailingContent = { Switch(!store.isMuted(threadId), { store.setMuted(threadId, !it) }) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Exporter la conversation") },
                supportingContent = { Text("Enregistre tous les messages dans un fichier texte") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier.clickable {
                    val name = contact.displayName.replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                    exportLauncher.launch("Chatty - $name.txt")
                },
            )
            ListItem(
                headlineContent = { Text(if (blocked) "Débloquer $address" else "Bloquer et signaler comme spam") },
                leadingContent = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    if (blocked) {
                        vm.unblock(address); blocked = false
                        Toast.makeText(context, "Numéro débloqué", Toast.LENGTH_SHORT).show()
                    } else confirmBlock = true
                },
            )
            ListItem(
                headlineContent = { Text("Supprimer la conversation", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmDelete = true },
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editNick) {
        var value by remember { mutableStateOf(store.nickname(address).orEmpty()) }
        AlertDialog(
            onDismissRequest = { editNick = false },
            title = { Text("Surnom") },
            text = {
                OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text(contact.displayName) })
            },
            confirmButton = { TextButton(onClick = { store.setNickname(address, value); vm.refresh(); editNick = false }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { store.setNickname(address, null); vm.refresh(); editNick = false }) { Text("Retirer") } },
        )
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Bloquer $address ?") },
            text = { Text("Vous ne recevrez plus d'appels ni de messages de ce numéro. La conversation sera archivée.") },
            confirmButton = {
                TextButton(onClick = {
                    blocked = vm.block(address, threadId)
                    confirmBlock = false
                    Toast.makeText(context, if (blocked) "Numéro bloqué" else "Blocage impossible", Toast.LENGTH_SHORT).show()
                }) { Text("Bloquer") }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Annuler") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette conversation ?") },
            text = { Text("Tous les messages avec ${contact.displayName} seront supprimés de ce téléphone.") },
            confirmButton = { TextButton(onClick = { vm.deleteThread(threadId); confirmDelete = false; onDeleted() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private suspend fun exportText(vm: ChattyViewModel, threadId: Long, name: String): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    val messages = vm.allMessages(threadId)
    return buildString {
        appendLine("Conversation avec $name — exportée depuis Chatty")
        appendLine()
        messages.forEach { m ->
            append('[').append(fmt.format(Date(m.date))).append("] ")
            append(if (m.isMine) "Moi" else name).append(" : ")
            m.quote?.let { append("(en réponse à « $it ») ") }
            append(m.body)
            if (m.reactions.isNotEmpty()) append("  [").append(m.reactions.joinToString(" ") { it.emoji }).append(']')
            appendLine()
        }
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) { Icon(icon, label) }
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ColorDot(color: Color?, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            label != null -> Text(label, fontSize = 10.sp)
            selected -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
