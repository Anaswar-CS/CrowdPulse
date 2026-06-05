package com.jyothi.crowdpulse.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log

class BleAdvertiser(private val context: Context) {

    private val tag = "BleAdvertiser"
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(tag, "Advertising started successfully")
        }
        override fun onStartFailure(errorCode: Int) {
            val reason = when (errorCode) {
                1 -> "DATA_TOO_LARGE"
                2 -> "TOO_MANY_ADVERTISERS"
                3 -> "ALREADY_STARTED"
                4 -> "INTERNAL_ERROR"
                5 -> "FEATURE_UNSUPPORTED"
                else -> "UNKNOWN($errorCode)"
            }
            Log.e(tag, "Advertising failed: $reason")
        }
    }

    @SuppressLint("MissingPermission")
    fun start(payload: ByteArray) {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tag, "Bluetooth not enabled — skipping advertise")
            return
        }
        val bleAdvertiser = adapter.bluetoothLeAdvertiser ?: run {
            Log.w(tag, "BLE advertiser not available on this device")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(BleConfig.SERVICE_PARCEL_UUID)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(BleConfig.SERVICE_PARCEL_UUID, payload.take(8).toByteArray())
            .build()
        bleAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun update(newPayload: ByteArray) {
        stop()
        start(newPayload)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            bluetoothManager.adapter?.bluetoothLeAdvertiser
                ?.stopAdvertising(advertiseCallback)
            Log.d(tag, "Advertising stopped")
        } catch (e: Exception) {
            Log.e(tag, "Stop error: ${e.message}")
        }
    }
}