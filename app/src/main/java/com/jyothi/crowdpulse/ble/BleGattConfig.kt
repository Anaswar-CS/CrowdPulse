package com.jyothi.crowdpulse.ble

import java.util.UUID

object BleGattConfig {
    val CHAT_SERVICE_UUID = UUID.fromString("0000FD70-0000-1000-8000-00805F9B34FB")
    val MESSAGE_CHAR_UUID = UUID.fromString("0000FD71-0000-1000-8000-00805F9B34FB")
}