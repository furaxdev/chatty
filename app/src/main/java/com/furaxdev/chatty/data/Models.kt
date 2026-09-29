package com.furaxdev.chatty.data

import com.furaxdev.chatty.effects.MessageEffect

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
    val reaction: String? = null,
    val starred: Boolean = false,
)

data class ScheduledMessage(
    val id: String,
    val address: String,
    val threadId: Long,
    val body: String,
    val effectName: String?,
    val sendAt: Long,
)
