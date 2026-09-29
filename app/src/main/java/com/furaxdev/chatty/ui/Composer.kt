package com.furaxdev.chatty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furaxdev.chatty.sms.SmsSender
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    sendColor: Color,
    onSendColor: Color,
    hapticsEnabled: Boolean,
    onSend: () -> Unit,
    onLongPressSend: () -> Unit,
    modifier: Modifier = Modifier,
    replyTo: String? = null,
    replyAuthor: String? = null,
    onCancelReply: () -> Unit = {},
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    recentEmojis: List<String> = emptyList(),
    onEmojiUsed: (String) -> Unit = {},
    simLabel: String? = null,
    onSimClick: () -> Unit = {},
    attachments: List<String> = emptyList(),
    onAddAttachment: (() -> Unit)? = null,
    onRemoveAttachment: (String) -> Unit = {},
    mms: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    val isMms = mms || attachments.isNotEmpty()
    val segments = remember(text) { if (text.isEmpty()) null else runCatching { SmsSender.segments(text) }.getOrNull() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Le bouton gonfle pendant l'appui, comme sur iMessage, pour signaler l'appui long.
    val pressScale by animateFloatAsState(if (pressed && canSend) 1.18f else 1f, tween(350), label = "press")
    var emojiOpen by rememberSaveable { mutableStateOf(false) }

    // Valeur interne avec curseur, pour insérer les emojis au bon endroit.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))

    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(suggestions.isNotEmpty() && text.isEmpty() && replyTo == null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s ->
                    SuggestionChip(onClick = { onSuggestion(s) }, label = { Text(s) }, shape = RoundedCornerShape(18.dp))
                }
            }
        }
        AnimatedVisibility(replyTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(sendColor))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text("Réponse à ${replyAuthor.orEmpty()}", fontSize = 12.sp, color = sendColor, fontWeight = FontWeight.SemiBold)
                    Text(replyTo.orEmpty(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCancelReply) { Icon(Icons.Default.Close, "Annuler la réponse", Modifier.size(18.dp)) }
            }
        }
        AnimatedVisibility(attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { uri ->
                    Box {
                        val bmp = rememberBitmap(uri, 240)
                        Box(
                            Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            if (bmp != null) androidx.compose.foundation.Image(
                                bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(84.dp),
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onRemoveAttachment(uri) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Close, "Retirer", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            if (onAddAttachment != null) {
                IconButton(onClick = onAddAttachment, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, "Ajouter une photo", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = {
                    emojiOpen = !emojiOpen
                    if (emojiOpen) keyboard?.hide() else keyboard?.show()
                }) {
                    Icon(
                        if (emojiOpen) Icons.Default.Keyboard else Icons.Outlined.EmojiEmotions,
                        if (emojiOpen) "Clavier" else "Emojis",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = { field = it; if (it.text != text) onTextChange(it.text) },
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 13.dp)
                        .heightIn(min = 22.dp)
                        .onFocusChanged { if (it.isFocused) emojiOpen = false },
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(if (isMms) "Message MMS" else "Message SMS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        inner()
                    },
                )
                if (simLabel != null) {
                    Text(
                        simLabel,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 14.dp, start = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onSimClick)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(isMms || (segments != null && (segments[0] > 1 || segments[2] < 20))) {
                    val s = segments
                    Text(
                        when {
                            isMms -> "MMS"
                            s!![0] > 1 -> "${s[2]} · ${s[0]} SMS"
                            else -> "${s[2]}"
                        },
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .scale(pressScale)
                        .clip(CircleShape)
                        .background(if (canSend) sendColor else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = canSend,
                            onClick = onSend,
                            onLongClick = {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPressSend()
                            },
                            onLongClickLabel = "Envoyer avec effet",
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, "Envoyer (appui long : effets)",
                        tint = if (canSend) onSendColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    if (pressed && canSend) {
                        Icon(
                            Icons.Default.AutoAwesome, null, tint = onSendColor,
                            modifier = Modifier.size(12.dp).align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(emojiOpen) {
            EmojiPanel(recentEmojis, onPick = { emoji ->
                val sel = field.selection
                val newText = field.text.replaceRange(sel.min, sel.max, emoji)
                field = TextFieldValue(newText, TextRange(sel.min + emoji.length))
                onTextChange(newText)
                onEmojiUsed(emoji)
            })
        }
    }
}

/** Choix de l'heure d'envoi (raccourcis + date/heure libre). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit, title: String = "Programmer l'envoi") {
    var step by remember { mutableIntStateOf(0) } // 0 = raccourcis, 1 = date, 2 = heure
    var pickedDay by remember { mutableStateOf<Long?>(null) }

    fun at(dayOffset: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    when (step) {
        0 -> {
            val now = System.currentTimeMillis()
            val options = buildList {
                if (now + 3_600_000 < at(0, 23)) add("Dans 1 heure" to now + 3_600_000)
                if (at(0, 18) > now) add("Plus tard aujourd'hui" to at(0, 18))
                if (at(0, 21) > now) add("Ce soir" to at(0, 21))
                add("Demain matin" to at(1, 8))
                add("Demain après-midi" to at(1, 14))
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column {
                        options.forEach { (label, time) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(formatScheduled(time)) },
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(time) },
                            )
                        }
                        ListItem(
                            headlineContent = { Text("Choisir la date et l'heure") },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { step = 1 },
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
            )
        }
        1 -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = { pickedDay = state.selectedDateMillis; step = 2 }) { Text("Suivant") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
            ) { DatePicker(state) }
        }
        else -> {
            val now = Calendar.getInstance()
            val state = rememberTimePickerState(now.get(Calendar.HOUR_OF_DAY), (now.get(Calendar.MINUTE) + 5) % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Heure d'envoi") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        // Le DatePicker renvoie minuit UTC du jour choisi.
                        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pickedDay ?: System.currentTimeMillis()
                        }
                        val local = Calendar.getInstance().apply {
                            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), state.hour, state.minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPick(local.timeInMillis.coerceAtLeast(System.currentTimeMillis() + 60_000))
                    }) { Text("Programmer") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
            )
        }
    }
}
