package com.jyothi.crowdpulse.safety

import android.util.Log
import com.jyothi.crowdpulse.mesh.MeshRouter
import com.jyothi.crowdpulse.mesh.PacketType
import com.jyothi.crowdpulse.mesh.PeerTable
import kotlinx.coroutines.*

class SafetyMonitor(
    private val peerTable: PeerTable,
    private val router: MeshRouter,
    private val onStatusUpdate: (DensityReport) -> Unit
) {
    private val tag = "SafetyMonitor"
    private val engine = DensityEngine(peerTable)
    private var job: Job? = null

    private var lastDangerBroadcastMs = 0L
    private val DANGER_BROADCAST_COOLDOWN_MS = 30_000L  // 30 seconds between auto-alerts

    fun start() {
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(3000L)
                analyse()
            }
        }
        Log.d(tag, "SafetyMonitor started")
    }

    private fun analyse() {
        val report = engine.analyse()

        // FIX: post to main thread — onStatusUpdate writes to companion object vars
        // that are read by MainActivity polling on the main thread.
        // Without this, writes happen on Dispatchers.Default and reads on main thread
        // with no synchronisation.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onStatusUpdate(report)
        }

        if (report.hasDanger) {
            val now = System.currentTimeMillis()
            if (now - lastDangerBroadcastMs > DANGER_BROADCAST_COOLDOWN_MS) {
                lastDangerBroadcastMs = now
                val worstCell = report.dangerCells.firstOrNull()
                if (worstCell != null) {
                    router.createAndSend(
                        type        = PacketType.DANGER,
                        lat         = worstCell.centreLat,
                        lon         = worstCell.centreLon,
                        payloadText = "Danger: ${worstCell.peopleCount} people in 20m area"
                    )
                    Log.d(tag, "🚨 Auto-broadcast DANGER: ${worstCell.peopleCount} people")
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        Log.d(tag, "SafetyMonitor stopped")
    }
}