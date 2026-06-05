package com.jyothi.crowdpulse.mesh

import java.util.UUID

class MessageCache(private val maxSize: Int = 500) {

    private val seen: LinkedHashMap<UUID, Long> = object :
        LinkedHashMap<UUID, Long>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<UUID, Long>) = size > maxSize
    }

    @Synchronized
    fun seenBefore(id: UUID): Boolean = seen.containsKey(id)

    @Synchronized
    fun markSeen(id: UUID) { seen[id] = System.currentTimeMillis() }

    @Synchronized
    fun size() = seen.size
}