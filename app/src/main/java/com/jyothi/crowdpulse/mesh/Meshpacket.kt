package com.jyothi.crowdpulse.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class PacketType(val id: Byte) {
    GPS(0x01),
    SOS(0x02),
    DANGER(0x03),
    MEDICAL(0x04),
    CHAT(0x05),        // friend-to-friend message
    CHAT_ACK(0x06);    // delivery confirmation
    companion object { fun from(b: Byte) = values().firstOrNull { it.id == b } ?: GPS }
}

data class MeshPacket(
    val messageId: UUID,
    val originId: Int,
    val type: PacketType,
    val ttl: Int,
    val hopCount: Int,
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val payload: ByteArray = ByteArray(0)
) {
    fun encode(): ByteArray {
        val buf = ByteBuffer.allocate(40 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x43)
        buf.put(0x50)
        buf.put(0x02)
        buf.put(type.id)
        buf.putLong(messageId.mostSignificantBits)
        buf.putLong(messageId.leastSignificantBits)
        buf.putInt(originId)
        buf.put(ttl.toByte())
        buf.put(hopCount.toByte())
        buf.putInt((lat * 1_000_000).toInt())
        buf.putInt((lon * 1_000_000).toInt())
        buf.putInt((timestamp / 1000).toInt())
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        return buf.array()
    }

    companion object {
        fun decode(bytes: ByteArray): MeshPacket? {
            if (bytes.size < 40) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            if (buf.get() != 0x43.toByte() || buf.get() != 0x50.toByte()) return null
            buf.get() // version
            val type     = PacketType.from(buf.get())
            val msgHigh  = buf.getLong()
            val msgLow   = buf.getLong()
            val originId = buf.getInt()
            val ttl      = buf.get().toInt() and 0xFF
            val hopCount = buf.get().toInt() and 0xFF
            val lat      = buf.getInt() / 1_000_000.0
            val lon      = buf.getInt() / 1_000_000.0
            val ts       = buf.getInt().toLong() * 1000
            val payLen   = buf.getShort().toInt() and 0xFFFF
            val payload  = if (payLen > 0 && buf.remaining() >= payLen) {
                ByteArray(payLen).also { buf.get(it) }
            } else ByteArray(0)
            return MeshPacket(
                messageId = UUID(msgHigh, msgLow),
                originId  = originId,
                type      = type,
                ttl       = ttl,
                hopCount  = hopCount,
                lat       = lat,
                lon       = lon,
                timestamp = ts,
                payload   = payload
            )
        }
    }
}