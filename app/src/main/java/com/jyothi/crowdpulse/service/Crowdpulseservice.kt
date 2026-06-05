package com.jyothi.crowdpulse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jyothi.crowdpulse.MainActivity
import com.jyothi.crowdpulse.ble.BleAdvertiser
import com.jyothi.crowdpulse.ble.BleConfig
import com.jyothi.crowdpulse.ble.BleScanner
import com.jyothi.crowdpulse.ble.GattManager
import com.jyothi.crowdpulse.ble.PacketCodec
import com.jyothi.crowdpulse.ble.PeerPacket
import com.jyothi.crowdpulse.location.GpsManager
import com.jyothi.crowdpulse.mesh.MeshPacket
import com.jyothi.crowdpulse.mesh.MeshRouter
import com.jyothi.crowdpulse.mesh.PacketType
import com.jyothi.crowdpulse.mesh.PeerTable
import com.jyothi.crowdpulse.mesh.WifiDirectTransport
import com.jyothi.crowdpulse.safety.SafetyMonitor
import com.jyothi.crowdpulse.safety.ZoneStatus
import org.json.JSONObject

class CrowdPulseService : Service() {

    private val tag = "CrowdPulseService"

    private lateinit var scanner:       BleScanner
    private lateinit var advertiser:    BleAdvertiser
    private lateinit var gpsManager:    GpsManager
    private lateinit var router:        MeshRouter
    private lateinit var transport:     WifiDirectTransport
    private lateinit var safetyMonitor: SafetyMonitor
    private lateinit var gattManager:   GattManager

    private val peerTable   = PeerTable()
    private val seenDevices = mutableSetOf<String>()

    // MAC address → real ANDROID_ID-based deviceId (populated once GPS payload decoded)
    private val macToRealId = mutableMapOf<String, Int>()

    private var lastLocation: Location? = null
    private var lastAdvertiseUpdate = 0L

