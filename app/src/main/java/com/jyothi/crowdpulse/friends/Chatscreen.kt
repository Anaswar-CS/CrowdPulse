package com.jyothi.crowdpulse.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    friend: Friend,
    messages: List<FriendMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Text(friend.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(friend.name,
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (friend.isNearby) "📍 Nearby — offline mesh" else "Via mesh network",
                    color = Color(0xFF44FF88), fontSize = 11.sp
                )
            }
        }

        // Messages
        LazyColumn(
            state          = listState,
            modifier       = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔒", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Messages travel peer-to-peer",
                                color = Color(0xFF555555), fontSize = 13.sp)
                            Text("No internet needed",
                                color = Color(0xFF444444), fontSize = 12.sp)
                        }
                    }
                }
            }
            items(messages) { msg ->
                MessageBubble(msg)
                Spacer(Modifier.height(4.dp))
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = inputText,
                onValueChange = { inputText = it },
                placeholder   = { Text("Message...", color = Color(0xFF555555)) },
                modifier      = Modifier.weight(1f),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    focusedBorderColor   = Color(0xFF333333),
                    unfocusedBorderColor = Color(0xFF222222),
                    cursorColor          = Color.White
                ),
                shape    = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSend(inputText.trim())
                        inputText = ""
                        scope.launch {
                            if (messages.isNotEmpty())
                                listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                },
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) Color(0xFF0066FF)
                        else Color(0xFF222222)
                    )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: FriendMessage) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (msg.isFromMe) Alignment.End else Alignment.Start
        ) {
            if (!msg.isFromMe) {
                Text(msg.senderName,
                    fontSize = 11.sp,
                    color    = Color(0xFF888888),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart    = if (msg.isFromMe) 18.dp else 4.dp,
                            topEnd      = if (msg.isFromMe) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd   = 18.dp
                        )
                    )
                    .background(
                        if (msg.isFromMe) Color(0xFF0066FF) else Color(0xFF1E1E1E)
                    )
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(msg.text,
                    color      = Color.White,
                    fontSize   = 15.sp,
                    lineHeight = 20.sp)
            }
            Text(
                formatTime(msg.timestamp),
                fontSize = 10.sp,
                color    = Color(0xFF555555),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ms))
}