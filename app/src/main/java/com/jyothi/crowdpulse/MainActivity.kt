package com.jyothi.crowdpulse

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jyothi.crowdpulse.friends.ChatScreen
import com.jyothi.crowdpulse.friends.Friend
import com.jyothi.crowdpulse.friends.FriendManager
import com.jyothi.crowdpulse.friends.FriendMessage
import com.jyothi.crowdpulse.friends.FriendsScreen
import com.jyothi.crowdpulse.service.CrowdPulseService
import com.jyothi.crowdpulse.ui.theme.CrowdPulseTheme

class MainActivity : ComponentActivity() {

    private val peerCountState   = mutableStateOf(0)
    private val latState         = mutableStateOf(0.0)
    private val lonState         = mutableStateOf(0.0)
    private val alertState       = mutableStateOf("")
    private val zoneState        = mutableStateOf("🟢 Zone: Safe")
    private val friendsState     = mutableStateOf<List<Friend>>(emptyList())
    private val nearbyUsersState = mutableStateOf<List<Friend>>(emptyList())
    private val messagesState    = mutableStateOf<List<FriendMessage>>(emptyList())
    private val screenState      = mutableStateOf("main")
    private val activeChatFriend = mutableStateOf<Friend?>(null)

    private lateinit var friendManager: FriendManager

    private val myDeviceId: Int by lazy {
        android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ).hashCode() and 0xFFFF
    }

    private val pollingHandler  = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (CrowdPulseService.isRunning) {
                peerCountState.value = CrowdPulseService.currentPeerCount
                latState.value       = CrowdPulseService.currentLat
                lonState.value       = CrowdPulseService.currentLon
                zoneState.value      = CrowdPulseService.currentZoneStatus
            }
            pollingHandler.postDelayed(this, 1000)
        }
    }

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.jyothi.crowdpulse.ALERT" -> {
                    alertState.value = intent.getStringExtra("message") ?: return
                }
                "com.jyothi.crowdpulse.CHAT" -> {
                    val senderId   = intent.getIntExtra("senderId", 0)
                    val senderName = intent.getStringExtra("senderName") ?: "Unknown"
                    val text       = intent.getStringExtra("text")      ?: return
                    val messageId  = intent.getStringExtra("messageId") ?: return
                    friendManager.receiveMessage(senderId, senderName, text, messageId)
                    friendsState.value  = friendManager.getFriends()
                    messagesState.value = friendManager.getAllMessages()
                }
                "com.jyothi.crowdpulse.PEER" -> {
                    val deviceId = intent.getIntExtra("deviceId", 0)
                    val name     = intent.getStringExtra("name") ?: "Unknown"
                    val lat      = intent.getDoubleExtra("lat", 0.0)
                    val lon      = intent.getDoubleExtra("lon", 0.0)
                    Log.d("MainActivity", "PEER received: id=$deviceId name=$name lat=$lat lon=$lon")
                    friendManager.onPeerDetected(deviceId, name, lat, lon)
                    friendsState.value     = friendManager.getFriends()
                    nearbyUsersState.value = friendManager.getNearbyNonFriends()
                    Log.d("MainActivity", "After update — friends=${friendsState.value.size} nearby=${nearbyUsersState.value.size}")
                }
            }
        }
    }

    private val REQUIRED_PERMISSIONS = buildList {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        friendManager = FriendManager(this)
        friendManager.onFriendDetected    = { friendsState.value = friendManager.getFriends() }
        friendManager.onMessageReceived   = { messagesState.value = friendManager.getAllMessages() }
        friendManager.onNearbyUsersUpdate = { nearbyUsersState.value = it }

        screenState.value = if (friendManager.myName == "User") "setup" else "main"

        setContent {
            CrowdPulseTheme {
                when (screenState.value) {
                    "setup" -> SetupScreen(
                        onNameSet = { name ->
                            friendManager.myName = name.trim()
                            screenState.value = "main"
                            if (allPermissionsGranted()) startService()
                            else requestPermissions()
                        }
                    )
                    "friends" -> FriendsScreen(
                        friends          = friendsState.value,
                        nearbyNonFriends = nearbyUsersState.value,
                        myLat            = latState.value,
                        myLon            = lonState.value,
                        onFriendClick    = { friend ->
                            activeChatFriend.value = friend
                            messagesState.value    = friendManager.getMessages(friend.deviceId)
                            screenState.value      = "chat"
                        },
                        onAddFriend = { user: Friend ->
                            friendManager.addFriend(user.deviceId, user.name)
                            friendsState.value     = friendManager.getFriends()
                            nearbyUsersState.value = friendManager.getNearbyNonFriends()
                        },
                        onBack = { screenState.value = "main" }
                    )
                    "chat" -> {
                        val friend = activeChatFriend.value
                        if (friend != null) {
                            ChatScreen(
                                friend   = friend,
                                // FIX: also match by senderName to handle MAC-based vs real deviceId mismatch
                                messages = messagesState.value.filter {
                                    it.senderId == friend.deviceId ||
                                            it.senderName == friend.name   ||
                                            it.isFromMe
                                },
                                onSend = { text -> sendChatMessage(friend, text) },
                                onBack = { screenState.value = "friends" }
                            )
                        }
                    }
                    else -> MainScreen(
                        peerCount      = peerCountState.value,
                        lat            = latState.value,
                        lon            = lonState.value,
                        alert          = alertState.value,
                        zoneStatus     = zoneState.value,
                        friendCount    = friendsState.value.size,
                        myName         = friendManager.myName,
                        onSosClick     = { sendSosToService() },
                        onGrantClick   = { requestPermissions() },
                        onFriendsClick = { screenState.value = "friends" },
                        onNameClick    = { screenState.value = "setup" }
                    )
                }
            }
        }

        if (screenState.value != "setup") {
            if (allPermissionsGranted()) startService() else requestPermissions()
        }

        val filter = IntentFilter().apply {
            addAction("com.jyothi.crowdpulse.ALERT")
            addAction("com.jyothi.crowdpulse.CHAT")
            addAction("com.jyothi.crowdpulse.PEER")
        }
        // On Android 13+ use RECEIVER_NOT_EXPORTED; older versions don't need the flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alertReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alertReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        pollingHandler.removeCallbacks(pollingRunnable)
        pollingHandler.post(pollingRunnable)
    }

    override fun onPause() {
        super.onPause()
        pollingHandler.removeCallbacks(pollingRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingHandler.removeCallbacks(pollingRunnable)
        unregisterReceiver(alertReceiver)
    }

    private fun sendChatMessage(friend: Friend, text: String) {
        val msg = friendManager.sendMessage(friend.deviceId, text, myDeviceId)
        messagesState.value = friendManager.getMessages(friend.deviceId)
        startService(Intent(this, CrowdPulseService::class.java).apply {
            action = CrowdPulseService.ACTION_CHAT
            putExtra("toDeviceId", friend.deviceId)
            putExtra("text",       text)
            putExtra("senderName", friendManager.myName)
            putExtra("messageId",  msg.messageId)
        })
    }

    private fun startService() {
        startForegroundService(Intent(this, CrowdPulseService::class.java))
    }

    private fun sendSosToService() {
        startService(Intent(this, CrowdPulseService::class.java).apply {
            action = CrowdPulseService.ACTION_SOS
        })
        alertState.value = "🆘 SOS sent — spreading through crowd..."
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && allPermissionsGranted()) startService()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun SetupScreen(onNameSet: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val isValid = name.trim().length >= 2
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📡", fontSize = 64.sp)
            Spacer(Modifier.height(24.dp))
            Text("Welcome to CrowdPulse",
                fontSize   = 26.sp, fontWeight = FontWeight.Bold,
                color      = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Offline crowd safety network\nfor festivals like Thrissur Pooram",
                fontSize = 14.sp, color = Color(0xFF888888),
                textAlign = TextAlign.Center, lineHeight = 20.sp)
            Spacer(Modifier.height(48.dp))
            Text("What's your name?",
                fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Friends will see this name when you send messages or SOS",
                fontSize = 12.sp, color = Color(0xFF666666), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value         = name,
                onValueChange = { if (it.length <= 20) name = it },
                placeholder   = { Text("Enter your name", color = Color(0xFF555555)) },
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    focusedBorderColor   = Color(0xFF0066FF),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor          = Color.White
                ),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick  = { if (isValid) onNameSet(name) },
                enabled  = isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Color(0xFF0066FF),
                    disabledContainerColor = Color(0xFF222222)
                )
            ) {
                Text("Get Started", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("Your name is stored only on your phone.\nNever sent to any server.",
                fontSize = 11.sp, color = Color(0xFF444444),
                textAlign = TextAlign.Center, lineHeight = 16.sp)
        }
    }
}

