package com.furaxdev.chatty.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furaxdev.chatty.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

private val AvatarColors = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFFFFA726), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFEC407A),
    Color(0xFF8D6E63), Color(0xFF7E57C2),
)

fun avatarColor(key: String): Color = AvatarColors[key.hashCode().absoluteValue % AvatarColors.size]

@Composable
fun Avatar(contact: Contact, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, contact.photoUri) {
        value = contact.photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                        BitmapFactory.decodeStream(it)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(contact.number)),
        contentAlignment = Alignment.Center,
    ) {
        val img = photo
        when {
            img != null -> Image(img, null, Modifier.size(size), contentScale = ContentScale.Crop)
            contact.number.contains(',') -> Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
            contact.name?.firstOrNull()?.isLetter() == true -> Text(
                contact.name.first().uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.42f).sp,
            )
            else -> Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun isSameDay(a: Long, b: Long) = sameDay(a, b)

fun formatTime(date: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(date))

/** Horodatage court pour la liste des conversations. */
fun formatListDate(date: Long): String {
    val now = System.currentTimeMillis()
    return when {
        now - date < DateUtils.MINUTE_IN_MILLIS -> "À l'instant"
        now - date < DateUtils.HOUR_IN_MILLIS -> "${(now - date) / DateUtils.MINUTE_IN_MILLIS} min"
        sameDay(now, date) -> formatTime(date)
        now - date < 6 * DateUtils.DAY_IN_MILLIS -> SimpleDateFormat("EEE", Locale.FRANCE).format(Date(date))
        else -> SimpleDateFormat("d MMM", Locale.FRANCE).format(Date(date))
    }
}

/** Séparateur de jour dans une conversation. */
fun formatDayHeader(date: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(now, date) -> "Aujourd'hui"
        sameDay(now - DateUtils.DAY_IN_MILLIS, date) -> "Hier"
        now - date < 6 * DateUtils.DAY_IN_MILLIS -> SimpleDateFormat("EEEE", Locale.FRANCE).format(Date(date))
            .replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date(date)).replaceFirstChar { it.uppercase() }
    }
}

fun formatScheduled(date: Long): String =
    SimpleDateFormat("EEE d MMM 'à' HH:mm", Locale.FRANCE).format(Date(date))