    private val myDeviceId: Int by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            .hashCode() and 0xFFFF
    }

    private val myName: String by lazy {
        "User_${myDeviceId.toString(16).takeLast(4).uppercase()}"
    }

    companion object {
        const val CHANNEL_ID       = "crowdpulse_status"
        const val CHANNEL_ALERT_ID = "crowdpulse_alerts"

        const val NOTIFICATION_ID       = 1001
        const val NOTIFICATION_ALERT_ID = 1002

        const val ACTION_STOP = "com.jyothi.crowdpulse.STOP"
        const val ACTION_SOS  = "com.jyothi.crowdpulse.SOS"
        const val ACTION_CHAT = "com.jyothi.crowdpulse.SEND_CHAT"

        var currentZoneStatus = "🟢 Zone: Safe"
        var currentPeerCount  = 0
        var currentLat        = 0.0
        var currentLon        = 0.0
        var latestAlert       = ""
        var isRunning         = false
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannels()
            startForeground(NOTIFICATION_ID,
                buildNotification("Scanning for peers...", isAlert = false))
            startAll()
            isRunning = true
            Log.d(tag, "Service started — myDeviceId=$myDeviceId")
        } catch (e: Exception) {
            Log.e(tag, "onCreate crash: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startAll() {
        transport = WifiDirectTransport()

        gattManager = GattManager(
            context           = this,
            onMessageReceived = { senderId, senderName, text, messageId ->
                Log.d(tag, "GATT message: $senderName → $text")
                sendBroadcast(Intent("com.jyothi.crowdpulse.CHAT").apply {
                    setPackage(packageName)
                    putExtra("senderId",   senderId)
                    putExtra("senderName", senderName)
                    putExtra("text",       text)
                    putExtra("messageId",  messageId)
                })
                updateNotification("💬 $senderName: $text", isAlert = true)
            },
            onMessageSent = { messageId, success ->
                Log.d(tag, "Message sent: id=$messageId success=$success")
            }
        )
        gattManager.start()

        router = MeshRouter(
            myDeviceId = myDeviceId,
            onForward  = { packet ->
                if (::advertiser.isInitialized)
                    advertiser.update(packet.encode().take(20).toByteArray())
            },
            onDeliver  = { packet ->
                when (packet.type) {
                    PacketType.SOS -> {
                        latestAlert = "🆘 SOS — ${packet.hopCount} hops away!\n" +
                                "Location: %.4f, %.4f".format(packet.lat, packet.lon)
                        updateNotification(latestAlert, isAlert = true)
                        sendAlertBroadcast(latestAlert)
                        vibrateAlert()
                    }
                    PacketType.DANGER -> {
                        latestAlert = "🚨 Dangerous crowding nearby — move away!"
                        currentZoneStatus = "🔴 DANGER — Leave immediately!"
                        updateNotification(latestAlert, isAlert = true)
                        sendAlertBroadcast(latestAlert)
                        vibrateAlert()
                    }
                    PacketType.MEDICAL -> {
                        latestAlert = "🏥 Medical help requested nearby"
                        updateNotification(latestAlert, isAlert = true)
                        sendAlertBroadcast(latestAlert)
                    }
                    PacketType.CHAT -> {
                        try {
                            val json       = JSONObject(String(packet.payload, Charsets.UTF_8))
                            val senderId   = json.getInt("senderId")
                            val senderName = json.getString("senderName")
                            val text       = json.getString("text")
                            val messageId  = json.getString("messageId")
                            sendBroadcast(Intent("com.jyothi.crowdpulse.CHAT").apply {
                                setPackage(packageName)
                                putExtra("senderId",   senderId)
                                putExtra("senderName", senderName)
                                putExtra("text",       text)
                                putExtra("messageId",  messageId)
                            })
                            updateNotification("💬 $senderName: $text", isAlert = true)
                        } catch (e: Exception) {
                            Log.e(tag, "Mesh chat decode error: ${e.message}")
                        }
                    }
                    else -> {}
                }

                sendBroadcast(Intent("com.jyothi.crowdpulse.PEER").apply {
                    setPackage(packageName)
                    putExtra("deviceId", packet.originId)
                    putExtra("name",
                        "User_${packet.originId.toString(16).takeLast(4).uppercase()}")
                    putExtra("lat", packet.lat)
                    putExtra("lon", packet.lon)
                })
            }
        )

        advertiser = BleAdvertiser(this)
        advertiser.start(ByteArray(8) { 0x00 })

        // ── Scanner ───────────────────────────────────────────────────────────
        scanner = BleScanner(this) { macAddress, rssi, rawBytes ->

            val isNew = seenDevices.add(macAddress)
            if (isNew) {
                currentPeerCount = seenDevices.size
                Log.d(tag, "New BLE peer: $macAddress total=$currentPeerCount")
            }

            // Step 1 — Broadcast PEER immediately using MAC-based ID
            // This ensures peers show up right away even before GPS fix
            val macBasedId = macAddress.hashCode() and 0xFFFF
            sendBroadcast(Intent("com.jyothi.crowdpulse.PEER").apply {
                setPackage(packageName)
                putExtra("deviceId", macBasedId)
                putExtra("name",     "User_${macAddress.takeLast(4)}")
                putExtra("lat",      0.0)
                putExtra("lon",      0.0)
            })

            // Register with GATT using MAC-based ID as initial fallback
            gattManager.registerPeer(macAddress, macBasedId)

            // Step 2 — Decode payload for real deviceId and GPS coords
            rawBytes?.let { bytes ->
                if (bytes.size >= 8) {
                    PacketCodec.decode(bytes)?.let { peerPacket ->
                        val realDeviceId = peerPacket.deviceId

                        // Only update if we got a valid real ID (non-zero = GPS ready)
                        if (realDeviceId != 0 && realDeviceId != macBasedId) {
                            Log.d(tag, "Real deviceId decoded: $macAddress → $realDeviceId")
                            macToRealId[macAddress] = realDeviceId

                            // Register real ID with GATT — now both IDs work
                            gattManager.registerPeer(macAddress, realDeviceId)

                            peerTable.update(peerPacket, rssi)

                            // Broadcast again with real deviceId and GPS coords
                            // This updates the friend entry with the correct ID
                            sendBroadcast(Intent("com.jyothi.crowdpulse.PEER").apply {
                                setPackage(packageName)
                                putExtra("deviceId", realDeviceId)
                                putExtra("name",
                                    "User_${realDeviceId.toString(16).takeLast(4).uppercase()}")
                                putExtra("lat",      peerPacket.lat)
                                putExtra("lon",      peerPacket.lon)
                            })
                        } else if (realDeviceId != 0) {
                            peerTable.update(peerPacket, rssi)
                            sendBroadcast(Intent("com.jyothi.crowdpulse.PEER").apply {
                                setPackage(packageName)
                                putExtra("deviceId", realDeviceId)
                                putExtra("name",
                                    "User_${realDeviceId.toString(16).takeLast(4).uppercase()}")
                                putExtra("lat",      peerPacket.lat)
                                putExtra("lon",      peerPacket.lon)
                            })
                        }
                    }
                }

                MeshPacket.decode(bytes)?.let { packet ->
                    router.receive(packet, rssi)
                }
            }
        }

        transport.startServer { bytes ->
            MeshPacket.decode(bytes)?.let { router.receive(it) }
        }

        gpsManager = GpsManager(this) { location ->
            lastLocation = location
            currentLat   = location.latitude
            currentLon   = location.longitude

            val now = System.currentTimeMillis()
            if (now - lastAdvertiseUpdate > 10_000L) {
                lastAdvertiseUpdate = now
                val payload = PacketCodec.encode(location, myDeviceId, BleConfig.DEFAULT_TTL)
                if (::advertiser.isInitialized) advertiser.update(payload)
            }
        }

        safetyMonitor = SafetyMonitor(
            peerTable      = peerTable,
            router         = router,
            onStatusUpdate = { report ->
                // FIX: only update peer count from SafetyMonitor if it has a real reading.
                // Without this guard, SafetyMonitor resets currentPeerCount to 0 every 3s
                // whenever PeerTable is empty (no GPS fix yet), wiping the BLE-based count.
                if (report.totalPeers > 0) currentPeerCount = report.totalPeers
                currentZoneStatus = when (report.worstStatus) {
                    ZoneStatus.SAFE    -> "🟢 Zone: Safe"
                    ZoneStatus.DENSE   -> "🟡 Zone: Getting crowded"
                    ZoneStatus.WARNING -> "🟠 Zone: Move away now"
                    ZoneStatus.DANGER  -> "🔴 DANGER — Leave immediately!"
                }
                updateNotification(currentZoneStatus, isAlert = false)
                if (report.hasDanger) {
                    latestAlert = "🚨 Dangerous crowding detected! Move away now."
                    updateNotification(latestAlert, isAlert = true)
                    sendAlertBroadcast(latestAlert)
                    vibrateAlert()
                }
            }
        )

        scanner.start()
        gpsManager.start()
        safetyMonitor.start()
    }

    // ── SOS ───────────────────────────────────────────────────────────────────

    private fun sendSos() {
        val loc = lastLocation

        // Primary: send SOS to all known peers via GATT (reliable, no size limit)
        // Mesh routing alone won't work — SOS packets are 40+ bytes, BLE ads carry only 8
        if (::gattManager.isInitialized) {
            val sosMessage = "🆘 SOS from $myName — Help needed! Location: ${
                if (loc != null) "%.4f, %.4f".format(loc.latitude, loc.longitude)
                else "unknown"
            }"
            gattManager.broadcastToAll(
                myDeviceId = myDeviceId,
                senderName = myName,
                text       = sosMessage,
                messageId  = "sos_${System.currentTimeMillis()}"
            )
        }

        // Fallback: mesh routing for multi-hop propagation beyond direct BLE range
        router.createAndSend(
            type        = PacketType.SOS,
            lat         = loc?.latitude  ?: 0.0,
            lon         = loc?.longitude ?: 0.0,
            payloadText = "Help needed!"
        )
        latestAlert = "🆘 SOS sent — spreading through crowd..."
        sendAlertBroadcast(latestAlert)
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    private fun sendChat(
        toDeviceId: Int,
        text:       String,
        senderName: String,
        messageId:  String
    ) {
        Log.d(tag, "sendChat: toDeviceId=$toDeviceId text='$text'")
        Log.d(tag, "  canReach=${
            if (::gattManager.isInitialized) gattManager.canReach(toDeviceId) else false
        }")

        // Echo to local UI — sender sees message immediately
        sendBroadcast(Intent("com.jyothi.crowdpulse.CHAT").apply {
            setPackage(packageName)
            putExtra("senderId",   myDeviceId)
            putExtra("senderName", senderName)
            putExtra("text",       text)
            putExtra("messageId",  messageId)
        })

        val sentViaGatt = if (::gattManager.isInitialized) {
            gattManager.sendMessage(toDeviceId, myDeviceId, senderName, text, messageId)
        } else false

        if (!sentViaGatt) {
            Log.w(tag, "GATT unavailable — mesh fallback for $toDeviceId")
            val json = JSONObject().apply {
                put("senderId",   myDeviceId)
                put("senderName", senderName)
                put("text",       text)
                put("messageId",  messageId)
                put("toDeviceId", toDeviceId)
            }.toString()
            router.createAndSend(
                type        = PacketType.CHAT,
                lat         = lastLocation?.latitude  ?: 0.0,
                lon         = lastLocation?.longitude ?: 0.0,
                payloadText = json
            )
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CrowdPulse Status",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background scanning — no sound"
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT_ID, "CrowdPulse Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "SOS, danger zone, and message alerts"
                enableVibration(true)
            }
        )
    }

    private fun buildNotification(status: String, isAlert: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CrowdPulseService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(
            this, if (isAlert) CHANNEL_ALERT_ID else CHANNEL_ID
        )
            .setContentTitle(if (isAlert) "🚨 CrowdPulse Alert" else "CrowdPulse 📡 Active")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(!isAlert)
            .setPriority(if (isAlert) NotificationCompat.PRIORITY_MAX
            else         NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(isAlert)
            .build()
    }

    private fun updateNotification(status: String, isAlert: Boolean) {
        getSystemService(NotificationManager::class.java).notify(
            if (isAlert) NOTIFICATION_ALERT_ID else NOTIFICATION_ID,
            buildNotification(status, isAlert)
        )
    }

    private fun sendAlertBroadcast(message: String) {
        sendBroadcast(Intent("com.jyothi.crowdpulse.ALERT").apply {
            setPackage(packageName)
            putExtra("message", message)
        })
    }

    private fun vibrateAlert() {
        try {
            getSystemService(android.os.Vibrator::class.java).vibrate(
                android.os.VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 1000), -1))
        } catch (e: Exception) { Log.e(tag, "Vibration error: ${e.message}") }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_SOS  -> sendSos()
            ACTION_CHAT -> {
                val toDeviceId = intent.getIntExtra("toDeviceId", 0)
                val text       = intent.getStringExtra("text")       ?: return START_STICKY
                val senderName = intent.getStringExtra("senderName") ?: myName
                val messageId  = intent.getStringExtra("messageId")  ?: return START_STICKY
                sendChat(toDeviceId, text, senderName, messageId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gpsManager.isInitialized)    gpsManager.stop()
        if (::advertiser.isInitialized)    advertiser.stop()
        if (::scanner.isInitialized)       scanner.stop()
        if (::transport.isInitialized)     transport.stop()
        if (::safetyMonitor.isInitialized) safetyMonitor.stop()
        if (::gattManager.isInitialized)   gattManager.stop()
        seenDevices.clear()
        macToRealId.clear()
        isRunning = false
        Log.d(tag, "CrowdPulseService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}