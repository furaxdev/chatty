package com.furaxdev.chatty.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furaxdev.chatty.effects.MessageEffect
import com.furaxdev.chatty.effects.ScreenEffectOverlay

@Composable
fun OnboardingScreen(onMakeDefault: () -> Unit) {
    var demo by remember { mutableStateOf<MessageEffect?>(MessageEffect.CONFETTI) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3D5AFE), Color(0xFF6C3DF4)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
            Spacer(Modifier.height(24.dp))
            Text("Bienvenue sur Chatty", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Vos SMS, en mieux : effets façon iMessage, envoi programmé, réactions, épinglage et plus encore.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Feature("🎊", "Restez appuyé sur Envoyer pour ajouter un effet")
            Feature("⏰", "Programmez vos messages")
            Feature("📌", "Épinglez, archivez, mettez en sourdine")
            Feature("❤️", "Réagissez aux messages")
            Spacer(Modifier.height(32.dp))
            Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Définir Chatty comme appli SMS par défaut")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Android exige qu'une appli soit l'appli SMS par défaut pour envoyer et recevoir des messages. Vous pourrez revenir en arrière à tout moment dans les réglages.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        demo?.let { ScreenEffectOverlay(it, "Chatty") { demo = null } }
    }
}

@Composable
private fun Feature(emoji: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Text(text)
    }
}

/** Écran affiché tant que Chatty est verrouillé. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Chatty est verrouillé", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("Déverrouiller") }
    }
}
