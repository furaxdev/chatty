package com.furaxdev.chatty.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furaxdev.chatty.ChattyViewModel
import com.furaxdev.chatty.data.Contact
import kotlinx.coroutines.delay

/**
 * Choix du ou des destinataires. Avec plusieurs destinataires, Chatty fait un
 * envoi groupé (un SMS individuel à chacun).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewConversationScreen(
    vm: ChattyViewModel,
    onBack: () -> Unit,
    onStart: (List<Contact>) -> Unit,
    onStartGroup: (List<Contact>) -> Unit = onStart,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var dialpad by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Contact>>(emptyList()) }
    val picked = remember { mutableStateListOf<Contact>() }

    LaunchedEffect(query) {
        delay(150)
        results = vm.searchContacts(query)
    }

    val typedNumber = query.filter { it.isDigit() || it == '+' }
    val looksLikeNumber = typedNumber.length >= 3 && query.all { it.isDigit() || it in "+ -()." }

    fun pick(c: Contact) {
        if (picked.none { it.number == c.number }) picked += c
        query = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = { Text("Nouvelle discussion") },
                actions = {
                    if (picked.size > 1) {
                        TextButton(onClick = { onStart(picked.toList()) }) { Text("Envoi individuel") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (picked.isNotEmpty()) {
                if (picked.size > 1) {
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        onClick = { onStartGroup(picked.toList()) },
                        icon = { Icon(Icons.Default.Groups, null) },
                        text = { Text("Créer le groupe") },
                    )
                } else {
                    FloatingActionButton(onClick = { onStart(picked.toList()) }) { Icon(Icons.Default.Check, "Suivant") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (picked.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    picked.forEach { c ->
                        InputChip(
                            selected = true,
                            onClick = { picked.remove(c) },
                            label = { Text(c.displayName) },
                            avatar = { Avatar(c, 24.dp) },
                            trailingIcon = { Icon(Icons.Default.Close, "Retirer", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("À : nom, numéro de téléphone") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { dialpad = !dialpad }) { Icon(Icons.Default.Dialpad, "Clavier numérique") }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (looksLikeNumber) pick(Contact(null, typedNumber))
                    else results.firstOrNull()?.let(::pick)
                }),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                if (looksLikeNumber) {
                    item {
                        ListItem(
                            headlineContent = { Text("Envoyer à $typedNumber") },
                            leadingContent = { Avatar(Contact(null, typedNumber), 44.dp) },
                            modifier = Modifier.clickable { pick(Contact(null, typedNumber)) },
                        )
                    }
                }
                if (picked.size == 1 && query.isBlank()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Ajoutez des personnes pour créer un groupe (MMS)") },
                            leadingContent = { Icon(Icons.Default.Groups, null) },
                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
                items(results, key = { it.number }) { c ->
                    val isPicked = picked.any { it.number == c.number }
                    ListItem(
                        headlineContent = { Text(c.displayName) },
                        supportingContent = { Text(c.number) },
                        leadingContent = { Avatar(c, 44.dp) },
                        trailingContent = {
                            if (isPicked) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            if (isPicked) picked.removeAll { it.number == c.number } else pick(c)
                        },
                    )
                }
                if (results.isEmpty() && !looksLikeNumber) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("Aucun contact trouvé", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
