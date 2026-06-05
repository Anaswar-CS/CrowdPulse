package com.jyothi.crowdpulse.friends

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class FriendManager(context: Context) {

    private val tag = "FriendManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("crowdpulse_friends", Context.MODE_PRIVATE)

    private val nearbyUsers = mutableMapOf<Int, Friend>()
    private val friends     = mutableMapOf<Int, Friend>()
    private val messages    = mutableListOf<FriendMessage>()

    var onFriendDetected:    ((Friend) -> Unit)?        = null
    var onMessageReceived:   ((FriendMessage) -> Unit)? = null
    var onNearbyUsersUpdate: ((List<Friend>) -> Unit)?  = null

    var myName: String
        get() = prefs.getString("my_name", "User") ?: "User"
        set(value) { prefs.edit().putString("my_name", value).apply() }

    init {
        loadFriends()
        loadMessages()
    }

    // ── Nearby detection ──────────────────────────────────────────────────────

    fun onPeerDetected(deviceId: Int, name: String, lat: Double, lon: Double) {
        Log.d(tag, "onPeerDetected: id=$deviceId name=$name friends=${friends.keys}")

        val isKnownFriend = friends.containsKey(deviceId)
        Log.d(tag, "isKnownFriend=$isKnownFriend")

        val user = Friend(
            deviceId   = deviceId,
            name       = if (isKnownFriend) friends[deviceId]!!.name else name,
            lastSeenMs = System.currentTimeMillis(),
            lastLat    = lat,
            lastLon    = lon,
            isNearby   = true
        )

        nearbyUsers[deviceId] = user

        if (isKnownFriend) {
            friends[deviceId] = user
            Log.d(tag, "Invoking onFriendDetected for ${user.name}")
            onFriendDetected?.invoke(user)
        }

        // Always fire nearby update regardless of friend status
        val nearby = getNearbyNonFriends()
        Log.d(tag, "Invoking onNearbyUsersUpdate — nearby non-friends: ${nearby.size}")
        onNearbyUsersUpdate?.invoke(nearby)
    }

    // ── Friend management ─────────────────────────────────────────────────────

    fun addFriend(deviceId: Int, name: String) {
        val friend = nearbyUsers[deviceId] ?: Friend(deviceId, name)
        friends[deviceId] = friend.copy(name = name)
        saveFriends()
        // Refresh nearby list so the newly added friend disappears from it
        onNearbyUsersUpdate?.invoke(getNearbyNonFriends())
        Log.d(tag, "Added friend: $name ($deviceId)")
    }

    fun removeFriend(deviceId: Int) {
        friends.remove(deviceId)
        saveFriends()
        onNearbyUsersUpdate?.invoke(getNearbyNonFriends())
    }

    fun getFriends(): List<Friend> = friends.values.toList()

    fun getNearbyFriends(): List<Friend> = friends.values
        .filter { it.isNearby }

    // Time filter removed — let all detected users show immediately
    fun getNearbyNonFriends(): List<Friend> = nearbyUsers.values
        .filter { !friends.containsKey(it.deviceId) }

    // ── Messaging ─────────────────────────────────────────────────────────────

    fun sendMessage(toDeviceId: Int, text: String, myDeviceId: Int): FriendMessage {
        val msg = FriendMessage(
            senderId   = myDeviceId,
            senderName = myName,
            text       = text,
            isFromMe   = true,
            status     = MessageStatus.SENDING
        )
        messages.add(msg)
        saveMessages()
        return msg
    }

    fun receiveMessage(senderId: Int, senderName: String, text: String, messageId: String) {
        if (messages.any { it.messageId == messageId }) return

        // FIX: Find friend by name in case deviceId doesn't match.
        // This happens when the MAC-based BLE ID was stored but the real ANDROID_ID
        // arrives later inside the GATT message payload.
        val friendByName = friends.values.firstOrNull { it.name == senderName }
        if (friendByName != null && friendByName.deviceId != senderId) {
            // Migrate the friend record to the real deviceId
            val updated = friendByName.copy(deviceId = senderId)
            friends.remove(friendByName.deviceId)
            friends[senderId] = updated
            saveFriends()
            Log.d(tag, "Updated deviceId for $senderName: ${friendByName.deviceId} → $senderId")
        }

        val msg = FriendMessage(
            messageId  = messageId,
            senderId   = senderId,
            senderName = senderName,
            text       = text,
            isFromMe   = false,
            status     = MessageStatus.DELIVERED
        )
        messages.add(msg)
        saveMessages()

        if (!friends.containsKey(senderId)) {
            addFriend(senderId, senderName)
        }

        onMessageReceived?.invoke(msg)
        Log.d(tag, "Message from $senderName ($senderId): $text")
    }

    fun getMessages(withDeviceId: Int): List<FriendMessage> =
        messages.filter {
            it.senderId == withDeviceId ||
                    it.senderName == friends[withDeviceId]?.name ||
                    it.isFromMe
        }.sortedBy { it.timestamp }

    fun getAllMessages(): List<FriendMessage> =
        messages.sortedBy { it.timestamp }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveFriends() {
        val arr = JSONArray()
        friends.values.forEach { f ->
            arr.put(JSONObject().apply {
                put("deviceId", f.deviceId)
                put("name",     f.name)
            })
        }
        prefs.edit().putString("friends", arr.toString()).apply()
    }

    private fun loadFriends() {
        val str = prefs.getString("friends", "[]") ?: "[]"
        try {
            val arr = JSONArray(str)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val f = Friend(
                    deviceId = obj.getInt("deviceId"),
                    name     = obj.getString("name")
                )
                friends[f.deviceId] = f
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load friends: ${e.message}")
        }
    }

    private fun saveMessages() {
        val recent = messages.takeLast(200)
        val arr = JSONArray()
        recent.forEach { m ->
            arr.put(JSONObject().apply {
                put("messageId",  m.messageId)
                put("senderId",   m.senderId)
                put("senderName", m.senderName)
                put("text",       m.text)
                put("timestamp",  m.timestamp)
                put("isFromMe",   m.isFromMe)
            })
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    private fun loadMessages() {
        val str = prefs.getString("messages", "[]") ?: "[]"
        try {
            val arr = JSONArray(str)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                messages.add(FriendMessage(
                    messageId  = obj.getString("messageId"),
                    senderId   = obj.getInt("senderId"),
                    senderName = obj.getString("senderName"),
                    text       = obj.getString("text"),
                    timestamp  = obj.getLong("timestamp"),
                    isFromMe   = obj.getBoolean("isFromMe"),
                    status     = MessageStatus.DELIVERED
                ))
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load messages: ${e.message}")
        }
    }
}