@Composable
fun MainScreen(
    peerCount:      Int,
    lat:            Double,
    lon:            Double,
    alert:          String,
    zoneStatus:     String,
    friendCount:    Int,
    myName:         String,
    onSosClick:     () -> Unit,
    onGrantClick:   () -> Unit,
    onFriendsClick: () -> Unit,
    onNameClick:    () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Text("📡", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("CrowdPulse", fontSize = 32.sp,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text("Offline crowd safety network",
                fontSize = 13.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNameClick() },
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF111111))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("👤", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("You are", fontSize = 11.sp, color = Color(0xFF666666))
                        Text(myName, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Text("Edit ›", fontSize = 12.sp, color = Color(0xFF555555))
                }
            }

            Spacer(Modifier.height(12.dp))

            val cardColor = when {
                zoneStatus.contains("DANGER")  -> Color(0xFF2A0000)
                zoneStatus.contains("Warning") -> Color(0xFF2A1500)
                zoneStatus.contains("crowded") -> Color(0xFF1A1A00)
                else                           -> Color(0xFF0A1A0A)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(zoneStatus, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("👥  Peers detected: $peerCount",
                        fontSize = 15.sp, color = Color(0xFFAAAAAA))
                    Spacer(Modifier.height(4.dp))
                    if (lat != 0.0) {
                        Text("📍  %.5f,  %.5f".format(lat, lon),
                            fontSize = 12.sp, color = Color(0xFF666666))
                    } else {
                        Text("📍  Acquiring GPS...",
                            fontSize = 12.sp, color = Color(0xFF666666))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth().clickable { onFriendsClick() },
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF0A1A2A))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💬", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Friends & Messages", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (friendCount > 0) "$friendCount friend(s) • Offline messaging"
                            else "Find nearby CrowdPulse users",
                            color = Color(0xFF6688AA), fontSize = 12.sp
                        )
                    }
                    Text("›", color = Color(0xFF6688AA), fontSize = 20.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (alert.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFF2A0000))
                ) {
                    Text(alert, modifier = Modifier.padding(16.dp),
                        color = Color(0xFFFF5555),
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (CrowdPulseService.isRunning) "🟢" else "🔴", fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (CrowdPulseService.isRunning)
                            "Background service active\nApp works with screen off"
                        else "Service not running",
                        fontSize = 12.sp, color = Color(0xFF6688AA), lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick  = onSosClick,
                modifier = Modifier.fillMaxWidth().height(68.dp),
                shape    = RoundedCornerShape(20.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000))
            ) {
                Text("🆘  SEND SOS", fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = onGrantClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Text("🔐  Grant / Re-check Permissions",
                    color = Color(0xFF8888FF), fontSize = 14.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}