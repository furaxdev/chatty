package com.chatty.fr.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

object MediaUtils {

    /**
     * Prépare une image pour un MMS : redimensionnée et compressée en JPEG pour
     * tenir sous [maxBytes] (limite de l'opérateur).
     */
    fun compressImage(context: Context, uri: Uri, maxBytes: Int): Pdu.Attachment? = runCatching {
        val type = context.contentResolver.getType(uri).orEmpty()
        // Les GIF animés sont envoyés tels quels s'ils rentrent.
        if (type == "image/gif") {
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (raw != null && raw.size <= maxBytes) return@runCatching Pdu.Attachment("image/gif", raw, "image.gif")
        }
        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1600) {
                    val scale = 1600f / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            // Android 8 : décodage sous-échantillonné puis rotation EXIF ignorée.
            loadThumbnail(context, uri, 1600) ?: error("image illisible")
        }
        var quality = 85
        var bytes = encode(bitmap, quality)
        while (bytes.size > maxBytes) {
            if (quality > 45) {
                quality -= 10
            } else {
                bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.75f).toInt().coerceAtLeast(1), (bitmap.height * 0.75f).toInt().coerceAtLeast(1), true)
            }
            bytes = encode(bitmap, quality)
            if (bitmap.width < 200) break
        }
        Pdu.Attachment("image/jpeg", bytes, "image_${System.currentTimeMillis() % 100000}.jpg")
    }.getOrNull()

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Décode une image avec sous-échantillonnage (miniatures dans les bulles). */
    fun loadThumbnail(context: Context, uri: Uri, targetPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
