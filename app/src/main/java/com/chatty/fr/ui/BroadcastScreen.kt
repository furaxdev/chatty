package com.chatty.fr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatty.fr.ChattyViewModel
import com.chatty.fr.data.Contact
import com.chatty.fr.effects.EffectPicker
import com.chatty.fr.effects.MessageEffect
import com.chatty.fr.ui.theme.BubblePalette

/** Envoi groupé : le même SMS envoyé individuellement à plusieurs contacts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastScreen(vm: ChattyViewModel, recipients: List<Contact>, initialText: String?, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val version by vm.store.version.collectAsState()
    var text by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    var picker by rememberSaveable { mutableStateOf(false) }
    val palette = BubblePalette.getOrNull(vm.store.bubbleColor.also { version })
    val color = palette ?: MaterialTheme.colorScheme.primary
    val onColor = if (palette != null) Color.White else MaterialTheme.colorScheme.onPrimary

    fun send(effect: MessageEffect?) {
        if (text.isBlank()) return
        vm.sendToMany(recipients.map { it.number }, text.trim(), effect)
        Toast.makeText(context, "Envoyé à ${recipients.size} contacts", Toast.LENGTH_SHORT).show()
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                    title = {
                        Column {
                            Text("Envoi groupé")
                            Text(
                                recipients.joinToString { it.displayName },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Text(
                    "Chaque destinataire recevra un SMS individuel : les réponses arriveront dans des conversations séparées.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp).weight(1f),
                )
                Composer(
                    text = text,
                    onTextChange = { text = it },
                    sendColor = color,
                    onSendColor = onColor,
                    hapticsEnabled = vm.store.haptics,
                    onSend = { send(null) },
                    onLongPressSend = { picker = true },
                )
            }
        }
        if (picker) {
            EffectPicker(
                text = text, bubbleColor = color, onBubbleColor = onColor,
                onSend = { picker = false; send(it) },
                onSchedule = {
                    picker = false
                    Toast.makeText(context, "La programmation est disponible dans une conversation individuelle", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { picker = false },
            )
        }
    }
}
