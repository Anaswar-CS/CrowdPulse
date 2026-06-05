package com.jyothi.crowdpulse.ble

import android.content.Context
import android.util.Log
import org.json.JSONObject

class GattManager(
    private val context:           Context,
    private val onMessageReceived: (senderId: Int, senderName: String,
                                    text: String, messageId: String) -> Unit,
    private val onMessageSent:     (messageId: String, success: Boolean) -> Unit
) {
    private val tag = "GattManager"

    // realDeviceId (ANDROID_ID-based) → MAC address
    private val deviceIdToMac = mutableMapOf<Int, String>()

    private val server = GattServer(context, onMessageReceived)
    private val client = GattClient(context) { messageId, success ->
        Log.d(tag, "Delivery result — id=$messageId success=$success")
        onMessageSent(messageId, success)
    }

    fun start() {
        server.start()
        Log.d(tag, "GattManager started")
    }

    fun registerPeer(macAddress: String, realDeviceId: Int) {
        deviceIdToMac[realDeviceId] = macAddress
        Log.d(tag, "Peer registered: deviceId=$realDeviceId → $macAddress")
        Log.d(tag, "All known peers: $deviceIdToMac")
    }

    fun canReach(deviceId: Int): Boolean = deviceIdToMac.containsKey(deviceId)

    fun sendMessage(
        toDeviceId: Int,
        myDeviceId: Int,
        senderName: String,
        text:       String,
        messageId:  String
    ): Boolean {
        Log.d(tag, "sendMessage: toDeviceId=$toDeviceId knownIds=${deviceIdToMac.keys}")

        val payload = JSONObject().apply {
            put("senderId",   myDeviceId)
            put("senderName", senderName)
            put("text",       text)
            put("messageId",  messageId)
        }.toString().toByteArray(Charsets.UTF_8).take(512).toByteArray()

        val mac = deviceIdToMac[toDeviceId]
        if (mac != null) {
            // Specific peer found — send directly
            Log.d(tag, "✅ Sending via GATT to $mac: $text")
            client.send(mac, payload, messageId)
            return true
        }

        // MAC not found — broadcast to ALL known peers as fallback
        if (deviceIdToMac.isEmpty()) {
            Log.w(tag, "❌ No known peers at all — GATT send failed")
            return false
        }

        Log.w(tag, "⚠️ No MAC for deviceId=$toDeviceId — broadcasting to all ${deviceIdToMac.size} peers")
        deviceIdToMac.values.forEach { peerMac ->
            client.send(peerMac, payload, messageId)
        }
        return true
    }

    // Broadcast to every known peer — used for SOS so all nearby devices are reached
    // regardless of whether we have their specific deviceId mapped yet.
    fun broadcastToAll(
        myDeviceId: Int,
        senderName: String,
        text:       String,
        messageId:  String
    ) {
        if (deviceIdToMac.isEmpty()) {
            Log.w(tag, "broadcastToAll: no peers registered — SOS not sent via GATT")
            return
        }

        val payload = JSONObject().apply {
            put("senderId",   myDeviceId)
            put("senderName", senderName)
            put("text",       text)
            put("messageId",  messageId)
        }.toString().toByteArray(Charsets.UTF_8).take(512).toByteArray()

        Log.d(tag, "📡 SOS broadcast to ${deviceIdToMac.size} peers")
        deviceIdToMac.values.forEach { mac ->
            client.send(mac, payload, messageId)
        }
    }

    fun stop() {
        server.stop()
        client.stop()
        Log.d(tag, "GattManager stopped")
    }
}