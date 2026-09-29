package com.furaxdev.chatty.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimCard(val subId: Int, val label: String, val slot: Int, val color: Int)

object SimCards {
    /** Cartes SIM actives (vide si la permission n'est pas accordée ou s'il n'y en a qu'une). */
    fun active(context: Context): List<SimCard> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map {
                SimCard(
                    subId = it.subscriptionId,
                    label = it.displayName?.toString()?.ifBlank { null } ?: "SIM ${it.simSlotIndex + 1}",
                    slot = it.simSlotIndex,
                    color = it.iconTint,
                )
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }
}
