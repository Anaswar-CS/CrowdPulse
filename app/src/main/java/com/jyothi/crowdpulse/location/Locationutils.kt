package com.jyothi.crowdpulse.location

import kotlin.math.*

object LocationUtils {

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val dLon  = Math.toRadians(lon2 - lon1)
        val x = sin(dLon) * cos(lat2R)
        val y = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360) % 360
    }

    fun bearingToDirection(degrees: Double): String = when {
        degrees < 22.5  -> "North"
        degrees < 67.5  -> "North-East"
        degrees < 112.5 -> "East"
        degrees < 157.5 -> "South-East"
        degrees < 202.5 -> "South"
        degrees < 247.5 -> "South-West"
        degrees < 292.5 -> "West"
        degrees < 337.5 -> "North-West"
        else            -> "North"
    }

    fun bearingToArrow(degrees: Double): String = when {
        degrees < 22.5  -> "↑"
        degrees < 67.5  -> "↗"
        degrees < 112.5 -> "→"
        degrees < 157.5 -> "↘"
        degrees < 202.5 -> "↓"
        degrees < 247.5 -> "↙"
        degrees < 292.5 -> "←"
        degrees < 337.5 -> "↖"
        else            -> "↑"
    }

    fun formatDistance(metres: Double): String = when {
        metres < 1    -> "< 1m"
        metres < 1000 -> "${metres.toInt()}m"
        else          -> "${"%.1f".format(metres / 1000)}km"
    }

    fun proximityLabel(metres: Double): String = when {
        metres < 10   -> "Right next to you"
        metres < 50   -> "Very close"
        metres < 100  -> "Nearby"
        metres < 300  -> "Close"
        metres < 1000 -> "In the area"
        else          -> "Far away"
    }
}