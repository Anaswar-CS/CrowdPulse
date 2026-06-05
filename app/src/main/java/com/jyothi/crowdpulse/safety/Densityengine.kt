package com.jyothi.crowdpulse.safety

import com.jyothi.crowdpulse.mesh.PeerTable

data class GridCell(
    val cellId: String,
    val centreLat: Double,
    val centreLon: Double,
    val peopleCount: Int,
    val densityPerSqM: Double,
    val status: ZoneStatus
)

enum class ZoneStatus(val label: String, val emoji: String) {
    SAFE("Safe", "🟢"),
    DENSE("Dense", "🟡"),
    WARNING("Warning — move away", "🟠"),
    DANGER("DANGER — leave now!", "🔴")
}

data class DensityReport(
    val cells: List<GridCell>,
    val worstStatus: ZoneStatus,
    val totalPeers: Int,
    val dangerCells: List<GridCell> = emptyList()
) {
    val hasDanger get() = worstStatus == ZoneStatus.DANGER
    val hasWarning get() = worstStatus == ZoneStatus.WARNING
}

class DensityEngine(private val peerTable: PeerTable) {

    private val CELL_SIZE_M   = 20.0
    private val CELL_AREA_SQM = CELL_SIZE_M * CELL_SIZE_M  // 400 m²

    private val DENSE_THRESHOLD   = 1.0
    private val WARNING_THRESHOLD = 4.0
    private val DANGER_THRESHOLD  = 7.0

    // 0.0002° ≈ 20 metres — grid cell size
    private fun latLonToCellId(lat: Double, lon: Double): String {
        val cellLat = (lat / 0.0002).toInt()
        val cellLon = (lon / 0.0002).toInt()
        return "$cellLat:$cellLon"
    }

    private fun cellCentre(cellId: String): Pair<Double, Double> {
        val parts = cellId.split(":")
        val lat = parts[0].toInt() * 0.0002 + 0.0001
        val lon = parts[1].toInt() * 0.0002 + 0.0001
        return Pair(lat, lon)
    }

    private fun statusFromDensity(density: Double) = when {
        density >= DANGER_THRESHOLD  -> ZoneStatus.DANGER
        density >= WARNING_THRESHOLD -> ZoneStatus.WARNING
        density >= DENSE_THRESHOLD   -> ZoneStatus.DENSE
        else                         -> ZoneStatus.SAFE
    }

    fun analyse(): DensityReport {
        val peers = peerTable.getAll()
        if (peers.isEmpty()) return DensityReport(emptyList(), ZoneStatus.SAFE, 0)

        // Group peers into grid cells
        val cellMap = mutableMapOf<String, MutableList<Double>>()
        peers.forEach { peer ->
            val cellId = latLonToCellId(peer.packet.lat, peer.packet.lon)
            cellMap.getOrPut(cellId) { mutableListOf() }.add(peer.packet.lat)
        }

        // Build GridCell list
        val cells = cellMap.map { (cellId, lats) ->
            val count   = lats.size
            val density = count.toDouble() / CELL_AREA_SQM
            val (cLat, cLon) = cellCentre(cellId)
            GridCell(
                cellId        = cellId,
                centreLat     = cLat,
                centreLon     = cLon,
                peopleCount   = count,
                densityPerSqM = density,
                status        = statusFromDensity(density)
            )
        }

        val worstStatus = cells.maxByOrNull { it.densityPerSqM }?.status ?: ZoneStatus.SAFE
        val dangerCells = cells.filter { it.status == ZoneStatus.DANGER }

        return DensityReport(
            cells       = cells,
            worstStatus = worstStatus,
            totalPeers  = peers.size,
            dangerCells = dangerCells
        )
    }
}