package com.jyothi.crowdpulse.mesh

import android.util.Log
import java.util.UUID

class MeshRouter(
    private val myDeviceId: Int,
    private val onForward: (MeshPacket) -> Unit,
    private val onDeliver: (MeshPacket) -> Unit
) {
    private val tag = "MeshRouter"
    private val cache = MessageCache()

    fun receive(packet: MeshPacket, rssi: Int = -60) {
        if (cache.seenBefore(packet.messageId)) {
            Log.d(tag, "Dropping duplicate ${packet.messageId}")
            return
        }
        cache.markSeen(packet.messageId)
        onDeliver(packet)
        Log.d(tag, "Delivered ${packet.type} from ${packet.originId} " +
                "(${packet.hopCount} hops, TTL: ${packet.ttl})")

        if (packet.ttl > 0 && packet.originId != myDeviceId) {
            val forwarded = packet.copy(
                ttl      = packet.ttl - 1,
                hopCount = packet.hopCount + 1
            )
            onForward(forwarded)
            Log.d(tag, "Forwarding ${packet.type} TTL=${forwarded.ttl}")
        }
    }

    fun createAndSend(
        type: PacketType,
        lat: Double,
        lon: Double,
        payloadText: String = ""
    ): MeshPacket {
        val packet = MeshPacket(
            messageId = UUID.randomUUID(),
            originId  = myDeviceId,
            type      = type,
            ttl       = if (type == PacketType.SOS) 15 else 7,
            hopCount  = 0,
            lat       = lat,
            lon       = lon,
            timestamp = System.currentTimeMillis(),
            payload   = payloadText.toByteArray(Charsets.UTF_8)
        )
        cache.markSeen(packet.messageId)
        onForward(packet)
        return packet
    }
}