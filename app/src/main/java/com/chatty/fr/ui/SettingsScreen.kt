package com.chatty.fr.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chatty.fr.data.ChattyStore
import com.chatty.fr.ui.theme.BubblePalette
import com.chatty.fr.ui.theme.THEME_BLACK
import com.chatty.fr.ui.theme.THEME_DARK
import com.chatty.fr.ui.theme.THEME_LIGHT
import com.chatty.fr.ui.theme.THEME_SYSTEM
import androidx.compose.foundation.horizontalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: ChattyStore, onBack: () -> Unit, onBlocked: () -> Unit = {}, lockAvailable: Boolean = false) {
    val version by store.version.collectAsState()
    var signature by remember { mutableStateOf(store.signature) }
    val latestSignature by androidx.compose.runtime.rememberUpdatedState(signature)
    DisposableEffect(Unit) { onDispose { if (store.signature != latestSignature) store.signature = latestSignature } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = { Text("Paramètres") },
            )
        },
    ) { padding ->
        // `version` force la relecture des réglages après chaque modification.
        key(version) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                Section("Apparence")
                Text(
                    "Couleur de mes bulles",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BubblePalette.forEachIndexed { i, color ->
                        val selected = store.bubbleColor == i
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color ?: MaterialTheme.colorScheme.primary)
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { store.bubbleColor = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Toggle("Couleurs Material You", "Suit le fond d'écran de votre téléphone", store.dynamicColor) { store.dynamicColor = it }
                }
                ChipRow(
                    "Thème",
                    listOf(THEME_SYSTEM to "Système", THEME_LIGHT to "Clair", THEME_DARK to "Sombre", THEME_BLACK to "Noir AMOLED"),
                    store.themeMode,
                ) { store.themeMode = it }
                ChipRow(
                    "Taille du texte",
                    listOf(0.9f to "Petit", 1f to "Normal", 1.15f to "Grand", 1.3f to "Très grand"),
                    store.textScale,
                ) { store.textScale = it }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Section("Effets")
                Toggle(
                    "Lecture automatique des effets",
                    "Rejoue les effets (confettis, lasers…) à la réception",
                    store.autoPlayEffects,
                ) { store.autoPlayEffects = it }
                Toggle(
                    "Compatibilité iPhone",
                    "Ajoute un lien pour que les iPhone (et téléphones sans Chatty) voient l'animation",
                    store.iPhoneEffects,
                ) { store.iPhoneEffects = it }
                Toggle(
                    "Effets par mots-clés",
                    "Comme iMessage : « Joyeux anniversaire » 🎈, « Félicitations » 🎊, « Bonne année » 🎆, « pew pew » 🪩",
                    store.keywordEffects,
                ) { store.keywordEffects = it }
                Toggle("Vibrations", "Retour haptique à l'appui long", store.haptics) { store.haptics = it }
                ListItem(
                    headlineContent = { Text("Astuce") },
                    supportingContent = {
                        Text("Restez appuyé sur le bouton Envoyer pour choisir un effet de bulle ou d'écran, comme sur iMessage. Les autres téléphones reçoivent le texte normal.")
                    },
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Section("SMS")
                Toggle(
                    "Envoyer les réactions par SMS",
                    "Comme Google Messages : « A réagi avec ❤️ à … ». Sinon, elles restent sur ce téléphone",
                    store.sendReactions,
                ) { store.sendReactions = it }
                Text(
                    "Délai d'annulation d'envoi",
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "Aucun", 3 to "3 s", 5 to "5 s", 10 to "10 s").forEach { (sec, label) ->
                        FilterChip(
                            selected = store.undoDelaySeconds == sec,
                            onClick = { store.undoDelaySeconds = sec },
                            label = { Text(label) },
                        )
                    }
                }
                Toggle("Accusés de réception", "Affiche « Distribué » quand le SMS est arrivé", store.deliveryReports) { store.deliveryReports = it }
                OutlinedTextField(
                    value = signature,
                    onValueChange = { signature = it },
                    label = { Text("Signature (ajoutée à chaque message)") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Section("Confidentialité")
                if (lockAvailable) {
                    Toggle("Verrouiller Chatty", "Empreinte, visage ou code du téléphone à l'ouverture", store.appLock) { store.appLock = it }
                }
                Toggle(
                    "Supprimer les codes après 24 h",
                    "Efface automatiquement les SMS de code de vérification",
                    store.autoDeleteOtp,
                ) { store.autoDeleteOtp = it }
                Toggle(
                    "Notifications discrètes",
                    "Affiche « Nouveau message » sans le contenu",
                    store.privateNotifications,
                ) { store.privateNotifications = it }
                ListItem(
                    headlineContent = { Text("Numéros bloqués") },
                    supportingContent = { Text("Gérer les numéros bloqués") },
                    modifier = Modifier.clickable(onClick = onBlocked),
                )
                ListItem(
                    headlineContent = { Text("Chatty") },
                    supportingContent = { Text("Version 1.0.0 · par FuraxDev") },
                )
            }
        }
    }
}

@Composable
private fun <T> ChipRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, modifier = Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
