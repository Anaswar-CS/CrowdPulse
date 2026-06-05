package com.jyothi.crowdpulse.mesh

import com.jyothi.crowdpulse.ble.PeerPacket

data class PeerEntry(
    val packet: PeerPacket,
    val rssi: Int,
    val lastSeenMs: Long = System.currentTimeMillis()
)

class PeerTable {

    private val peers = mutableMapOf<Int, PeerEntry>()
    private val STALE_THRESHOLD_MS = 120_000L  // FIX: 2 minutes — was 10s, peers were expiring too fast

    fun update(packet: PeerPacket, rssi: Int) {
        peers[packet.deviceId] = PeerEntry(packet, rssi)
        purgeStale()
    }

    fun getAll(): List<PeerEntry> {
        purgeStale()
        return peers.values.toList()
    }

    fun count(): Int {
        purgeStale()
        return peers.size
    }

    fun nearbyCount(): Int = peers.values
        .filter { it.rssi > -70 && !isStale(it) }
        .size

    private fun purgeStale() {
        val now = System.currentTimeMillis()
        peers.entries.removeIf { now - it.value.lastSeenMs > STALE_THRESHOLD_MS }
    }

    private fun isStale(entry: PeerEntry) =
        System.currentTimeMillis() - entry.lastSeenMs > STALE_THRESHOLD_MS
}