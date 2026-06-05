package com.jyothi.crowdpulse.friends

import java.util.UUID

enum class MessageStatus { SENDING, DELIVERED, READ }

data class FriendMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val senderId: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING
)

data class Friend(
    val deviceId: Int,
    val name: String,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    val isNearby: Boolean = false
)