package com.chatty.fr.data

import com.chatty.fr.effects.MessageEffect

data class Contact(
    val name: String?,
    val number: String,
    val photoUri: String? = null,
) {
    val displayName: String get() = name ?: number
}

data class Conversation(
    val threadId: Long,
    val address: String,
    val contact: Contact,
    val snippet: String,
    val snippetEffect: MessageEffect?,
    val date: Long,
    val unreadCount: Int,
    val lastIsMine: Boolean,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
)

enum class MessageStatus { RECEIVED, SENDING, SENT, DELIVERED, FAILED }

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val effect: MessageEffect?,
    val date: Long,
    val isMine: Boolean,
    val status: MessageStatus,
    val read: Boolean,
    val reactions: List<Reaction> = emptyList(),
    val starred: Boolean = false,
    /** Texte cité quand le message est une réponse. */
    val quote: String? = null,
    val subId: Int = -1,
    val attachments: List<Attachment> = emptyList(),
    val isMms: Boolean = false,
    /** Effet déclenché par un mot-clé (« Joyeux anniversaire »…), comme sur iMessage. */
    val effectFromKeyword: Boolean = false,
) {
    val myReaction: String? get() = reactions.lastOrNull { it.mine }?.emoji
}

/** Pièce jointe d'un MMS (photo, vidéo, audio, carte de visite). */
data class Attachment(val uri: String, val contentType: String) {
    val isImage get() = contentType.startsWith("image/")
    val isVideo get() = contentType.startsWith("video/")
    val isAudio get() = contentType.startsWith("audio/")
}

data class Reaction(val emoji: String, val mine: Boolean)

data class ScheduledMessage(
    val id: String,
    val address: String,
    val threadId: Long,
    val body: String,
    val effectName: String?,
    val sendAt: Long,
)
