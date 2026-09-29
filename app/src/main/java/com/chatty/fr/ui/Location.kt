package com.chatty.fr.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import java.util.Locale

object LocationShare {
    /** Dernière position connue (GPS, réseau…), la plus récente. Nécessite la permission de localisation. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Texte lisible par n'importe quel téléphone : un lien Google Maps. */
    fun message(location: Location): String =
        "📍 Ma position : https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, location.latitude, location.longitude)
}
