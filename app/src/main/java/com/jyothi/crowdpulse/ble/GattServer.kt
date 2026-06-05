package com.jyothi.crowdpulse.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

class GattServer(
    private val context: Context,
    private val onMessageReceived: (
        senderId:   Int,
        senderName: String,
        text:       String,
        messageId:  String
    ) -> Unit
) {
    private val tag = "GattServer"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(
            device: BluetoothDevice, status: Int, newState: Int
        ) {
            val s = if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
            Log.d(tag, "Client $s: ${device.address} status=$status")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device:         BluetoothDevice,
            requestId:      Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite:  Boolean,
            responseNeeded: Boolean,
            offset:         Int,
            value:          ByteArray
        ) {
            Log.d(tag, "Write request from ${device.address} — ${value.size} bytes")

            if (characteristic.uuid != BleGattConfig.MESSAGE_CHAR_UUID) {
                if (responseNeeded) gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                return
            }

            try {
                val raw        = String(value, Charsets.UTF_8)
                Log.d(tag, "Payload: $raw")
                val json       = JSONObject(raw)
                val senderId   = json.getInt("senderId")
                val senderName = json.getString("senderName")
                val text       = json.getString("text")
                val messageId  = json.getString("messageId")
                Log.d(tag, "✅ Message from $senderName ($senderId): $text")
                mainHandler.post { onMessageReceived(senderId, senderName, text, messageId) }
            } catch (e: Exception) {
                Log.e(tag, "Parse error: ${e.message}")
            }

            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d(tag, "MTU changed: ${device.address} → $mtu")
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.d(tag, "Service added: status=$status uuid=${service.uuid}")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tag, "Bluetooth not enabled — GATT server skipped")
            return
        }
        gattServer = bluetoothManager.openGattServer(context, serverCallback) ?: run {
            Log.e(tag, "Failed to open GATT server")
            return
        }
        val service = BluetoothGattService(
            BleGattConfig.CHAT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val messageChar = BluetoothGattCharacteristic(
            BleGattConfig.MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageChar)
        gattServer?.addService(service)
        Log.d(tag, "GATT server started — UUID: ${BleGattConfig.CHAT_SERVICE_UUID}")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            gattServer?.close()
            gattServer = null
            Log.d(tag, "GATT server stopped")
        } catch (e: Exception) {
            Log.e(tag, "Stop error: ${e.message}")
        }
    }
}