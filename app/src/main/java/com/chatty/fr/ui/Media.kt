@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.chatty.fr.ui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chatty.fr.data.Attachment
import com.chatty.fr.mms.MediaUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberBitmap(uri: String, targetPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri, targetPx) {
        value = try {
            withContext(Dispatchers.IO) {
                runCatching { MediaUtils.loadThumbnail(context, Uri.parse(uri), targetPx)?.asImageBitmap() }
                    .getOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    return bitmap
}

/** Pièce jointe affichée dans une bulle. */
@Composable
fun AttachmentView(attachment: Attachment, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    when {
        attachment.isImage -> {
            val bmp = rememberBitmap(attachment.uri, 720)
            Box(
                Modifier
                    .widthIn(max = 260.dp)
                    .heightIn(min = 120.dp, max = 340.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickableCompat(onOpen, onLongPress),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) Image(bmp, "Photo", contentScale = ContentScale.Crop, modifier = Modifier.widthIn(max = 260.dp))
                else CircularProgressIndicator(Modifier.size(28.dp).padding(4.dp))
            }
        }
        else -> {
            val (icon, label) = when {
                attachment.isVideo -> Icons.Default.PlayCircle to "Vidéo"
                attachment.isAudio -> Icons.Default.Audiotrack to "Message audio"
                else -> Icons.Default.ContactPage to "Carte de contact"
            }
            Row(
                Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickableCompat({ openExternally(context, attachment) }, onLongPress)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(label, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

/**
 * Copie la pièce jointe dans le cache et renvoie une URI partageable (FileProvider) :
 * les autres applis n'ont pas accès directement à la base MMS.
 */
fun shareableUri(context: android.content.Context, a: Attachment): Uri? = runCatching {
    val ext = a.contentType.substringAfter('/').substringBefore(';').replace("jpeg", "jpg").ifBlank { "bin" }
    val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
    val file = java.io.File(dir, "chatty_${a.uri.hashCode().toUInt()}.$ext")
    if (!file.exists()) {
        context.contentResolver.openInputStream(Uri.parse(a.uri))?.use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
}.getOrNull()

private fun openExternally(context: android.content.Context, a: Attachment) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(shareableUri(context, a) ?: Uri.parse(a.uri), a.contentType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Aucune appli pour ouvrir ce fichier", Toast.LENGTH_SHORT).show() }
}

/** Visionneuse plein écran (pincer pour zoomer). */
@Composable
fun ImageViewer(attachment: Attachment, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val bmp = rememberBitmap(attachment.uri, 2048)
    // Android 8-9 : enregistrer dans la galerie demande la permission de stockage.
    val storagePermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) saveToGallery(context, attachment) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bmp, "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )
        } else CircularProgressIndicator()
        Row(Modifier.fillMaxWidth().systemBarsPadding().align(Alignment.TopCenter).padding(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fermer", tint = Color.White) }
            Box(Modifier.weight(1f))
            IconButton(onClick = {
                val needsPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needsPermission) storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else saveToGallery(context, attachment)
            }) { Icon(Icons.Default.Download, "Enregistrer", tint = Color.White) }
            IconButton(onClick = {
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = attachment.contentType
                    putExtra(Intent.EXTRA_STREAM, shareableUri(context, attachment) ?: Uri.parse(attachment.uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Partager la photo"))
                }.onFailure {
                    Toast.makeText(context, "Impossible de partager la photo", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Default.Share, "Partager", tint = Color.White) }
        }
    }
}

private fun saveToGallery(context: android.content.Context, a: Attachment) {
    runCatching {
        val ext = a.contentType.substringAfter('/').substringBefore(';').ifBlank { "jpg" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Chatty_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, a.contentType)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Chatty")
            }
        }
        val target = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert")
        context.contentResolver.openInputStream(Uri.parse(a.uri))?.use { input ->
            context.contentResolver.openOutputStream(target)?.use { input.copyTo(it) }
        }
    }.onSuccess { Toast.makeText(context, "Enregistrée dans Images/Chatty", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(context, "Impossible d'enregistrer la photo", Toast.LENGTH_SHORT).show() }
}
