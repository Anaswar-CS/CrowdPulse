package com.jyothi.crowdpulse.ble

import android.location.Location
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PeerPacket(
    val deviceId: Int,
    val lat: Double,
    val lon: Double,
    val altitudeMetres: Int,
    val timestampDelta: Int,
    val ttl: Int,
    val isSos: Boolean,
    val isMedical: Boolean
)

object PacketCodec {

    // Encode GPS into 8 bytes — fits in BLE scan response
    // Bytes 0-3: latitude  × 1,000,000 as Int32
    // Bytes 4-7: longitude × 1,000,000 as Int32
    // Accuracy: 0.000001° = ~11cm — more than enough for crowd detection
    fun encode(location: Location, deviceId: Int, ttl: Int, flags: Int = 0): ByteArray {
        val buf    = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        val latInt = (location.latitude  * 1_000_000).toInt()
        val lonInt = (location.longitude * 1_000_000).toInt()
        buf.putInt(latInt)
        buf.putInt(lonInt)
        return buf.array()
    }

    fun decode(bytes: ByteArray): PeerPacket? {
        if (bytes.size < 8) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val lat = buf.getInt() / 1_000_000.0
        val lon = buf.getInt() / 1_000_000.0
        return PeerPacket(
            deviceId       = 0,
            lat            = lat,
            lon            = lon,
            altitudeMetres = 0,
            timestampDelta = 0,
            ttl            = BleConfig.DEFAULT_TTL,
            isSos          = false,
            isMedical      = false
        )
    }
}