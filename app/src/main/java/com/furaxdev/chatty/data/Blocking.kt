package com.furaxdev.chatty.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * Blocage via la liste système d'Android (partagée avec l'appli Téléphone).
 * Seule l'appli SMS par défaut (ou Téléphone) peut la modifier.
 */
object Blocking {
    fun canUse(context: Context) = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun isBlocked(context: Context, number: String) =
        runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)

    fun block(context: Context, number: String): Boolean = runCatching {
        val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null
    }.getOrDefault(false)

    fun unblock(context: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.unblock(context, number) > 0 }.getOrDefault(false)

    fun list(context: Context): List<String> = runCatching {
        context.contentResolver.query(
            BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.orEmpty()
    }.getOrDefault(emptyList())
}
