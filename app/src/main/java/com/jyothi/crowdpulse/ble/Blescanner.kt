package com.jyothi.crowdpulse.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log

class BleScanner(
    private val context: Context,
    private val onDeviceFound: (deviceId: String, rssi: Int, rawBytes: ByteArray?) -> Unit
) {
    private val tag = "BleScanner"
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceId = result.device.address
            val rssi     = result.rssi

            // Check BOTH UUID list (from advertisement) AND service data (from scan response)
            // BLE sends these in separate packets so either one alone is valid
            val uuids   = result.scanRecord?.serviceUuids
            val payload = result.scanRecord?.serviceData
                ?.get(BleConfig.SERVICE_PARCEL_UUID)

            val isCrowdPulse = uuids?.contains(BleConfig.SERVICE_PARCEL_UUID) == true
                    || payload != null

            if (!isCrowdPulse) {
                // Log non-CrowdPulse devices for debugging but don't process
                Log.d(tag, "Non-CrowdPulse: $deviceId  UUIDs: $uuids")
                return
            }

            Log.d(tag, "✅ CrowdPulse peer: $deviceId  RSSI: $rssi  payload: ${payload?.size ?: 0}B")
            onDeviceFound(deviceId, rssi, payload)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                val deviceId = result.device.address
                val rssi     = result.rssi
                val uuids    = result.scanRecord?.serviceUuids
                val payload  = result.scanRecord?.serviceData
                    ?.get(BleConfig.SERVICE_PARCEL_UUID)

                val isCrowdPulse = uuids?.contains(BleConfig.SERVICE_PARCEL_UUID) == true
                        || payload != null

                if (isCrowdPulse) {
                    Log.d(tag, "✅ Batch CrowdPulse peer: $deviceId payload: ${payload?.size ?: 0}B")
                    onDeviceFound(deviceId, rssi, payload)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val reason = when (errorCode) {
                1 -> "ALREADY_STARTED"
                2 -> "APPLICATION_REGISTRATION_FAILED"
                3 -> "INTERNAL_ERROR"
                4 -> "FEATURE_UNSUPPORTED"
                5 -> "OUT_OF_HARDWARE_RESOURCES"
                6 -> "SCANNING_TOO_FREQUENTLY"
                else -> "UNKNOWN($errorCode)"
            }
            Log.e(tag, "Scan failed: $reason")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tag, "Bluetooth not enabled — skipping scan")
            return
        }
        val bleScanner = adapter.bluetoothLeScanner ?: run {
            Log.w(tag, "BLE scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            // No filter — scan everything and check CrowdPulse manually in callback
            // This catches advertisement packets that arrive before the scan response
            bleScanner.startScan(null, settings, scanCallback)
            Log.d(tag, "BLE scan started (no filter) — UUID: ${BleConfig.SERVICE_UUID}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(tag, "BLE scan stopped")
        } catch (e: Exception) {
            Log.e(tag, "Stop error: ${e.message}")
        }
    }
}