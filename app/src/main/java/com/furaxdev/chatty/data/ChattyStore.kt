package com.furaxdev.chatty.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Données propres à Chatty qui n'existent pas dans le fournisseur SMS :
 * épinglage, archives, sourdine, réactions, favoris, brouillons, envois programmés, réglages.
 */
class ChattyStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("chatty", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** Incrémenté à chaque modification pour rafraîchir l'interface. */
    val version: StateFlow<Int> = _version

    private fun bump() { _version.value++ }

    private fun ids(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    private fun toggle(key: String, id: Long, on: Boolean) {
        val set = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        if (on) set += id.toString() else set -= id.toString()
        prefs.edit { putStringSet(key, set) }
        bump()
    }

    fun isPinned(threadId: Long) = threadId in ids("pinned")
    fun setPinned(threadId: Long, on: Boolean) = toggle("pinned", threadId, on)

    fun isArchived(threadId: Long) = threadId in ids("archived")
    fun setArchived(threadId: Long, on: Boolean) = toggle("archived", threadId, on)

    fun isMuted(threadId: Long) = threadId in ids("muted")
    fun setMuted(threadId: Long, on: Boolean) = toggle("muted", threadId, on)

    fun isStarred(messageId: Long) = messageId in ids("starred")
    fun setStarred(messageId: Long, on: Boolean) = toggle("starred", messageId, on)

    fun starredIds(): Set<Long> = ids("starred")

    fun reaction(messageId: Long): String? = prefs.getString("reaction_$messageId", null)
    fun setReaction(messageId: Long, emoji: String?) {
        prefs.edit { if (emoji == null) remove("reaction_$messageId") else putString("reaction_$messageId", emoji) }
        bump()
    }

    fun draft(threadId: Long): String = prefs.getString("draft_$threadId", "").orEmpty()
    fun setDraft(threadId: Long, text: String) {
        prefs.edit { if (text.isBlank()) remove("draft_$threadId") else putString("draft_$threadId", text) }
    }

    /** Couleur de bulle propre à une conversation (sinon la couleur globale). */
    fun colorFor(threadId: Long): Int = prefs.getInt("color_$threadId", bubbleColor)
    fun hasCustomColor(threadId: Long) = prefs.contains("color_$threadId")
    fun setColorFor(threadId: Long, index: Int?) {
        prefs.edit { if (index == null) remove("color_$threadId") else putInt("color_$threadId", index) }
        bump()
    }

    /** Surnom donné à un contact dans Chatty. */
    fun nickname(address: String): String? = prefs.getString("nick_$address", null)
    fun setNickname(address: String, name: String?) {
        prefs.edit { if (name.isNullOrBlank()) remove("nick_$address") else putString("nick_$address", name.trim()) }
        bump()
    }

    /** SIM choisie pour une conversation (-1 = SIM par défaut du téléphone). */
    fun simFor(threadId: Long): Int = prefs.getInt("sim_$threadId", -1)
    fun setSim(threadId: Long, subId: Int) {
        prefs.edit { if (subId < 0) remove("sim_$threadId") else putInt("sim_$threadId", subId) }
        bump()
    }

    fun recentEmojis(): List<String> =
        prefs.getString("recent_emojis", "")!!.split('\u0001').filter { it.isNotEmpty() }

    fun pushRecentEmoji(emoji: String) {
        val list = (listOf(emoji) + recentEmojis().filterNot { it == emoji }).take(24)
        prefs.edit { putString("recent_emojis", list.joinToString("\u0001")) }
    }

    // --- Envois programmés ---

    fun scheduled(): List<ScheduledMessage> {
        val arr = JSONArray(prefs.getString("scheduled", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScheduledMessage(
                id = o.getString("id"),
                address = o.getString("address"),
                threadId = o.getLong("threadId"),
                body = o.getString("body"),
                effectName = o.optString("effect").ifBlank { null },
                sendAt = o.getLong("sendAt"),
            )
        }
    }

    fun scheduledFor(threadId: Long) = scheduled().filter { it.threadId == threadId }.sortedBy { it.sendAt }

    fun addScheduled(msg: ScheduledMessage) = saveScheduled(scheduled() + msg)

    fun removeScheduled(id: String) = saveScheduled(scheduled().filterNot { it.id == id })

    private fun saveScheduled(list: List<ScheduledMessage>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("address", it.address); put("threadId", it.threadId)
                put("body", it.body); put("effect", it.effectName ?: ""); put("sendAt", it.sendAt)
            })
        }
        prefs.edit { putString("scheduled", arr.toString()) }
        bump()
    }

    // --- Réglages ---

    var bubbleColor: Int
        get() = prefs.getInt("bubble_color", 0)
        set(value) { prefs.edit { putInt("bubble_color", value) }; bump() }

    /** Voir THEME_* dans ui.theme. */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(value) { prefs.edit { putInt("theme_mode", value) }; bump() }

    /** Taille du texte (1 = normale). */
    var textScale: Float
        get() = prefs.getFloat("text_scale", 1f)
        set(value) { prefs.edit { putFloat("text_scale", value) }; bump() }

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(value) { prefs.edit { putBoolean("dynamic_color", value) }; bump() }

    var deliveryReports: Boolean
        get() = prefs.getBoolean("delivery_reports", true)
        set(value) { prefs.edit { putBoolean("delivery_reports", value) }; bump() }

    var autoPlayEffects: Boolean
        get() = prefs.getBoolean("auto_play_effects", true)
        set(value) { prefs.edit { putBoolean("auto_play_effects", value) }; bump() }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit { putBoolean("haptics", value) }; bump() }

    /** Envoie les réactions par SMS (« A réagi avec ❤️ à … »), comme Google Messages. */
    var sendReactions: Boolean
        get() = prefs.getBoolean("send_reactions", true)
        set(value) { prefs.edit { putBoolean("send_reactions", value) }; bump() }

    /** Délai pendant lequel on peut annuler un envoi (0 = désactivé). */
    var undoDelaySeconds: Int
        get() = prefs.getInt("undo_delay", 0)
        set(value) { prefs.edit { putInt("undo_delay", value) }; bump() }

    /** Verrouillage de l'appli par empreinte / code du téléphone. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(value) { prefs.edit { putBoolean("app_lock", value) }; bump() }

    /** Masque le contenu des messages dans les notifications. */
    var privateNotifications: Boolean
        get() = prefs.getBoolean("private_notifications", false)
        set(value) { prefs.edit { putBoolean("private_notifications", value) }; bump() }

    var signature: String
        get() = prefs.getString("signature", "").orEmpty()
        set(value) { prefs.edit { putString("signature", value) }; bump() }

    companion object {
        @Volatile private var instance: ChattyStore? = null
        fun get(context: Context): ChattyStore =
            instance ?: synchronized(this) { instance ?: ChattyStore(context.applicationContext).also { instance = it } }
    }
}
