package com.jyothi.crowdpulse.ble

import android.os.ParcelUuid
import java.util.UUID

object BleConfig {

    // Short 16-bit UUID — only 2 bytes vs 16 bytes for full 128-bit UUID
    // This fixes Samsung DATA_TOO_LARGE advertising error
    val SERVICE_UUID: UUID = UUID.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)

    const val ADVERTISE_INTERVAL_MS = 500L
    const val DEFAULT_TTL = 7
    const val SEEN_CACHE_SIZE = 500
}