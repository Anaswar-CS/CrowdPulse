package com.jyothi.crowdpulse.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

class GattClient(
    private val context: Context,
    private val onSent: (messageId: String, success: Boolean) -> Unit
) {
    private val tag = "GattClient"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private data class Pending(val payload: ByteArray, val messageId: String)

    private val pending     = mutableMapOf<String, Pending>()
    private val connections = mutableMapOf<String, BluetoothGatt>()

    @SuppressLint("MissingPermission")
    fun send(macAddress: String, payload: ByteArray, messageId: String) {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tag, "Bluetooth not enabled")
            mainHandler.post { onSent(messageId, false) }
            return
        }
        Log.d(tag, "Connecting to $macAddress — id=$messageId size=${payload.size}B")
        pending[macAddress] = Pending(payload, messageId)

        connections.remove(macAddress)?.let {
            try { it.disconnect(); it.close() } catch (_: Exception) {}
        }
        try {
            val device = adapter.getRemoteDevice(macAddress)
            val gatt   = device.connectGatt(
                context, false, makeCallback(macAddress), BluetoothDevice.TRANSPORT_LE)
            connections[macAddress] = gatt
        } catch (e: Exception) {
            Log.e(tag, "Connect error: ${e.message}")
            pending.remove(macAddress)
            mainHandler.post { onSent(messageId, false) }
        }
    }

    private fun makeCallback(mac: String) = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, status: Int, newState: Int
        ) {
            Log.d(tag, "State: $mac newState=$newState status=$status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(tag, "Connected to $mac — requesting MTU 512")
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(tag, "Disconnected from $mac")
                    connections.remove(mac)
                    gatt.close()
                    pending.remove(mac)?.let {
                        Log.w(tag, "Message lost on disconnect: ${it.messageId}")
                        mainHandler.post { onSent(it.messageId, false) }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(tag, "MTU=$mtu for $mac — discovering services")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(tag, "Services discovered for $mac status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(gatt, mac); return }

            gatt.services.forEach { Log.d(tag, "  Service: ${it.uuid}") }

            val characteristic = gatt
                .getService(BleGattConfig.CHAT_SERVICE_UUID)
                ?.getCharacteristic(BleGattConfig.MESSAGE_CHAR_UUID)

            if (characteristic == null) {
                Log.w(tag, "CrowdPulse service/char not found on $mac")
                fail(gatt, mac)
                return
            }

            val msg = pending[mac] ?: run {
                Log.w(tag, "No pending message for $mac")
                gatt.disconnect()
                return
            }

            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = msg.payload
            val queued = gatt.writeCharacteristic(characteristic)
            Log.d(tag, "Write queued=$queued for $mac")
            if (!queued) fail(gatt, mac)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt:           BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status:         Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            Log.d(tag, "Write complete: $mac success=$success")
            pending.remove(mac)?.let { mainHandler.post { onSent(it.messageId, success) } }
            gatt.disconnect()
        }

        @SuppressLint("MissingPermission")
        private fun fail(gatt: BluetoothGatt, mac: String) {
            pending.remove(mac)?.let { mainHandler.post { onSent(it.messageId, false) } }
            gatt.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        connections.values.forEach {
            try { it.disconnect(); it.close() } catch (_: Exception) {}
        }
        connections.clear()
        pending.clear()
        Log.d(tag, "GATT client stopped")
    }
}